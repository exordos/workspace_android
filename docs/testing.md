# Testing and regression detection

## Test levels

| Level | What it establishes | What needs separate verification |
| --- | --- | --- |
| JVM, `app/tests/unit` | Parsing, access/state rules, serialization, cryptographic vectors, snapshot/event reconciliation | Android Keystore, real windows/permissions/IO, R8 optimization |
| Android, `app/tests/instrumented` | Production HTTP client with a synthetic loopback peer, DataStore/Keystore, Compose, lifecycle | Compatibility with a deployed backend and external providers |
| Web interop, `app/tests/interop` | Compatibility of real Android/web crypto providers | Complete user exchanges between installed clients |
| Optimized APK, `app/tests/release` and `host` | R8/JNI/resources, emoji database, runtime fixtures in release builds | Production vitals, Play delivery, live backend |
| Dedicated backend and providers | Real IAM, Workspace events, files, push, and calls | Long-term production statistics |

Basic build commands are in [development.md](development.md); specialized
scenarios and isolation procedures are in
[app/tests/README.md](../app/tests/README.md). This checkout has no
`.github/workflows`; the presence of tests does not establish that external CI
runs every level automatically.

## Regression map

The names below identify existing test classes or groups sharing a prefix.
This map describes covered contracts; it does not establish full line coverage.

| Contract / risk | Checks |
| --- | --- |
| HTTP status, refresh/relogin, credential replacement | `AuthenticationReloginPolicyTest`, `ProtectedRequestAuthenticationTest`, `AuthenticationRefreshInstrumentedTest` |
| Incomplete/extended errors, Throwable message, blank-message fallback and JSON round-trip, login/OTP/refresh JSON, query lists, percent encoding | `ApiErrorTest`, `ResponseErrorTest`, `SessionRequestContractTest`, `RequestQuerySerializationTest`, `WorkspaceRequestHttpContractTest` |
| Draft revision / If-Match, read/read-up-to, notification/done actions over real HTTP | `DraftRequestContractTest`, `WorkspaceRequestHttpContractTest` |
| Confirmed account owner, server/project changes | `ConfirmedAccountContextTest`, `VerifiedOwnerAuthenticationTest`, `ServerConfigCompatibilityTest` |
| Event URL, cursor, retries, missed events, stale history | `RealtimeTransportPolicyTest`, `RealtimeRecoveryInstrumentedTest`, `MessageHistorySnapshotTest` |
| Member snapshots, late responses, user search | `StreamMembershipStateTest`, `StreamMembershipInstrumentedTest`, `UserSearchTest` |
| Active unread, topic overrides, first unread message, folders | `InboxUnreadTest`, `InboxInstrumentedTest`, `NotificationIndicatorRepositoryTest` |
| Markdown, URNs, links, snapshots, bounded quotes | `UrnParserTest`, `MarkdownAttachmentUrnParsingTest`, `ForwardedQuoteParsingTest`, `MessageQuote*Test`, `ChatHistoryPresentationTest` |
| Sending/forwarding/deletion/selection, partial failures | `MessageDeletion*Test`, `DeleteMessage*Test`, `MessageSelection*Test`, `MessageForwardingTest`, `Forward*Test`, `EncryptedMessageForwardingTest` |
| External sharing, grants, attachments, safe filenames | `ExternalMessageShareTest`, `ExternalShareGrantTest`, `AttachmentFileNameTest`, `WorkspaceFileUploadTest` |
| Web-link origin/UUID/port/IPv6 handling, device-locale independence | `WorkspaceShareTest` |
| Message time, unit boundaries, Russian declension, timezone/DST | `MessageTimeFormattingTest` |
| Mention composer and emoji | `ComposerMentionTest`, `EmojiReactionTest`, optimized emoji driver |
| Avatar origins/redirects and credential minimization | `AvatarOriginTest`, `AvatarImageRequestTest`, `AvatarRedirectPolicyTest` |
| Remote TLS enforcement before transport, redirect/downgrade rejection, exact loopback exceptions and private error text | `WorkspaceTransportPolicyTest`, `WorkspaceTransportTest` |
| Encryption, key requests/grants/rotation, deletion, stale runtime | `WorkspaceChatCryptoTest`, `ChatKeyStorageTest`, `EncryptedChat*Test`, `EncryptedConversationUiTest`, interop verifier |
| Blurred encrypted bodies in normal bubbles, author/time/alignment, accessibility, narrow windows/themes, key-menu guard and unlock transition | `EncryptedMessagePlaceholderUiTest`, `EncryptedConversationUiTest` |
| Compact conversation chrome, rendered text/icon/lock contrast in both themes, long titles, 200% font scaling, touch targets and sending with the real keyboard | `EncryptedConversationUiTest.conversationChrome*`, `conversationHeaderDoesNotClipTextAtLargeAccessibilityFontScale`, `protectedHeaderUsesThemeLockBeforeTitle`, `composerAttachmentStaysNearLeadingEdgeWithKeyboard` |
| Rendered protected bubble/author/marker palette, own-author/body/timestamp/topic-label/marker contrast, locked bodies and live light/dark changes | `EncryptedConversationUiTest#decryptedMessageBodiesRemainReadableAcrossLiveThemeChanges`, `EncryptedConversationUiTest#lockedMessageBubblesFollowThePaletteAcrossLiveThemeChanges` |
| Protected stream, settings, notifications, call action | `ProtectedStream*Test`, `StreamInfoAvailabilityUiTest` |
| Login/OTP server-error and empty/whitespace fallback Toasts, repeated identical failures, navigation, layout | `AuthenticationErrorUiTest`, `AuthenticationUiTest`, `AuthReviewRegressionTest`, `ProfileNavigationUiTest`, `PublicationWindowTest` |
| Firebase release configuration, token/payload log privacy and notification content | `verifyFirebaseReleaseConfig`, `FirebaseTokenLoggingTest`; opt-in `FirebaseRegistrationSmokeTest` and dedicated notification acceptance |

