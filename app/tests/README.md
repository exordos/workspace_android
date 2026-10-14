# Tests

All app tests live in this directory:

- `unit/`: local JVM tests, mapped to Gradle's `test` source set.
- `instrumented/`: tests and fixtures that run on Android, mapped to the
  `androidTest` source set. Its manifest belongs to the test APK.

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
