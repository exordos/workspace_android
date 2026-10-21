# Native encrypted Workspace notifications

Android registers its Firebase token and a separate X25519 push public key with
Workspace. Workspace prepares the preview, encrypts one content envelope per
device, and sends it through local Notification → upstream Notification → FCM.
Only the upstream Notification has Firebase sending credentials. The complete
server/event/deployment contract lives in
`exordos_notification/docs/mobile-push.md`.

## Registration and account lifetime

`PushRegistrationWorker` obtains the latest token from Firebase and uses the
existing API client with an explicit saved server. It checks server settings,
verifies `/me`, then PUTs `/api/workspace/v1/push_devices/{device_uuid}` with
`transport=fcm`, `platform=android`, `registration_token`, and
`encryption={kind:HPKE, algorithm, key_uuid, public_key}`. The response must
identify that same device, owner and project. The token is never part of a URL.

`PushKeyStore` stores one stable device UUID/key pair per connection and owner.
Its private key is encrypted with an Android Keystore-backed key in
`workspace_push_secrets_v1`, excluded from cloud backup and device transfer.
Chat keys use their existing separate store. Updating an FCM token replaces the
registration with the same UUID and public key. A different owner/project gets
new push keys and removes the previous owner's displayed notifications.

`WorkspaceApplication` prepares one background API client on the shared HTTP
transport and server repository, independently of `MainActivity`. Its
`PushWorkerFactory` passes that client and repository into both worker
constructors. The same client handles explicit device removal. WorkManager's
default startup initializer is removed so `Configuration.Provider` supplies the
factory. Workers do not create or attach UI models or own the shared transport.

WorkManager schedules reconciliation on launch, login, project changes and
`onNewToken`, and retries failed network work. It reads the current token at
execution time. A fresh request replaces older pending registration work, so a
new login or token is not blocked behind an older request's retry backoff.
No registration revision counter is used. Registrations are
independent across multiple saved servers/accounts.

The explicit remove/logout path serializes with registration, disables local
receipt, attempts DELETE while credentials still exist, then deletes the local
key and connection notifications. An unavailable server prevents confirmation
of remote removal; local key deletion immediately prevents subsequent display.
Removing one connection does not delete the app-wide Firebase token needed by
other accounts. Registration has a 15-second bound per connection and DELETE a
15-second bound so a stalled server cannot hold logout indefinitely. Backend invalid-token feedback only removes the matching old
token snapshot, preserving a registration already refreshed by this worker.

Logout serializes with an already running registration; it can first wait for
that operation's 15-second bound before its own 15-second DELETE attempt.
The stalled-DELETE regression isolates that HTTP attempt from unrelated startup
registration jobs instead of treating their mutex wait as DELETE latency.

Older servers without an installation UUID or enabled native push are skipped.
The old bouncer receipt parser remains available; obsolete token-in-URL
registration requests are removed.

## Receipt and privacy

The new wire message is data-only: `data.payload` is a compact JSON string with
an event UUID, action, installation/project/stream/topic/message/user/device
UUIDs, and `content={kind:encrypted, algorithm, key_uuid, enc, ciphertext}`.
The supported actions are exactly `workspace_message_create` and
`workspace_message_cancelled`.

The profile is `HPKE-v1-BASE-X25519-HKDF-SHA256-AES-256-GCM` using RFC 9180 base
mode, raw 32-byte X25519 keys and unpadded base64url. The `info` is
`workspace-mobile-push`. Associated data is compact JSON in this exact order:

```text
[event_uuid, kind, organization_uuid, project_uuid, stream_uuid, topic_uuid,
 message_uuid, user_uuid, device_uuid, key_uuid]
```

The existing chat HPKE suite retains its previous behavior; push adds the
AES-256-GCM suite separately. A wrong key, unsupported profile or altered action
or route cannot authenticate. Such input is dropped without logging ciphertext,
tokens, keys or preview text.

After decryption, a create contains only the preview fields needed locally:

```json
{"kind":"message","sender_name":"Alice","text":"Hello"}
```

`WorkspacePushNotifications` renders through the `workspace_messages` channel.
No server request or image download is needed. Android notification permission
and channel settings still apply. Lock-screen public content is generic.
Existing encrypted chat messages use a generic encrypted-message preview; the
backend does not acquire their chat keys.

