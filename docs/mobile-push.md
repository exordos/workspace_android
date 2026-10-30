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

The wire message is data-only: `data.payload` contains an Event UUID, installation,
project, stream, topic, target message, user and device UUIDs, and an encrypted
HPKE envelope. The producer uses `notification_topic_update`; the receiver still
accepts legacy create/cancel kinds for in-flight deliveries.

HPKE follows RFC 9180 base mode, X25519/HKDF-SHA256/AES-256-GCM, raw 32-byte keys,
unpadded base64url and `info=workspace-mobile-push`. AAD is compact JSON:

```text
[event_uuid, kind, organization_uuid, project_uuid, stream_uuid, topic_uuid,
 message_uuid, user_uuid, device_uuid, key_uuid]
```

Protected content includes `kind=topic`, stream/topic/sender names, target message
text and timestamp, absolute unread/active/passive counts, and the existing
Workspace `epoch_version`/`epoch_generation`. Android validates count consistency,
recipient/key/route authentication and ordering before showing anything. Bad input
is dropped without logging it. No text, key or token is stored in the receipt DB.
No server request or image download is required to render a received snapshot.

`WorkspacePushNotifications` shows one `workspace_messages` channel card per saved
connection/account/project/stream/topic. The title is the stream/topic, its count
is the absolute unread count and its preview/click target use the first unread
message. A newer snapshot replaces the same card, including when that first
unread message changes. Active-unread growth can alert; decrease, repeat and
restoration are silent. Zero active unread removes it. Android permission and
channel settings apply; the lock-screen public version is generic.

The additive SQLite upgrade preserves existing legacy receipts. Topic receipts
persist their existing WebSocket cursor and retired generation decisions, so an
older push cannot undo a zero snapshot or restore an old account's card. A
WebSocket all-read topic update closes the card immediately, including when it
arrives before the first FCM delivery. A stale swipe intent cannot dismiss a newer
snapshot, including after a WebSocket generation reset. A deliberate swipe stays dismissed until active unread grows again.
Tapping alone does not close a card. A later new snapshot updates its click target.
Closed topic receipts expire one delivery window after the last zero/closure,
including WebSocket-only tombstones. This bounds closed-topic storage while
retaining the barrier for every still-deliverable push. Unread and deliberately
dismissed cards are retained.

The existing background reconciliation worker also batches topic IDs through the
ordinary authenticated topic API. All-read, deleted or inaccessible topics close
only the receipt captured before HTTP; failed requests retain it and a concurrent
newer FCM snapshot survives. Legacy receipts keep their message-API repair. This
runs separately from offline receipt/rendering and requires no resolver endpoint.

The default limit remains five conversations. Legacy per-message cards retain
the existing per-conversation limit and read/delete reconciliation during
transition. Topic snapshots use a single card and no separate summary. Overflow
preserves pending routes and returns silently when room is available. A message
actually visible in the resumed chat is hidden without changing its unread state.
The application being open elsewhere does not make messages read.

Decrypted previews live only in a bounded process cache. After process death,
restoration uses generic text until a new encrypted snapshot arrives. Unread
receipts have no age deadline; this does not promise background delivery through
force-stop, reinstall or OS restrictions. Android 16 timeout compatibility uses
the existing maximum-duration sentinel and current posting time. The transport
still has its one-hour delivery window. No new revision or status endpoint is
introduced.

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

Deploy Notification support and this Android consumer before enabling the new
Workspace topic-update producer. Keep legacy kinds during the transition.

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


`LiveTopicPushNoRealtimeTest` is an opt-in real-delivery check. Given an unread
external topic Event in a selected saved connection, it closes only that
connection's realtime scope and signals readiness through a temporary local
marker. An external test driver marks the topic read through its authenticated
API. The phone must receive a new authenticated FCM snapshot and persist zero
before the test restores the repository. It never injects a payload or changes
keys/tokens; the marker is removed in `finally`.

A topic update that closes an empty topic after its last message was deleted has
`message_uuid: null`. Its authenticated route still includes the topic; Android
removes that topic's card without creating a click target. A positive active
unread count requires a non-null message UUID. Existing message create/cancel
payloads still require a canonical message UUID.
