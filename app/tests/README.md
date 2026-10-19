# Tests

See the [project test strategy](../../docs/testing.md) for architecture boundaries,
the regression map and release-quality gates, and the
[development guide](../../docs/development.md) for SDK/build prerequisites.

All app tests live in this directory:

- `unit/`: local JVM tests, mapped to Gradle's `test` source set.
- `instrumented/`: tests and fixtures that run on Android, mapped to the
  `androidTest` source set. Its manifest belongs to the test APK.
- `host/`: local server fixtures and build regressions run by Gradle, outside the Android source sets.
- `live/`: opt-in Android checks that require a real Firebase configuration and service access.

Keep Java and Kotlin package paths under each directory's `java/` folder.
The source set mappings are defined in `app/build.gradle.kts`; Gradle task names
are unchanged.
`app/src/test` and `app/src/androidTest` are not configured source roots.

From the repository root, run the JVM tests with:

```sh
./gradlew :app:testGenesisDebugUnitTest
```

With an Android device or emulator connected, run the instrumented tests with:

```sh
./gradlew :app:connectedGenesisDebugAndroidTest
```

## Notification indicators

Run the repository regressions on a connected Android device or emulator:

```sh
./gradlew :app:connectedGenesisDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.genesiscorporation.workspace.beta.data.NotificationIndicatorRepositoryTest
```

These cases verify that authoritative topic events update active unread in both
topic caches, including mute/read transitions. Folder events must refresh the
selected folder's items and counter without switching tabs, preserve unrelated
selections, and remove deleted folders with a valid remaining selection or none.
Repeated deletion events must be harmless and must not delete conversations.

Use the corresponding `ExordosDebug` tasks for the Exordos package. Select the
dedicated device with `ANDROID_SERIAL` before running a connected task.

`PublicationWindowTest` exercises the signed-out entry screen. Run the full
suite in a clean test installation or a separate Android user; do not clear
someone's saved accounts to satisfy that precondition. HTTP and repository
tests use isolated DataStores and unique token-store IDs.

## General request contracts

`WorkspaceRequestHttpContractTest` reuses `WorkspaceTestAccount` and
`LoopbackWorkspaceServer` with synthetic sessions. It verifies the production
Ktor pipeline for login/OTP and refresh credential boundaries, Unicode draft
bodies, quoted `If-Match`, revision conflicts, distinct read/read-up-to actions,
notification overrides, topic completion, configuration mismatches and plain
server errors. It never contacts a deployed Workspace.

```sh
ANDROID_SERIAL=TASK_SERIAL ./gradlew :app:connectedExordosDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.genesiscorporation.workspace.beta.data.remote.WorkspaceRequestHttpContractTest
```

JVM regressions `SessionRequestContractTest`, `DraftRequestContractTest`,
`RequestQuerySerializationTest` and `ResponseErrorTest` cover wire encoding
and error boundaries without Android. `WorkspaceShareTest` belongs under
`unit/java/`, so Gradle discovers the web-link regressions alongside the other
tests. `MessageTimeFormattingTest` fixes the clock and zones; its temporary
default timezone is always restored.

`StreamMembershipInstrumentedTest` exercises creation, invitation retries,
membership changes and user search through the production screens and HTTP
client with three synthetic authenticated loopback accounts. It does not
replace acceptance against an independently deployed Workspace backend.
Snapshot/event ordering is covered by JVM `StreamMembershipStateTest` cases;
Android tests retain the HTTP contract, delayed-response wiring and visible UI
checks. Membership and login tests share `WorkspaceTestAccount` and the bounded
`LoopbackWorkspaceServer` transport under the instrumented fixtures directory.

`AuthenticationErrorUiTest` also repeats identical login/OTP failures in one
mounted form. It deliberately coalesces the Loading frame and requires a real
system Toast for each response.

## FCM notification regressions

