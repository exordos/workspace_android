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
The source set mappings are defined in `app/build.gradle.kts`; Exordos test task
names are unchanged.
`app/src/test` and `app/src/androidTest` are not configured source roots.

From the repository root, run the JVM tests with:

```sh
./gradlew :app:testExordosDebugUnitTest
```

With an Android device or emulator connected, run the instrumented tests with:

```sh
./gradlew :app:connectedExordosDebugAndroidTest
```

## Notification indicators

Run the repository regressions on a connected Android device or emulator:

```sh
./gradlew :app:connectedExordosDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.exordos.workspace.data.NotificationIndicatorRepositoryTest
```

These cases verify that authoritative topic events update active unread in both
topic caches, including mute/read transitions. Folder events must refresh the
selected folder's items and counter without switching tabs, preserve unrelated
selections, and remove deleted folders with a valid remaining selection or none.
Repeated deletion events must be harmless and must not delete conversations.

Select the dedicated device with `ANDROID_SERIAL` before running a connected task.

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
  -Pandroid.testInstrumentationRunnerArguments.class=com.exordos.workspace.data.remote.WorkspaceRequestHttpContractTest
```

JVM regressions `SessionRequestContractTest`, `DraftRequestContractTest`,
`RequestQuerySerializationTest` and `ResponseErrorTest` cover wire encoding
and error boundaries without Android. `WorkspaceShareTest` belongs under
`unit/java/`, so Gradle discovers the web-link regressions alongside the other
tests. `MessageTimeFormattingTest` fixes the clock and zones; its temporary
default timezone is always restored.

`AddStreamRequestContractTest` checks ordinary, direct and encrypted creation
bodies with default encoding enabled: only `source_name` is sent, and encrypted
creation retains its canonical stream and key-generation UUIDs. The HTTP suite's
`streamCreationUsesSupportedFieldsWithTheProductionJsonConfiguration` repeats
those cases through the actual Ktor transport. The encrypted conversation peer
rejects unknown creation fields so runtime creation/invitation tests cannot
silently accept a body rejected by the backend.

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

`PushWorkerFactoryTest` verifies constructor injection against an isolated
authenticated loopback account, fresh worker instances, fallback for unrelated
workers, and execution through the application's real WorkManager configuration
without launching an Activity. Run it in a fresh dedicated test process to check
cold background startup:

```sh
ANDROID_SERIAL=TASK_SERIAL ./gradlew :app:connectedExordosDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.exordos.workspace.data.push.PushWorkerFactoryTest
```

For the optimized APK, build `:app:buildEmojiReleaseSmoke` and sign its framework
driver and the release APK with the same test certificate. On a dedicated test
installation, force-stop the target and run:

```sh
adb -s TASK_SERIAL shell am instrument -w \
  com.exordos.workspace.cassi.releasetest/.PushWorkerReleaseInstrumentation
```

Require `result=PASS`, `new_startup_worker_succeeded=true`,
`activity_launched=false` and instrumentation code `-1`. Before
`Application.onCreate`, the driver removes Activity tasks left by earlier UI
tests and snapshots existing work IDs. It requires a new successful
startup reconciliation, so an earlier debug run cannot satisfy it. If the
release mapping renames the worker, pass its mapped name with `-e workerClass`.
The driver adds no production keep rules and does not require a live backend.

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
  -Pandroid.testInstrumentationRunnerArguments.class=com.exordos.workspace.data.FirebaseNotificationsTest
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

## Final newline regression

The formatter's new-file and Git-control-file discovery regression runs with:

```sh
python3 app/tests/host/verify_final_newlines.py
```

It uses the real Spotless task and temporary untracked files; GitHub Actions
runs it alongside the repository newline check.

## Firebase release configuration regression

```sh
./gradlew --no-daemon -p app/tests/host verifyFirebaseReleaseConfig
```

This copies the production build script into a temporary Android project and
executes its actual Google services tasks offline. It checks the Exordos client,
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
  -Pandroid.testInstrumentationRunnerArguments.class=com.exordos.workspace.data.FirebaseRegistrationSmokeTest
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
  -Pandroid.testInstrumentationRunnerArguments.class=com.exordos.workspace.data.RealtimeRecoveryInstrumentedTest
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
  -Pandroid.testInstrumentationRunnerArguments.class=com.exordos.workspace.modules.homeinbounds.InboxInstrumentedTest
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

## Project picker after authentication

`ProjectsDuplicateIdentityTest` drives the real HTTP client, login and OTP
screens, project picker, selection and project-scoped token refresh against an
isolated loopback server. Duplicate project UUIDs must render once (using the
first record's metadata), and both authentication paths must still open the
selected project. The suite includes unique-list controls, a failed lookup/retry
and return to credentials. No deployed account or server is used.

```sh
ANDROID_SERIAL=TASK_SERIAL ./gradlew :app:connectedExordosDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.exordos.workspace.modules.projects.ProjectsDuplicateIdentityTest
```

## Crashlytics configuration

`CrashlyticsConfigurationTest` checks provider registration and disabled automatic
collection in an installed debug build. Run it only on a dedicated installation,
without persisted SDK collection overrides. Both brands use the debug manifest.

The existing `buildEmojiReleaseSmoke` task also packages
`CrashlyticsReleaseInstrumentation` for the optimized Exordos APK. Sign this
framework-only driver with the same test certificate as the installed release.
Supply the exact generated `com.google.firebase.crashlytics.mapping_file_id` and
the obfuscated `CrashlyticsUncaughtExceptionHandler` class from that candidate's
`mapping.txt`; no production keep rules are needed for the driver. On an isolated
emulator with networking disabled, run:

```sh
adb -s TASK_SERIAL shell am instrument -w -r \
  -e mappingId CANDIDATE_MAPPING_ID -e handlerClass R8_HANDLER_CLASS \
  com.exordos.workspace.cassi.releasetest/.CrashlyticsReleaseInstrumentation