A cancellation authenticates its own encrypted `{}` content. The durable identity
includes the saved connection, organization, project, owner and public message
UUID. Different event kinds for that message share one alert; two saved
connections to the same account own separate registrations, keys, routes and
Android notification tags. Logout or removal clears only that connection.
Explicit login/project replacement retires the old registration before replacing
its credentials; automatic token refresh keeps the existing device identity.

`workspace_push_inbox.db` stores only routes, ordering timestamps and unread,
dismissed or closed decisions. It stores no decrypted preview, FCM token or key,
and is excluded from backup/transfer. There is no unread age deadline. Terminal
read/delete/cancellation decisions and manual dismissals prevent late, repeated
or reordered creates from reviving an alert. Tapping alone does not auto-cancel
it. Old SharedPreferences routes are imported once; already expired routes from
an earlier installation cannot be reconstructed by that migration.

Loading old read history only closes receipts that already exist; it does not
create a row for every historical message. A recent read snapshot, or an explicit
read/delete arriving before its push, keeps a route-less decision for the
existing one-hour delivery window. Such transient decisions are pruned during
receipt/restoration; pre-release rows without a timestamp get one full window
on upgrade. This cleanup never removes an unread route or the dismissal/closed
decision belonging to a received push. Recent read-before-push races therefore
remain covered without accumulating the user's entire read history.

The default display limits are **5 conversations and 5 messages per conversation**.
Appearance settings allow 1–5 conversations and 1–10 messages per conversation; a total budget of 40 Android cards,
including summaries, may reduce the displayed number further. Conversations are
separate for each saved connection/account/project/stream/topic. The oldest
eligible items are hidden first, preserving unread state and their click routes;
they return silently when room becomes available. Counts may exceed the number
of displayed messages. A group dismissal covers its current sequence snapshot,
including hidden messages represented by its unread count; later arrivals are
not included. A single swipe dismisses only that message on this device.

Repeated deliveries, preview changes, restoration and overflow returns are
silent. Decrypted previews live only in a bounded process-local display cache;
restoration may use generic text. Actually visible messages in the resumed chat
are temporarily hidden without changing unread state. Read confirmation removes
their records; a failed read leaves them eligible when the user leaves that chat.
The app being open in another screen does not make a message read.

Android 16 supplies its own three-day timeout when the requested timeout is zero,
and rejects newly posted cards whose supplied `when` is over 14 days old. The
system-card timeout therefore uses a maximum-duration compatibility sentinel
with arithmetic headroom, rather than an application age-expiry policy. Posting
uses current time with `showWhen=false`; original message time remains in the
sort key and receipt, so chronology is preserved. Android and user settings
still control OS presentation. This does not promise preservation through
reinstall, reboot, force-stop or system restrictions on background work.

A local periodic worker restores eligible cards silently even offline. Startup,
network return, realtime reconnect/read/delete and periodic work also reconcile
known routes in batches of 50 through the existing authenticated message API.
Confirmed reads and successful snapshots omitting deleted/inaccessible messages
close their routes; failed HTTP/authentication requests retain them. Periodic
work uses the platform's inexact minimum 15-minute interval. There is no new
resolver or status endpoint. Workspace retains route-only cancellation data for
unread messages; terminal create tokens/ciphertexts are removed. The original
one-hour server/FCM delivery deadline remains unchanged.

## Opening a notification

Each immutable PendingIntent identifies a locally saved receipt, not an
arbitrary URL supplied by a remote sender. The receipt resolves the saved
connection and complete chat route. The app selects/restores that connection,
waits for its verified account, and checks that the owner/project match. It
opens the authorized stream/topic and uses the public message placement UUID as
the initial message anchor. Stream, topic and message loading must succeed in
the captured authenticated context before navigation. A failed sign-in, denied
request, failed loading or failed navigation does not remove the receipt. Only
successful read confirmation or an authoritative read/delete update closes it.

Cold launch and `onNewIntent` both handle the receipt. A handled intent is
cleared so rotation cannot replay an old navigation. A removed/disabled
connection cannot resolve its old notification. Transport choice remains in the
Notification API; the later Workspace transport-preference UI/proxy is separate
from the Android presentation-limit controls implemented here.

