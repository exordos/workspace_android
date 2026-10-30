# Workspace Android

A native Workspace client for Android: organizations and projects, channels and
direct conversations, topics, messages, attachments, notifications, and encrypted
chats. The app uses Kotlin and Jetpack Compose. It combines server REST APIs with
an event stream; the server remains the source of truth.

## Documentation

| Document | Contents |
| --- | --- |
| [Architecture](docs/architecture.md) | Components, dependencies, state, HTTP, events, storage, and security boundaries |
| [Development](docs/development.md) | Environment setup, builds, conventions, and change workflow |
| [Features](docs/features.md) | Implemented user flows and limitations |
| [Testing](docs/testing.md) | Test levels, regression map, and acceptance criteria |
| [Encrypted chats](docs/encrypted-chats.md) | Wire format, cryptography, keys, and lifecycle |
| [Specialized test procedures](app/tests/README.md) | Loopback scenarios, UI, release smoke tests, and Android fixtures |
| [Web cryptography interoperability](app/tests/interop/crypto/README.md) | Bidirectional checks using real crypto providers |
| [Agent instructions](AGENTS.md) | Test locations and quality requirements |

## Quick start

Prepare JDK 21, the Android SDK, a local `google-services.json` for the required
flavor, and the Gradle wrapper as described in the
[development guide](docs/development.md). Run commands from the repository root:

```sh
./gradlew :app:testExordosDebugUnitTest
./gradlew :app:assembleExordosDebug
./gradlew :app:lintExordosDebug
```

Run
`:app:connectedExordosDebugAndroidTest` on a dedicated device or emulator selected
with `ANDROID_SERIAL`. The full UI suite requires a clean test installation;
do not clear a user's saved accounts to meet that requirement.

## Application variants

| Flavor | Application ID |
| --- | --- |
| `exordos` | `com.exordos.workspace` |

The source package and namespace are
`com.exordos.workspace`. SDK and application versions are defined
in [app/build.gradle.kts](app/build.gradle.kts); library versions are in the
[version catalog](gradle/libs.versions.toml).

The documentation describes implemented code. A screen, SDK, or DTO alone does
not establish a complete feature; the [feature catalog](docs/features.md)
identifies those boundaries.