`NotificationMessageIdTest` covers decimal producer IDs, malformed values and
notification-key compatibility. `FirebaseNotificationsTest` posts synthetic
callbacks into Android's real notification registry: private/stream payloads,
missing or malformed IDs, Int/Long overflow, mixed removal lists, updates and
cancellation isolation. It sends both older and newer PendingIntents and checks
their delivered `MainActivity` extras. No real Firebase configuration is needed.
The app currently retains the deeplink without resolving a chat; these tests
establish activity delivery, not completed chat navigation or remote FCM delivery.

```sh
ANDROID_SERIAL=TASK_SERIAL ./gradlew :app:connectedExordosDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.genesiscorporation.workspace.beta.data.FirebaseNotificationsTest
```

The existing `:app:buildEmojiReleaseSmoke` task also builds the framework-only
`FirebaseNotificationReleaseInstrumentation` driver. Sign the driver and the
optimized Exordos APK with the same test certificate. Use a clean dedicated
emulator, grant `android.permission.POST_NOTIFICATIONS`, and run:

```sh
adb -s TASK_SERIAL shell am instrument -w \
  com.exordos.workspace.cassi.releasetest/.FirebaseNotificationReleaseInstrumentation
```

Require `result=PASS` and instrumentation code `-1`. This driver delivers
synthetic broadcasts through the APK's actual FCM receiver and service, checks
invalid/large IDs and independent update/cancel behavior, then sends both
notification intents to the real activity. It requires a non-debuggable target,
uses no account or live Firebase credentials, and adds no production keep rules.

## Firebase release configuration regression

```sh
./gradlew --no-daemon -p app/tests/host verifyFirebaseReleaseConfig
```

This copies the production build script into a temporary Android project and
executes its actual Google services tasks offline. It checks both flavors,
placeholder/missing/malformed configuration, app/project consistency, matching
client selection, variant-file precedence, the first API key and redacted errors.
All values are synthetic; no Firebase account or registration token is used.
The debug configuration path remains usable for isolated UI tests. Downloaded
production configuration must stay outside Git. Live FCM registration and
notification delivery are separate acceptance checks in the development guide.

The live SDK regression is kept out of offline suites. With a real local Firebase
configuration and Google Play services on a dedicated installation, run:

```sh
ANDROID_SERIAL=TASK_SERIAL ./gradlew :app:connectedExordosDebugAndroidTest \
  -PfirebaseLiveSmoke=true \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.genesiscorporation.workspace.beta.data.FirebaseRegistrationSmokeTest
```

It obtains an actual FCM token without printing it. `FirebaseTokenLoggingTest`
checks that the refresh callback, notification body/sender and private/stream
chat payloads do not enter logcat. It also checks that synthetic chat notifications
retain their expected title and body, then cancels only its own notifications.
On Android 13+, it grants notification permission to the dedicated test installation.

## Optimized emoji regression

Debug tests cannot detect classes removed by R8. Build the release APK with
`:app:assembleExordosRelease`, then build the standalone framework test driver:

```sh
./gradlew :app:buildEmojiReleaseSmoke
```

The Kotlin Gradle task uses the project's configured Android SDK, build tools
and JDK. Its unsigned driver is written to
`app/build/outputs/emoji-release-smoke/emoji-release-smoke-unsigned.apk`.

Sign the release APK and driver with the same test certificate, install them
on the selected test device (use `adb install -t` for the driver), and run:

```sh
adb shell am instrument -w \
  com.exordos.workspace.cassi.releasetest/.EmojiReleaseInstrumentation
```

Require `result=PASS` and instrumentation code `-1`. The driver refuses a
debuggable target and deserializes the optimized app's complete bundled emoji
database, including the model fields, aliases and enums. It uses no network or
account data and adds no keep rules for a test runner to the production app.
The expected count is 5,225 entries for the pinned JEmoji 1.7.6 database; revisit
that baseline when updating the dependency. A truncated database must fail even
when it still contains the sample emoji and at least 1,000 entries.

## Avatar credential boundary