The saved target is selected before the project/login screen gate and its
existing settings/owner bootstrap starts explicitly. A cold repository created
after the initial all-connections startup must not remain waiting for an owner
proof indefinitely. Chat navigation still requires the exact verified owner and
project; selection/bootstrap alone cannot grant access.

## Build and verification

Supply the real matching Firebase **Android client** configuration as the ignored
`app/google-services.json` from private storage. Do not commit it. It does not
provide the server credentials needed to send FCM messages. No Google Play
publication is required for these checks.

```bash
./gradlew --no-daemon --max-workers=2 :app:testExordosDebugUnitTest \
  --tests 'ru.genesiscorporation.workspace.beta.data.crypto.*' \
  --tests 'ru.genesiscorporation.workspace.beta.data.push.*'
./gradlew --no-daemon --max-workers=2 \
  :app:assembleExordosDebug :app:assembleExordosDebugAndroidTest
```

`EncryptedPushReceiptTest` runs on Android with public synthetic HPKE keys. It
checks offline decryption/display, routes older than two weeks, duplicate and
tampered-cancellation handling, independent duplicate connections, single/group
dismissal snapshots, overflow/silent return, visible-message suppression and
late read/delete cancellation. `PushAccountAndRecoveryTest` exercises the real
HTTP client, pre-replacement device retirement, automatic refresh and recovery
of missed read/delete updates through the existing message API. It removes only its synthetic registration/notifications and preserves
real connection data. It does not establish live Firebase delivery.

`PushRegistrationSchedulingTest` uses real WorkManager storage to keep an older
request pending and verifies that a fresh login/token request supersedes it
without becoming blocked behind its retry backoff.

The existing opt-in `-PfirebaseLiveSmoke=true` includes
`FirebaseRegistrationSmokeTest`, which checks that the configured app obtains a
real token without printing it. This still does not establish sending access.

For full acceptance, deploy the native Workspace backend and both Notification
roles, log in with the agreed real account, and verify a real received FCM push
from a newly written message. Exercise foreground/background, cold/warm clicks,
multiple accounts/servers, token refresh, logout and read cancellation. Disable
Workspace connectivity during receipt to prove offline rendering. Restore it
for normal chat loading. Repeat critical receipt/click checks on the optimized
release artifact; unit tests and a debug build do not replace those checks.
Never uninstall or clear an existing app's data to work around a signing mismatch.

Deploy the Notification changes in `exordos_notification#106` first, then the
Workspace backend changes in `workspace_backend#196`, and finally this client.

## Live connection preflight

The opt-in `WorkspaceConnectionSmokeTest` reads only public server settings. It
sends no credentials and does not change saved accounts. It reports transport
exception classes without logging requests or tokens:

```sh
./gradlew --no-daemon --max-workers=2 -PfirebaseLiveSmoke=true \
  :app:assembleExordosDebugAndroidTest
adb shell am instrument -w \
  -e class ru.genesiscorporation.workspace.beta.data.push.WorkspaceConnectionSmokeTest \
  -e workspaceBaseUrl https://<WORKSPACE_HOST> \
  com.exordos.workspace.test/androidx.test.runner.AndroidJUnitRunner
```

