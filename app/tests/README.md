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