`UrnParserTest` and `AvatarOriginTest` cover namespace parsing and HTTP origin
validation. `AvatarImageRequestTest` exercises the actual avatar Coil/Ktor/CIO
path using loopback image origins, including same-origin and cross-origin
redirects. `AvatarRedirectPolicyTest` keeps Ktor's redirect pipeline but captures
engine requests to test scheme changes without TLS or DNS. No saved credentials
or public image hosts are used.

For the optimized APK, use a new dedicated emulator and the standalone driver
built by `:app:buildEmojiReleaseSmoke`. The driver's
`AvatarFixtureInstrumentation`, in `instrumented/java/`, seeds one synthetic
session into the debug app.
It refuses physical devices, non-debug apps and pre-existing user sessions.
Sign the driver and release APK with the debug APK's local test certificate.
With only the debug APK and driver installed, run:

```sh
adb -s TASK_SERIAL shell am instrument -w -e allowCassiFixtureSeed true \
  com.exordos.workspace.cassi.releasetest/.AvatarFixtureInstrumentation
./gradlew --no-daemon -p app/tests/host runAvatarFixture -PavatarFixtureArtifacts=/absolute/artifacts
```

The host fixture is Kotlin under `host/` and runs with Gradle's JDK;
it needs no additional runtime. Its isolated Gradle build keeps host code out of
Android compilation and lint. Stop its Gradle command with Ctrl+C after testing.
In a second terminal, forward both fixture origins and install the optimized
APK as an update, preserving the synthetic session:

```sh
adb -s TASK_SERIAL reverse tcp:19557 tcp:19557
adb -s TASK_SERIAL reverse tcp:19558 tcp:19558
adb -s TASK_SERIAL install -r /absolute/artifacts/release-test-signed.apk
```

Write `external`, `image`, `redirect_port`, `redirect_host`, `same_origin` or
`fallback` to `/absolute/artifacts/release-mode.txt`, then force-stop and launch
the app. Open Profile and verify that the avatar decodes (or the initial is
displayed for `fallback`). Inspect `release-network.jsonl`: the external initial
request and cross-origin redirect targets must have `auth_present=false`;
Workspace image requests and same-origin targets must have
`auth_matches_fixture=true`. Rotate, resize and restart the process with these
fixtures as well. Logs deliberately store only header-presence/match booleans.

Require instrumentation code `-1` and `result=PASS` when seeding. Remove the
task's port forwards and emulator after the run. Never use this setup on a
user's installation. Gravatar's exact unauthenticated request is checked by
`AvatarImageRequestTest` without contacting the public service.

## Chat recovery after an expired event cursor

`RealtimeRecoveryInstrumentedTest` uses a loopback HTTP/WebSocket peer to exercise
the actual event connection and message-history reload. It covers close code
4410, the typed `epoch_pruned` error, HTTP 410 from the periodic event probe,
a failed open-chat history request followed by retry, and replay after the snapshot
boundary without losing or duplicating messages. Recovery refreshes only the open
chat; inactive cached chats stay stale until opened, including non-empty caches.
An inactive chat returning 503 must not delay recovery or subsequent live events.
The suite also checks lazy history retries, reconnecting with no open chat, and
closing a chat while its history request is blocked.
It also holds an uncached chat's pre-expiry history response until after recovery
with no chat open, then checks that the obsolete response is rejected and the
chat stays stale until reloaded on opening. Another case overlaps an ordinary
open-chat load with recovery: the fresh history is empty after a missed deletion,
and the older response must not resurrect the deleted message in either cache
or the ViewModel flow. Live delivery and reopening the fresh chat are checked too.
The probe case waits for the normal 30-second heartbeat. Sessions and DataStores
are isolated; no live account or backend is required.

```sh
ANDROID_SERIAL=TASK_SERIAL ./gradlew :app:connectedExordosDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.genesiscorporation.workspace.beta.data.RealtimeRecoveryInstrumentedTest
```

`MessageHistorySnapshotTest` covers pending sends and concurrent incoming messages,
edits, deletions and HTTP send confirmations while history is loading.

## Inbox topics and unread navigation

