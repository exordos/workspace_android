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
