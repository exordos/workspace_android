# Tests

All app tests live in this directory:

- `unit/`: local JVM tests, mapped to Gradle's `test` source set.
- `instrumented/`: tests and fixtures that run on Android, mapped to the
  `androidTest` source set. Its manifest belongs to the test APK.
- `host/`: local server fixtures run by Gradle, outside the Android source sets.

Keep Java and Kotlin package paths under each directory's `java/` folder.
The source set mappings are defined in `app/build.gradle.kts`; Gradle task names
are unchanged.

From the repository root, run the JVM tests with:

```sh
./gradlew :app:testGenesisDebugUnitTest
```

With an Android device or emulator connected, run the instrumented tests with:

```sh
./gradlew :app:connectedGenesisDebugAndroidTest
```

Use the corresponding `ExordosDebug` tasks for the Exordos package. Select the
dedicated device with `ANDROID_SERIAL` before running a connected task.

`PublicationWindowTest` exercises the signed-out entry screen. Run the full
suite in a clean test installation or a separate Android user; do not clear
someone's saved accounts to satisfy that precondition. HTTP and repository
tests use isolated DataStores and unique token-store IDs.

`StreamMembershipInstrumentedTest` exercises creation, invitation retries,
membership changes and user search through the production screens and HTTP
client with three synthetic authenticated loopback accounts. It does not
replace acceptance against an independently deployed Workspace backend.
Snapshot/event ordering is covered by JVM `StreamMembershipStateTest` cases;
Android tests retain the HTTP contract, delayed-response wiring and visible UI
checks. Membership and login tests share `WorkspaceTestAccount` and the bounded
`LoopbackWorkspaceServer` transport under the instrumented fixtures directory.

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