`InboxUnreadTest` covers the notification policy for all three stream modes and
all four topic modes, active counters, mention selection and stable unread
anchors. Topic overrides (`follow` and `unmute`) take precedence over the stream.

`InboxInstrumentedTest` uses the actual inbox, navigation and chat screens with
isolated loopback accounts. It checks empty topic caches, late stream snapshots,
per-topic counters, hidden muted/read/passive topics, live counter changes,
first-unread navigation and a first unread mention after earlier passive unread.
During a delayed topic response, 24 counter updates must leave the current
request running and produce just one pending refresh with the latest counter.
It also renders the actual Chats topic preview before and after Inbox refreshes:
matching cached previews survive message-fetch errors, new previews are loaded,
and a changed last-message UUID cannot retain an older preview.
The encrypted case verifies locked messages on a device without the key. Opening
an inbox topic must neither jump to the newest message nor mark the whole history
read; new messages must preserve the selected unread position.

```sh
ANDROID_SERIAL=TASK_SERIAL ./gradlew :app:connectedExordosDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.genesiscorporation.workspace.beta.modules.homeinbounds.InboxInstrumentedTest
```

## Encrypted runtime binding and reopen

`EncryptedChatRuntimeInstrumentedTest#initialObserverCannotInvalidateANewerAccountBinding`
holds the repository monitor while an initial observer captures its empty binding
and another thread starts a session. The delayed observer must preserve the newer
binding. The existing runtime cases still reject revoked credentials, changed
owners and deleted streams; invalidation must remain independent of network work.
The removal test holds the repository monitor while dispatching each IO step so
that a fast inline completion cannot skip its queued resumption boundary.
The conversation retry test waits for the rendered inline link's accessibility
action, including when rejection history appears during a refresh. Pointer-range
actions remain covered by the service-state UI tests. The retry test holds
the retry response until the server has received the request, then waits for the
runtime's acknowledgement before asserting the waiting UI. Account-switch cases
wait for the selected ID, selected configuration and credential flows together.
Conversation and stream-info fixtures save the complete account before creating
their Main-bound `UserViewModel`, then require a matching ID/configuration/token
snapshot. Stream-info timeout diagnostics expose only readiness booleans.

`disconnectedImageResponseDoesNotPoisonSubsequentDownloads` holds an image GET
response while its client closes with TCP RST, then decodes a fresh request through
Coil/CIO. The peer retains recognized broken-pipe/reset reasons separately and
records successful downloads after writing the body; malformed requests,
non-image responses and other transport errors still fail the checks.
The conversation test retains authenticated image-hash and rendered-pixel checks.
Its edit path deliberately hides the IME before sending. The helper accepts
either shown or dismissed keyboard state, requiring stable IME visibility,
native window and reachable button coordinates for 200 ms within 5 seconds.
It retains the actual send action; failures report window focus, IME visibility
and button reachability.

For the optimized APK, reuse the dedicated-emulator seed and signing procedure
under **Avatar credential boundary**, then run the encrypted host fixture:

```sh
./gradlew --no-daemon -p app/tests/host runEncryptedRuntimeFixture \
  -PencryptedRuntimeArtifacts=/absolute/artifacts
adb -s TASK_SERIAL reverse tcp:19557 tcp:19567
```

Open **Encrypted runtime fixture** in the optimized app. The host starts in `fail`
mode: the failed key-request POST must leave the conversation's retry link available.
Restart the app, write
`reject` to `/absolute/artifacts/runtime-mode.txt`, and retry. The second entry in
`runtime-attempts.jsonl` must have `matches_first_request=true`, proving the failed
request retained its exact canonical identity and public payload across restart.
Reopen the conversation to load the synthetic rejection. Set the mode to `pending`
and press its retry link; expect a new request, then the waiting state after a
further process restart without an additional POST. Repeat navigation and rotation.
The fixture keeps only synthetic wire records in memory and logs request equality
booleans, never local private key material. Stop the host and remove the task's
port forward and emulator after testing.

