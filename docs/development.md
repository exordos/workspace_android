# Development

## Environment

The repository's Gradle files define the required versions:

| Component | Configuration |
| --- | --- |
| Gradle | Wrapper 9.2.1; SHA-256 pinned in `gradle/wrapper/gradle-wrapper.properties` |
| Gradle JVM | JDK 21, `gradle/gradle-daemon-jvm.properties` |
| Android Gradle Plugin | 9.0.1, version catalog |
| Android SDK | Compile 36.1, target 36, min 30 (Android 11) |
| Java source/target | 11; separate from the JVM version running the Gradle daemon |
| UI / transport | Kotlin, Compose, Navigation Compose, Ktor, Kotlin Serialization |

1. Install JDK 21 and the Android SDK platform matching the compile SDK. Configure
   the SDK through a local `local.properties` (`sdk.dir=...`) or Android SDK
   environment variables. Use Android Studio with support for the project's AGP.
2. Download `google-services.json` from the existing Firebase project's
   **Project settings > General > Your apps**, selecting the Android app whose
   package matches the required flavor. Place it in `app/google-services.json`;
   building both flavors requires both application IDs in that file. Obtain
   access from the project maintainer if needed. Keep the downloaded configuration
   out of Git, documentation, logs, and test fixtures.
3. Check for `gradle/wrapper/gradle-wrapper.jar`: the current `.gitignore` excludes
   `*.jar`, and the JAR is absent from Git. Restore it from a trusted wrapper, or
   use an installed Gradle 9.2.1 to run
   `gradle wrapper --gradle-version 9.2.1 --distribution-type bin`.
   Review the generated diff and preserve the pinned distribution hash.
4. Run `./gradlew --version`, then the JVM tests and a debug build. Dependency
   repositories are declared in `settings.gradle.kts`; arbitrary mirrors and TLS
   bypasses are not part of project setup.

Prepare local SDK, Firebase, and wrapper files separately in each new worktree.
Do not commit `local.properties` or the Firebase configuration. `app/.gitignore`
contains the module's local exclusions.

The Google services plugin also supports variant-specific configuration, such as
`app/src/exordosRelease/google-services.json`. Release validation checks the
actual file and matching Android client selected by that plugin, including the
project number, Firebase app ID and API-key format. Missing/malformed configuration
or placeholder values that violate these formats fail before packaging, with no
configuration values in the error.
Debug builds can still use a maintainer-provided synthetic configuration for
isolated UI tests; it does not establish Firebase registration or push delivery.

Before publication, verify that the correctly configured app obtains an FCM
registration token on a dedicated device and receives a test notification.
Format validation alone cannot detect a revoked key, API restrictions, or a
server-side delivery problem. Never log or attach registration tokens.

## Crash reporting

Release variants include Firebase Crashlytics using the existing Firebase Android
client selected by `google-services.json`. Firebase's provider initializes the
SDK before `Application.onCreate`, so early uncaught Java/Kotlin crashes can be
recorded. No custom user identifiers, account details, request bodies, credentials,
message contents, Analytics dependency, or manual exception logging are added.
Native symbol reporting is not configured by this integration.

Automatic report collection is disabled in both debug flavors by the debug
manifest. The release SDK uses its default automatic collection policy. Do not
add a persisted runtime override just for development: it takes precedence over
the manifest on future launches of the same installation.

The Crashlytics Gradle plugin injects the build ID and uploads the matching R8
mapping for each minified release. Keep `SourceFile`/`LineNumberTable` attributes
and retain the exact candidate's mapping with its APK/AAB. For local release
verification that must not write to Firebase, exclude the upload task:

```sh
./gradlew :app:assembleExordosRelease :app:bundleExordosRelease \
  -x :app:uploadCrashlyticsMappingFileExordosRelease
```

An authorized publication build must run without that exclusion and verify the
matching mapping upload. Use the corresponding Genesis task for that package.
Do not count a local build or mapping generation as cloud delivery proof.

Before publishing the first Crashlytics-enabled release, agree and update any
required diagnostics disclosure/Data safety answers, confirm the intended
Firebase Android client and Console setup, and verify a synthetic crash followed
by restart in the Firebase dashboard. Do this on a dedicated installation without
real accounts; do not add a crash button to the production UI. Uploading a mapping
or receiving a report does not authorize publishing an app update. Crashlytics
reports appear in Firebase; Android vitals continues to collect independently.