```

It checks release flags, automatic collection policy in the manifest, the
packaged mapping ID and the SDK's installed uncaught-exception hook under R8.
It does not send an event. Keep cloud receipt/deobfuscation verification as a
separate synthetic crash-and-restart acceptance step before publication.

## Shared date/time contract and Android 12 regressions

Use `data.WorkspaceDateTime` for wall-clock parsing, serialization and display.
API/cache input is an ISO date with `Z` or a numeric offset; sort by the parsed
`Instant`, never by the date string or `LocalDateTime`. New local timestamps use
UTC `Z`. Calendar days and displayed times use the current device zone, with an
explicit zone available for deterministic tests. Epoch-second and epoch-millisecond
formatters are distinct. Invalid/offset-free input throws; existing callers keep
their error handling. Scheduling intervals and monotonic deadlines are durations,
not wall-clock dates.

Run `ChatTimestampRegressionTest` on an Android 12 (API 31) emulator as well as
a current Android runtime. A host JVM accepts numeric offsets in `Instant.parse`
that Android 12 rejects, so JVM tests alone do not cover this crash. The test
uses the reported `2026-10-30T11:39:50.168+03:00` timestamp, mixed-offset history
ordering, key requests, the real chat screen and a pending/confirmed message
sent from `GMT+03:00`. It also checks message neighbors, the latest-history read
boundary and local day separators across UTC midnight. `DateTimeScreensRegressionTest`
checks folder loading and the actual drafts/mentions screens with mixed offsets.
Their HTTP servers, accounts and storage are isolated fixtures.

```sh
ANDROID_SERIAL=TASK_SERIAL ./gradlew --no-daemon \
  :app:connectedExordosDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.exordos.workspace.modules.chatdialog.ChatTimestampRegressionTest,com.exordos.workspace.data.DateTimeScreensRegressionTest
```

`WorkspaceDateTimeTest`, `WorkspaceDateTimeFormattingTest` and
`ActivityTimestampTest` cover precision, invalid input, UTC output, positive and
negative offsets, device-zone changes, DST, stream/topic activity and forwarding
destination order. Keep the device run: a passing JVM test does not establish
that the platform date parser behaves the same on older Android.

## Source-package migration and installed-app updates

The source namespace is `com.exordos.workspace`, matching the existing Exordos
application ID. The obsolete Genesis product flavor is removed; Exordos Gradle
task names are retained. Never change the application ID or signing certificate
as part of a source-package migration.

`NavigationRouteIdentityTest` checks every typed-route name in the current
namespace. `PackageRenameCompatibilityTest` checks the current launcher and
forwarding activity and restores a saved back stack with current destinations.
Production components and serialized destinations use the Exordos namespace.
`AppUpdateReceiverTest` disables a real task's root activity without killing
the process, verifies removal on package replacement and keeps resolvable tasks.
The update receiver removes only this app's unresolvable task roots, so Recents
cannot relaunch a removed activity; it leaves account storage untouched.

For an in-place upgrade on a disposable emulator:

1. Install a pre-migration, pre-push **debug** Exordos APK and sign in to a synthetic local
   fixture. Sign both the candidate optimized APK and framework test driver with
   the same test certificate as that baseline.
2. Install the framework driver built by `:app:buildEmojiReleaseSmoke`, then run
   `adb -s TASK_SERIAL shell am instrument -w -r -e mode seed
   com.exordos.workspace.cassi.releasetest/.PackageUpgradeInstrumentation`.
   It records the UID, first install time and private fingerprints of saved
   accounts and encrypted tokens.
3. Immediately install the optimized APK with `adb -s TASK_SERIAL install -r
   CANDIDATE.apk`. Retain the application data throughout the update.
4. Run the same instrumentation with `-r -e mode verify`. Require code `-1`, the
   same UID, first install time and storage fingerprints. Reopen the app,
   enter the existing project/chat without logging in,
   and check that the local fixture received no new password login.
5. Also keep a home-screen icon and a background task with an open chat from the
   baseline. Replace the APK without force-stopping or clearing that task first;
   verify that obsolete task roots are removed before any instrumentation or
   manual force-stop, that the existing icon opens the new launcher, and that
   Recents opens a current task. Repeat process recreation and
   rotation on the updated app.

This exercises Android's replacement path with a local test certificate; it
is separate from a Play-signed upgrade from the currently published version.
Before release, also test that exact distribution/version/signature path.

To exercise the optimized conference SDK on a 16 KB emulator, grant its runtime
permissions and run `adb -s TASK_SERIAL shell am instrument -w -r
com.exordos.workspace.cassi.releasetest/.SdkReleaseInstrumentation`. Require
code `-1` and initialized native bridge, bundled JavaScript and mounted view.
The driver uses a synthetic invalid conference URL.