## Protected message palette and body contrast

`EncryptedConversationUiTest#decryptedMessageBodiesRemainReadableAcrossLiveThemeChanges`
uses the real authenticated chat and native Markdown `TextView`. It requires
rendered glyphs and at least 4.5:1 body/background contrast for incoming and
outgoing decrypted messages in light/dark/light mode without remounting the chat.
The existing missing-key case checks the normal bubble, author, time, alignment,
blur and transition back to the decrypted body.
`lockedMessageBubblesFollowThePaletteAcrossLiveThemeChanges` checks incoming and
outgoing locked bubbles in the same theme sequence. Both cases sample actual
bubble, author and topic-marker pixels against the selected workspace palette.
The own author's `Text` is captured separately from the body, with a 4.5:1
contrast minimum; the protected-topic marker must reach 3:1 on its bubble.
The invalid-ciphertext placeholder case also checks its rendered error text
against the palette during light/dark/light changes.

For an optimized APK, reuse the **Avatar credential boundary** seed/sign/update
procedure and `runEncryptedRuntimeFixture`. On a clean dedicated emulator, add
`-e seedProtectedBodies true` to the seed invocation. The driver saves a key in
the production secure store and exports only synthetic encrypted bodies:

```sh
adb -s TASK_SERIAL pull \
  /sdcard/Android/data/com.exordos.workspace/files/protected-body-payloads.json \
  /absolute/artifacts/protected-body-payloads.json
./gradlew --no-daemon -p app/tests/host runEncryptedRuntimeFixture \
  -PencryptedRuntimeArtifacts=/absolute/artifacts \
  -PencryptedRuntimePayloads=/absolute/artifacts/protected-body-payloads.json \
  -PencryptedRuntimePort=19577
adb -s TASK_SERIAL reverse tcp:19557 tcp:19577
```

Install the same-certificate optimized APK as an update. Open **Encrypted runtime
fixture** and verify both protected bodies, then change the system light/dark
mode while the conversation remains open. Check the historical locked body and
the themed invalid-ciphertext error, then repeat after process recreation and
rotation. The optional host port permits
independent runs; its default and the existing request/retry modes are unchanged.
Neither the key nor real account data belongs in the exported payload file.

## Mentions topic navigation

Pinned Mentions queries opt into the shared HTTP client's session recovery. A 401
may renew the still-current credential through the common refresh mutex and token
store, but never replays that captured query. The owner-only
`EventsRepository.verifyCurrentAccount()` path shares `/me` verification. A
same-owner check returns before bootstrap and preserves shared state on failure.
A proven owner replacement clears account projections and runs the existing
owner-scoped metadata bootstrap without starting another realtime loop. Chat
therefore reloads even when Mentions is never created; failure exposes its Retry
UI. Every bootstrap stage carries the captured owner generation, and reconnect
captures a fresh owner-bound cursor before replacement snapshots, so replay
covers events arriving during their reads. The Chat reconnect regressions check
that cursor HTTP request precedes the first metadata request.

Every asynchronous topic-cache producer carries a `TopicCacheScope` captured before
IO. Publication checks the repository lineage, account generation, owner, server
and credential while holding the same lock as owner verification. Explicit login
replacement revokes the generation even if token values are reused. All account
projections are hidden on revocation. A private in-memory snapshot
can be restored only after `/me` confirms the same owner; a replacement owner
discards it. Ordinary same-owner refresh and unchanged-credential verification
preserve valid caches. Realtime uses the scope
captured for its connection. Immediate projection fixtures have test-only seeding
extensions; production has no unscoped metadata-write overload.

`readTopicCache` and `readTopicSnapshot` share the bounded owner-bound read for chat,
inbox, drafts and forwarding. Metadata and optional message enrichment form one
attempt. A credential change discards the whole old result, confirms `/me`, and
starts one fresh attempt only for the same owner; a replacement owner never
receives the old IDs. Atomic snapshot publication also guards message enrichment.
Pinned encrypted-runtime reads retain their existing mutation/session policy.