When extending this map, identify the failure scenario: lost acknowledgement,
account replacement during a request, an empty fresh snapshot after deletion,
revoked membership, a partial batch, an unexpected payload, or cancellation.

## Running and interpreting checks

1. Run the relevant JVM class with `--tests`, then the full JVM suite for that
   flavor. Check both flavors after changing shared requests or resources.
2. Select a dedicated Android device with `ANDROID_SERIAL`. Filter classes with
   `-Pandroid.testInstrumentationRunnerArguments.class=fully.qualified.ClassName`.
   Run the full UI suite in a clean test installation.
3. Loopback fixtures use synthetic accounts, separate DataStores, and unique
   token IDs. Close test jobs/clients and remove only your own fixtures. Do not
   clear a user's saved accounts.
4. Inspect XML/HTML reports and actual test/failure/skip counts. `UP-TO-DATE`,
   `NO-SOURCE`, and successful compilation do not prove that a test ran.
   Rotation checks wait for the software keyboard and require the submit button
   to remain reachable after the window and IME finish resizing.
5. Run lint and `git diff --check`. Run the optimized smoke test for R8-sensitive
   changes; a debug runtime cannot detect a serializer removed by R8.

Line and branch coverage are not measured. Test and file counts do not establish
a coverage percentage. Push delivery, permission denial, and the real Jitsi media
path require separate end-to-end verification; launch/link tests alone do not
cover those flows.

## Google Play: criteria and evidence

Source: [Play Console technical quality requirements](https://support.google.com/googleplay/android-developer/answer/17492799),
checked on October 16, 2026. Mobile/tablet requirements apply; native Jitsi
dependencies require 64-bit and 16 KB checks on the final artifact.

- Current thresholds: overall user-perceived crash rate 1.09%, ANR rate 0.47%;
  per phone model, 8% for each. Excessive partial wake locks: 5%.
- From February 2027: 28-day P90 Anon RSS + Swap, separated by RAM tier and app
  state. For 4/6/8/12/16 GB tiers, foreground limits are
  2/2.25/2.25/3.25/4.25 GB; user-perceived services and background limits are
  1/1.25/1.5/1.75/2 GB. Bitmap limits: 200 MB for services/background,
  400 MB for cached apps.
- From February 2027: apps with DEX size above 10 MB require at least 25% each
  for optimization, obfuscation, and shrinking; check the uploaded bundle's
  metrics.
- From April 2027: zero-tap sign-in restoration for apps with login; check
  Restore Credentials and applicable exemptions.

The release gate checks the optimized APK on a device: launch, login,
chat/attachments, native SDKs, rotation, resizing, edge-to-edge, keyboard, and
memory in foreground/background/cached states. Select the RAM tier using actual
`Total Memory`, rather than the device's advertised capacity alone.

The current build enables R8 and resource shrinking, but that does not measure
optimization percentages. Local memory samples do not establish production P90.
Without fresh release-specific data, Console vitals, 28-day memory, DEX
percentages, and Console recommendations remain `NOT RUN`; compliance has not
been established. Before publication, recheck the source, exact release artifact,
and Console. Store individual run results in the external archive rather than
turning them into a permanent product contract.

FCM ID parsing, cancellation and independent notification intents are covered by
`NotificationMessageIdTest`, `FirebaseNotificationsTest` and the optimized
`FirebaseNotificationReleaseInstrumentation` driver; see [test commands](../app/tests/README.md#fcm-notification-regressions).

`ChatTopicNavigationInstrumentedTest` covers conversation classification at the
production Chats, Inbox, Drafts and Mentions navigation boundaries: private groups
with a null `direct_user_uuid`, public groups, direct/self chats and encrypted private-group
avatars/topic markers. Privacy alone must not select direct-chat presentation.