## Commands

```sh
# Quick regression and build
./gradlew :app:testExordosDebugUnitTest :app:assembleExordosDebug
./gradlew :app:lintExordosDebug

# Second application ID
./gradlew :app:testGenesisDebugUnitTest :app:assembleGenesisDebug :app:lintGenesisDebug

# One JVM class
./gradlew :app:testExordosDebugUnitTest \
  --tests 'ru.genesiscorporation.workspace.beta.data.RealtimeTransportPolicyTest'

# Android tests on a selected dedicated device/emulator
ANDROID_SERIAL=TASK_SERIAL ./gradlew :app:connectedExordosDebugAndroidTest

# Optimized artifacts; signing and publication are separate operations
./gradlew :app:assembleExordosRelease :app:bundleExordosRelease
```

APKs are written to `app/build/outputs/apk/`, AABs to
`app/build/outputs/bundle/`, and unit reports to `app/build/reports/tests/`.
Release builds enable R8 minification and resource shrinking; no signing
configuration is defined in `app/build.gradle.kts`. A successful `bundle` task
does not establish a signed or published release.

## Code conventions

- Find the existing flow in the screen model, repository, DTOs, and tests first.
  Reuse the project's Kotlin/Gradle mechanisms and existing fixtures.
- Keep manual dependency assembly through Application and
  `ViewModelFactories.kt`; add an architectural dependency only for a confirmed
  need.
- Define a new backend operation as an `ApiRequest`/DTO and call it through
  `WorkspaceAPIClient.performRequest`. Serialize wire fields using their actual
  `SerialName` values; keep the HTTP status separate from the backend error code.
- Capture the account context before asynchronous work. For a late response,
  check the account, configuration, token, and required revision. Do not retry a
  protected mutation using credentials from a login that replaced the captured
  session.
- Reconcile HTTP responses with realtime events. Test both orderings, repeated
  delivery, cancellation, partial failures, and an empty fresh snapshot.
- Use `viewModelScope` and lifecycle-aware flows; close jobs, sockets, clients,
  and local fixtures. Propagate `CancellationException` to the caller so that
  cancelled work cannot continue changing state.
- Check permissions before exposing or invoking an action, while retaining
  backend-denial handling. Keep messages in the UI after a failed DELETE.
- Keep decrypted message plaintext within its scoped presentation. Local key
  deletion must invalidate previews, quotes, the composer, and selection; wire
  caches remain encrypted.
- Exclude credentials, keys, real accounts, and production history from tests
  and reports. Synthetic values must be clearly identifiable as fixtures.
- Put UI strings in Android resources; reuse shared components and the theme
  from `ui/`. Support edge-to-edge layouts, resizing, rotation, and reachable
  actions while the keyboard is open.
- Keep all tests and fixtures in `app/tests/`: `unit/` for JVM tests,
  `instrumented/` for Android, `host/` for separate server fixtures, `release/`
  for the framework driver, and `interop/` for cross-client checks.
  `app/src/test` and `app/src/androidTest` are not configured source roots.
- Add regressions for observable contracts and failures. Avoid tests that merely
  duplicate implementation. Fix time and zones in formatting tests; control
  delays with bounded waits/signals rather than arbitrary sleeps.

## Change workflow

Check `git status`, the current base branch, and the nearest `AGENTS.md`. Preserve
other people's changes. Describe the affected contract and choose a focused
regression. After editing, run it, the corresponding full suite, the build, and
lint. Verify runtime/UI changes on a dedicated test installation; use an optimized
APK for R8/JNI/serialization changes.

Update the [architecture](architecture.md), [features](features.md), and
[regression map](testing.md) alongside contract changes. Keep individual run
histories and logs in the external verification archive; retain commands,
criteria, and current limitations here. Commit, PR, release, and deployment
operations require authorization within the task's scope.

## Native encrypted mobile push

The per-device registration, HPKE envelope, offline receipt and account-scoped
notification route are described in [mobile-push.md](mobile-push.md). Firebase
client configuration and server sending credentials are separate requirements.