A confirmed owner replacement also clears the shared stream/user directory.
The private suspended snapshot preserves same-owner data needed to restore
encrypted chats after fresh `/me` proof. Mentions waits
for a fresh owner-scoped directory before publishing rows, including previously
unseen streams and colliding stream/user UUIDs. It reads the current repository
user flow directly so author/avatar data cannot remain in an unsubscribed
derived flow. Directory snapshots use the same bounded captured-session recovery;
late bootstrap responses cannot overwrite another owner's data or advance its
loading state. Folder snapshots and the selected filter are scoped as well.
Explicit credential replacement hides every public account projection immediately.
A private suspended snapshot is restored only after `/me` proves the same owner,
including topic/history caches used by retained Inbox, Drafts and Chat consumers.
A different owner discards it. Failed proof keeps data hidden and exposes Retry.
Login and OTP start the bounded common verification without waiting for a websocket
frame. Regressions reproduce all four paths on the previous APK.

Project selection finalizes login only after the project-scoped credential and
project configuration are persisted. The shared login hook waits for the current
configuration snapshot, gets the matching repository and starts the existing
workspace bootstrap for the final project. Regressions cover reselecting the same
project after preliminary Login verification and selecting a different project.

Credential revocation also closes the captured active websocket through the
existing connection loop, without waiting for an incoming frame or the 30-second
probe. A handshake completing after revocation is rejected before publishing its
session. Real loopback websocket regressions keep the old peer quiet and require
replacement-credential live messages within five seconds, covering both an active
session and a delayed handshake.

Retained Drafts observes the repository's current draft list and verified owner,
cancels superseded topic loads and reads direct repository flows. A new owner's
draft emission therefore starts a scoped topic load without recreating the screen.
Same-owner token refresh does not restart that observer, preserving the shared
one-refresh budget; a repeated 401 exposes Error. UI regressions cover replacement
while idle and with an old request in flight, plus the bounded expiry control.

Regressions cover replacement names/avatars, different stream IDs, failed reloads,
successful delayed bootstrap responses and expiry on both directory endpoints.
Same-owner directory reloads preserve an already loaded last-message preview
only when its UUID still matches the fresh stream metadata; replacement-owner
clearing prevents carrying the previous account's preview across.
The real `ChatWithTopics` regressions cover realtime replacement without Mentions,
failure/retry, and a delayed old folder response. Their account fixture enables
WebSockets so they also verify a real reconnect request after the bounded reload.

Regressions delay successful Chat/Inbox metadata and enrichment responses across
login replacement, reuse stream/topic UUIDs across owners, and verify 403 handling,
shared Home/Chat state after owner-only 503, primary/enrichment expiry recovery,
concurrent recovery and bounded retries. The regular bootstrap failure path remains
covered separately. Adjacent Inbox, forwarding, encrypted runtime, realtime,
notification/deletion projections and transport checks cover the shared boundary.

`MentionNavigationInstrumentedTest` clicks the actual `HomeMentionsScreen` row,
then decodes `HomeFlow.ChatDialog` and passes those arguments to the production
dialog. It verifies message topic UUID/name, visible history, real HTTP query
parameters, cold and cached default history, cached topic metadata, lookup retry,
stream/topic mismatch, account switches and same-server replacement logins during
message/topic requests or after row rendering. A credential change must verify its
owner and perform a fresh bounded load before publishing results. It covers
public topics, two-member private chats and self conversations using isolated synthetic
accounts and the existing loopback HTTP fixture.

```sh
ANDROID_SERIAL=TASK_EMULATOR ./gradlew :app:connectedExordosDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.genesiscorporation.workspace.beta.modules.homementions.MentionNavigationInstrumentedTest
```

