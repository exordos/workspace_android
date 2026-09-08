# Android project instructions

## Test location

- Keep all app tests and test-only fixtures together under `app/tests/`.
- Use `app/tests/unit/` for local JVM tests and `app/tests/instrumented/`
  for tests that require an Android device or emulator. These are separate
  Gradle source sets within the common test directory; keep their runtimes
  and dependencies separate.
- Preserve package paths under each source set's `java/` directory. Keep the
  test APK manifest and Android-only fixtures in `app/tests/instrumented/`.
- When changing test locations, update the mappings in `app/build.gradle.kts`
  and verify that both test suites still discover and run the expected tests.
  See `app/tests/README.md` for the commands.
