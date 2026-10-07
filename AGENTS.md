# Android project instructions

## Google Play technical quality

- For development and release preparation, verify the applicable requirements in [Google Play technical quality requirements](https://support.google.com/googleplay/android-developer/answer/17492799). Read the current source for thresholds, scope, and enforcement dates.
- Include crashes, ANRs, wake locks, memory and bitmap memory by app state/RAM tier, DEX shrinking/optimization/obfuscation, 64-bit and 16 KB compatibility, and sign-in restoration where applicable. Also inspect Console recommendations for edge-to-edge, rotation, and resizable windows.
- Test the optimized release artifact and affected behavior on a device/emulator. Local tests and memory samples do not prove the 28-day P90 production vitals thresholds. Mark unavailable measurements as `NOT RUN` or `BLOCKED`; recheck the uploaded release in Console before claiming compliance.

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