Private-chat presentation continues to use the existing privacy contract.
The tests preserve conversation classification and use two-member private chats.
A mixed cached/missing-topic lookup failure keeps cached mentions usable. The
unresolved row retries only missing metadata, without repeating the messages
request or hiding cached rows when that endpoint is unavailable. The retry is
disabled while the lookup is pending. Account invalidations clear loaded bodies
even while the Activity is stopped and its Mentions view model is retained.
Repeating /me with unchanged credentials must preserve the loaded list and route.
A credential refresh first clears old bodies, verifies the same owner, then loads
fresh rows automatically. A verified replacement owner rebinds the retained model
and loads that account's rows, including after the Activity resumes and on explicit
reload. The old rows stay cleared while verification is pending. Delayed responses
remain bound to the credential that sent the request and cannot replace the new
owner's rows or metadata. Superseded requests cannot publish bodies or metadata.
Both messages and topic lookups pin the verified credential, so a delayed 401
cannot replay the old operation or its topic UUIDs as a replacement account.
The HTTP regressions confirm the held response is 401 and wait for the old
operation to finish before checking the wire log; the two accounts have different topic UUIDs.
Switching away and back must restore the original organization; returning to the
original owner after another login also restores its list. A cosmetic organization
name change must not leave a retained screen blank. A first-open model after
replacement login must adopt the verified owner rather than cached /me data.

For optimized APK acceptance, reuse the debug seed and same-certificate release
update under **Avatar credential boundary**. Run the Kotlin host fixture with
the following command, then forward
the seeded device port 19557 to that host port. Open My activity → Mentions and
select public/private default and non-default rows. Self-chat coverage remains in
the instrumented matrix. Check
`navigation-network.jsonl` for the exact selected topic UUID and authorization
match booleans, and inspect the rendered header/history after cold entry, cached
default history, process recreation, rotation and window resizing. Stop only the
task's host fixture and remove its emulator/port forward afterward. No deployed
backend, production account or physical device is needed for these checks.

```sh
./gradlew --no-daemon -p app/tests/host runNavigationFixture \
  -PnavigationArtifacts=/absolute/artifacts -PnavigationPort=FREE_PORT
```

The fixture's Epoch HTTP contract is checked separately against the numeric
version and string generation expected by the Android DTO:

```sh
./gradlew --no-daemon -p app/tests/host runNavigationFixture \
  -PnavigationArtifacts=/absolute/fresh-artifacts -PnavigationPort=FREE_PORT \
  -PnavigationVerifyEpoch=true
```

The loopback host fixture has four workers, a 32-task queue and a 2 MiB request-log
cap. Its resource regression exercises both the actual executor and log writer:

```sh
./gradlew --no-daemon -p app/tests/host runNavigationFixture \
  -PnavigationArtifacts=/absolute/fresh-artifacts -PnavigationPort=FREE_PORT \
  -PnavigationVerifyLimits=true
```

To check partial lookup failure in the optimized APK, write `cached-only` to the
fixture's `navigation-mode.txt` before opening Mentions. Then write `fail-topics`
and retry an unresolved row: cached rows must remain visible and usable after the
503. Write `fail-messages` and retry again: metadata must resolve without another
mentioned-messages request, and the selected row must open its actual topic. Write
`normal` before the ordinary navigation matrix.
The fixture returns only synthetic data in every mode.

For optimized token-expiry acceptance, set `navigation-mode.txt` to
`expire-messages` or `expire-topics` before opening Mentions in a freshly seeded
fixture account. The fixture rejects the old credential on that endpoint and
returns a synthetic renewed credential from the real refresh path. Require one
refresh, a subsequent `/me`, a fresh mentions query, and correct topic navigation;
`refreshed_fixture` in the bounded request log records credential use without
logging token values. These checks use the loopback peer, not a deployed IAM service.

For optimized coverage of the shared snapshot reader, the host fixture also
supports `expire-enrichment`: it rejects the old credential on a messages-by-IDs
request and supplies synthetic last-message IDs for topics. Open Inbox and require
one refresh, owner verification, a fresh metadata/enrichment attempt, and the
selected topic history. `expire-topics` on the Chat topic list exercises the same
owner-bound path. The fixture remains loopback-only and logs matching booleans,
never credential values.