The server's certificate chain must be trusted by the target build. Debug builds
allow explicitly installed user CAs; release builds retain the system trust
policy. Use an appropriately trusted certificate for release click/navigation
acceptance. See [Android's trust configuration](https://developer.android.com/privacy-and-security/security-config).
A TLS preflight failure must be resolved before device registration or live
acceptance; it is not evidence that Firebase delivery or chat routing works.

## Observing an external FCM delivery

The opt-in `LivePushDeliveryTest` never injects a payload or calls the receipt
handler. Send an encrypted event through the deployed Notification pipeline to
the registered real device first, then pass its event UUID and expected preview:

```sh
adb shell am instrument -w \
  -e class ru.genesiscorporation.workspace.beta.data.push.LivePushDeliveryTest \
  -e eventUuid '<EVENT_UUID>' -e action open \
  -e expectedSender '<EXPECTED_SENDER>' -e expectedPreview '<EXPECTED_PREVIEW>' \
  -e expectedMessageText '<ACTUAL_CHAT_MESSAGE_TEXT>' \
  -e workspaceBaseUrl 'https://<WORKSPACE_HOST>' \
  com.exordos.workspace.test/androidx.test.runner.AndroidJUnitRunner
```

It checks the authenticated local receipt, posted notification preview and
immutable intent, invokes the actual notification PendingIntent, and waits for
the real chat text and matching saved server/project. Also verify an actual
touch on the notification in the system shade. The
device must be unlocked and its own notification/DND settings must allow the
alert to be visible. These tests do not change those settings. Add
`-e screenshot true` to save the verified
chat view as `live-push-chat.png` in the target app cache; pull and remove only
that test artifact after inspection. Use only
synthetic acceptance content in these command arguments. With `action=observe`
it checks the displayed receipt without opening it. After sending a cancellation
for a displayed message, run with `action=cancel` and the original create event
UUID; it requires both notification removal and an authenticated cancellation
tombstone. Keep any real accounts and their data intact.

A directly submitted diagnostic event establishes the transport/receipt path;
it does not establish native message fanout or read-triggered cancellation.
Full acceptance must repeat the checks with an event produced by another real
message author and its normal read operation, and on the optimized build.

## Live lifecycle checks

`LivePushOwnerSmokeTest#selectedAcceptanceSessionRefreshKeepsItsPushRegistration`
requires `allowSessionRefresh=true`, `workspaceBaseUrl`, `userUuid` and
`organizationUuid`, with optional `serverId` or a known receipt's `eventUuid`.
It uses the saved connection's real
IAM refresh and `/me`, verifies recovery from a sign-in-required state, and checks
that automatic renewal retains the device/key. Use an agreed acceptance account.

Select live tests explicitly; they need different arguments and must not be run
as an unattended bulk suite. `LivePushOwnerSmokeTest#savedAcceptanceOwnerCanAuthenticate`
accepts `workspaceBaseUrl` and verifies the saved connection through its real
authenticated `/me`. Its separate
`#savedReceiptRestoresItsAccountAndOpensActualChat` method accepts `eventUuid`
and `expectedMessageText`, opens an already authenticated local receipt from
cold launch and waits for the actual chat. This navigation preflight complements
the actual notification touch; it does not establish remote delivery by itself.
Pass `coldOtherAccount=true` to reproduce startup with another saved connection
selected and the target absent from the initial in-memory repository map. The
test requires two saved connections and an existing authenticated receipt,
recreates only process-local repositories, and keeps persisted accounts/keys.
This scenario timed out before target bootstrap was explicit and passes after
the change. Keep a separate actual system-shade touch check in full acceptance.

`#savedAcceptanceRegistrationReconcilesCurrentToken` schedules the normal
registration worker without removing a connection, waits for successful work,
and verifies that the saved key/device identity stays stable. It emits only
device/key UUIDs and the token's SHA-256 fingerprint for a private comparison
with the backend; the token itself is not printed.

For diagnosis, `LivePushWireCompatibilityTest` can read a privately staged
encrypted server snapshot from `live-push-wire.json` in target cache, with
`allowWireProbe=true` and synthetic `expectedMessageText`. Strip registration
tokens before staging it. The test verifies the current device/key, route and
authenticated plaintext, then removes the input. It never calls the receipt
handler or creates a notification, and cannot establish actual FCM delivery.

`LivePushRegistrationTest` requires `allowAccountRemoval=true`,
`workspaceBaseUrl`, `organizationUuid`, `projectUuid` and `userUuid`. It removes
and restores only that matched acceptance connection through the real repository
and WorkManager, requires the real HTTP DELETE acknowledgement, checks a new
device/key, and preserves the other connections.
It changes a real connection and should run only on an agreed acceptance account.
Verify backend row removal/re-registration independently and repeat actual delivery with
the replacement key.

`LivePushTokenRefreshTest` requires `allowTokenRefresh=true` and refuses physical
devices. Use only the dedicated emulator fixture below; `serverId` can explicitly
select its single `cassi-pr97-` connection. It calls the actual SDK
`deleteToken()`/`getToken()`, waits for work scheduled by the SDK callback and
checks that the device/key remain unchanged. It does not invoke the callback or
worker directly. Privately compare the resulting SHA-256 fingerprint in target
cache `live-push-token-fingerprint.txt` with the backend registration, then remove
that file. The token itself is never printed. Verify new delivery and the old
token's inactive response/filtering through both Notification instances.

## Optimized live acceptance

Use a new dedicated emulator with Google Play services and a server certificate
trusted by its system CA store. A lab CA may be installed in that emulator's
system store; keep the release APK's TLS policy unchanged. Preserve all existing
physical-device data and accounts.

Build the debug APK/live tests, release APK and existing framework-only driver:

```sh
./gradlew --no-daemon --max-workers=2 -PfirebaseLiveSmoke=true \
  :app:assembleExordosDebug :app:assembleExordosDebugAndroidTest \
  :app:assembleExordosRelease :app:buildEmojiReleaseSmoke
```

Sign the release APK and driver with the debug APK's local test certificate.
Install debug plus the live test APK on the empty emulator. Stage one authorized
session as mode `0600` target-cache `live-push-connection.json`, using a private
programmatic stdin pipe. Never pass tokens in command arguments or publish this
file. Its fields are `server_id` (`cassi-optimized-push-acceptance`), `base_url`,
`organization_uuid`, `project_uuid`, `user_uuid`, `access_token` and
`refresh_token`. Use a separate normal IAM login for this fixture so another
client does not rotate its refresh token.

Run `LivePushConnectionSeedTest` with `allowSeed=true`. It refuses non-debug
targets, physical devices and existing sessions, deletes the private input, and
uses the normal registration worker. The explicit `allowAppend=true` fixture
mode permits additional connections only when all saved IDs and the new ID
start with `cassi-pr97-`; it never replaces an existing ID. This permits testing
duplicate connections and another selected account on the dedicated emulator.
After registration succeeds, update the
app in place with the same-certificate optimized APK. Install the driver with
`adb -s TASK_SERIAL install -t`; do not clear app data. Start the app normally,
then put it in the background before creating a fresh acceptance message.

```sh
adb -s TASK_SERIAL shell am instrument --user 0 -w \
  -e eventUuid '<CREATE_EVENT_UUID>' -e messageUuid '<MESSAGE_UUID>' \
  -e action open -e expectedSender '<SYNTHETIC_SENDER>' \
  -e expectedPreview '<SYNTHETIC_PREVIEW>' \
  -e expectedMessageText '<UNIQUE_FRESH_CHAT_TEXT>' \
  com.exordos.workspace.cassi.releasetest/.WorkspacePushLiveReleaseInstrumentation
```

Require `result=PASS` and instrumentation code `-1`. This driver refuses
debuggable targets, observes externally received FCM without injection, verifies
the absence of a short alert deadline, and sends its real PendingIntent before checking
the actual loaded message. It references framework APIs and persisted receipt
fields, requiring no app reflection or production R8 keep rules. `action=observe`
checks receipt/display only; `action=cancel` requires removal and an authenticated
tombstone using the original create event UUID.

Physical devices support the ordinary `observe`, `open` and `cancel` checks;
fixture mutations remain restricted to a dedicated emulator. A physical release
test requires the Workspace HTTPS certificate to be trusted by the system.
Keep release TLS policy unchanged and preserve all existing accounts and data.

Pass `serverId` when the same account is saved twice. `action=paired` also takes
`secondServerId` and requires two independent unread receipts and displayed
cards for the same external event. `action=age` ages only that owned receipt's
ordering timestamp by 15 days; restart the app and repeat `open` with the generic
restored preview. This is a persisted-state regression, not a claim that a test
waited two weeks. `action=dismiss` sends the card's actual delete PendingIntent;
after restarting, `action=absent` requires its durable dismissed decision.
With all emulator networks disabled, `action=openOffline` attempts the actual
PendingIntent and requires the unread receipt/card to survive failed loading.
Restore network and verify normal opening afterwards. Never use these fixture
mutations against a physical device or real existing account data.

For offline acceptance, block only the dedicated emulator's Workspace destination
while retaining FCM access, terminate the background app process without
force-stopping the package, and send a fresh message. Verify `observe`, perform a
normal read from another authorized client, then verify `cancel`. Restore and
verify connectivity before navigation checks. End instrumentation can stop its
target process; start the app normally before the next background-delivery test.
Remove only task-owned network rules and emulator resources afterwards.
