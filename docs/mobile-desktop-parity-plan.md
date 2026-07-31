# Mobile/Desktop Parity Plan

## Purpose

Build the Android client into a first-class Workspace application with the same
user-visible product capabilities as the current desktop client, adapted to
mobile interaction patterns and Android platform services.

The desktop application is the functional source of truth. The mobile Figma
file is the interaction and visual reference. Backend documentation and
implementation are the API contract source of truth.

## Verified baselines

- Android starting point: `64f7faa` (`cassi/android-app-updates`).
- Android upstream reference: `684f2e8` (`workspace_android` `master` at audit time).
- Desktop reference: `3e168762` (`workspace_ui` `master` at audit time).
- Backend contract reference: `f8d59dd` (`workspace_backend` `master` at audit time).
- Android baseline unit tests pass for both product flavors.
- A physical Android 14 phone is available for device acceptance.
- The newer upstream Android OTP work is merged as `1.0.7`. It still diverges
  from the Android starting point and does not contain the later messaging,
  profile, push, and test work, so useful changes must be reviewed and ported
  selectively instead of merging the branch wholesale.

Re-check all moving references before integration or release.

## Source interpretation

The Figma page contains these maintained design areas:

- Calls.
- Sign-in, OTP, password recovery, and password replacement.
- Organization selection and adding an organization.
- Chats and channel/group actions.
- Services.
- Chat/topic list variants.
- Creating direct chats, channels, and group chats.
- Profile, settings, and folders.

The Figma section explicitly marked as drafts that should not be implemented is
not a visual source. This does not remove server-backed message drafts from
functional parity: the desktop draft behavior remains in scope and will use
mobile-native presentation.

## Status legend

- **Present**: implemented and covered by at least a focused automated test.
- **Partial**: a visible path exists, but important contract, state, UX, or
  recovery behavior is missing.
- **Missing**: no production path exists in Android.
- **Adapt**: desktop behavior needs an Android-native equivalent.
- **Unavailable**: the backend contract does not support the capability. It is
  omitted from production navigation until a real end-to-end behavior exists;
  a disabled control, placeholder page, or simulated success is not acceptable.

## Functional-control policy

Every control visible in a production build must have a verified end-to-end
outcome. A screen is not complete merely because it matches a design.

- Maintain a UI action registry mapping each button, menu item, gesture,
  notification action, and deep link to its handler, permission rule, backend
  or platform effect, loading state, success oracle, failure state, and tests.
- Do not ship empty callbacks, TODO handlers, mock-backed product data,
  placeholder pages, permanently disabled primary actions, or controls that
  only close a dialog without performing their stated operation.
- Hide capabilities that are unavailable in the current product contract.
- If neither desktop behavior nor the verified API/platform contract defines an
  interaction, capture the exact mobile state, send the screenshot and a
  focused product question in the originating Workspace topic, and continue
  independent work while awaiting the decision.
- Add a release gate that traverses every reachable production screen and
  exercises every enabled action with success, permission-denied, and
  recoverable-failure assertions where applicable.

## Parity matrix

| Domain | Desktop capability | Android baseline | Target |
| --- | --- | --- | --- |
| Realm discovery | Validate a Workspace server and load public settings | Present | Preserve with strict URL normalization, timeout, TLS, and actionable errors |
| Authentication | Login by nickname or email, password, six-digit OTP | Partial | One state machine with retry-safe OTP, process restoration, and tests for every server response |
| Session lifecycle | Refresh token, logout, expired-session recovery | Partial: refresh is single-flight and owner-bound; manual logout and automatic refresh rejection share one serialized removal path that clears encrypted conversation state before credentials/account removal | Finish background expiry/push cleanup fault E2E and prove retry/backoff behavior |
| Password recovery | Request reset, success state, replace password | Missing | Implement only against verified IAM endpoints |
| Organizations | Multiple saved organizations/accounts and switching | Partial: encrypted identity registry, add/return/logout UI, owner-bound request/refresh isolation, private attachment-cache partitioning, owner-scoped encrypted drafts/outbox and Room catalog/history/member snapshots, account-aware links, and realm-aware push routing that never guesses between same-server accounts; a same-server two-project Pixel E2E passed warm switching, independent cold starts, catalog isolation, and cross-account message focus | Finish real multi-account FCM delivery/rotation acceptance, then automate delayed-request and background-delivery fault cases |
| App links | Open organization, stream, topic, message, invitation | Partial: HTTPS and canonical desktop `ew://open/...` stream/topic/message routes work on cold/warm Pixel starts, including around-message loading and unsaved-account recovery | Publish release-signing-aware `/.well-known/assetlinks.json`, add invitation contract, and automate process-restoration E2E |
| Main navigation | Messenger, inbox/home, calendar, mail, profile, services | Partial | Adaptive bottom navigation with stable back-stack restoration |
| Chat catalog | Direct chats, channels, topics, unread counts, folders | Partial: bounded encrypted Room snapshots restore the active account's streams/topics, full folders/items, users and stream bindings before network startup, retain them through a cold offline launch, label them stale through the global recovery banner, and reconcile newer REST/realtime rows without rollback; schema-v1→v2 migration and physical in-place cold-start acceptance passed | Add controlled corrupt-row, account-switch, storage-pressure and cursor-expiry E2E |
| Search | Global modal, chat/message navigation | Partial: catalog/user search is functional; the maintained desktop explicitly hides its unfinished global-message-search action and the backend marks message search unsupported | Keep the unsupported global control hidden; implement it only after the desktop/backend contract becomes real |
| Inbox | Unread direct messages and channels grouped by conversation | Partial: real all-stream/all-topic projection, direct/channel sections, exact UUID routes, stream fallback, loading/empty/error/retry, account-owner fence and atomic realtime-race retry are implemented; the encrypted owner-scoped catalog plus an independent success marker now restore cached rows or an authoritative empty Inbox after a cold offline start, with non-empty physical Pixel restore/Retry accepted | Complete physical authoritative-empty and cross-client read convergence, plus controlled delayed-response/account-switch fault E2E |
| Feed | Cross-conversation chronological feed and forwarding | Partial: the real global messages endpoint, newest-first keyset pages, chronological rendering, stable prepend anchor, exact-message navigation, refresh/error/retry and verified forwarding are implemented; an owner-scoped encrypted Room projection now restores 500 messages plus the continuation marker and reconciles create/update/read/delete realtime deltas without stale REST rollback | Complete cold-offline/upgrade/realtime physical acceptance, controlled account/in-flight/injected-response faults and long-running accessibility acceptance |
| Activity | Mentions, starred items, drafts | Partial: read-only Starred uses the real `starred=true` filter and the same encrypted owner-scoped timeline/realtime reconciliation; Drafts uses the real paginated server API with exact open/delete/retry/conflict actions and encrypted per-draft local state; mentions/reactions and star/unstar stay hidden as unsupported | Finish multi-draft physical/cross-client acceptance, controlled conflict/fault/process-death cases, and non-empty Starred navigation/pagination/realtime acceptance |
| Folder management | Create, rename, delete, assign, pin, reorder/layout | Partial: current folder/folder-item endpoints now back create, rename, delete, assign, remove, pin and unpin; authoritative refresh follows every mutation | Add drag reorder/layout only when the desktop product contract exposes the same user action |
| Chat creation | Direct chat, group/private channel, public channel | Partial: direct and public/private channel creation resolve the authoritative default topic; channel creation supports description, visibility, announcement and real-user selection | Complete cross-client mutation E2E, permission revocation and retry/failure injection |
| Channel management | Desktop-visible notification, read, membership, folder and topic actions | Partial: notification mode, mark-read, folders and real membership are wired; backend-only archive/delete controls remain hidden | Add only actions present in the desktop product contract or confirmed by a focused product decision |
| Membership | Add/remove users and show roles/presence | Partial: real stream bindings, owner/self removal rules, role labels, active count and multi-add are wired without mock fallbacks; users/bindings are now encrypted, owner-scoped and reused as stale real data after a cold offline start | Complete real-backend permission/restart UI E2E and delayed-response reconciliation |
| Topic management | Desktop-visible create, rename, notification, read and done actions | Partial: all listed actions use current endpoints and update the shared projection; backend-only delete/default/move controls remain hidden | Complete cross-client/restart E2E and request product direction before exposing an action absent from desktop |
| Message history | Initial window, older/newer pagination, focused message | Partial: normal entry requests the bounded latest page and exact earliest incoming unread concurrently; when that unread predates the latest page, its validated detail and strict older/newer context replace the window and remain bidirectionally pageable without route-focus highlighting; exact focus still wins; all keyset paths keep local outbox rows, reject invalid/cross-scope pages, preserve newer realtime edits, and retain focus across recreation; Room schema v4 atomically restores up to 100 latest server messages plus the exact latest/context/unknown mode, context anchor and both validated markers per cached conversation. Trimmed/corrupt/migrated windows expose conservative real continuation and full-refresh Retry instead of false completeness; physical Pixel acceptance covered a 55-message sequence, bidirectional continuation, cold offline history, exact deep-link recovery and rotation | Complete physical v3→v4, process-death marker restoration, delayed-request/timeout/5xx/corrupt-row and storage-pressure E2E |
| Message sending | Text, optimistic row, retry/remove failed send | Partial: an encrypted account/conversation-scoped outbox is persisted before POST; optimistic rows survive Activity/process recreation and a fully cold offline exact-topic start; rejected and ambiguous outcomes expose different retry/verify/remove paths; successful responses are validated against the exact conversation and payload | Expand real-backend/process-failure E2E to attachments, edits and concurrent sends; backend request idempotency is required before ambiguous delivery can be retried automatically without duplicate risk |
| Message editing | Edit own message | Present: canonical one/multi-reply Markdown restores to structural reply tabs; accessible uncached sources are fetched with owner/revision fences and unavailable sources remain safely editable as raw Markdown | Complete controlled conflict/timeout/account-switch E2E for the asynchronous source-restore path |
| Message deletion | Delete own message with confirmation | Present for owned native messages: canonical UUID DELETE, explicit irreversible confirmation, per-message single-flight state, no optimistic removal, recoverable failure, realtime/cache projection cleanup, and text/image/file/call action parity; external-provider edit/delete stay hidden without capability/preflight data | Add provider capability/preflight support, then automate delayed-response/process-recreation and edit/quote-source deletion cases |
| Reactions | Add and remove reactions | Present | Aggregate by emoji/user, optimistic state, rollback and idempotency |
| Replies and quotes | Quote selected text, multiple reply contexts | Present for the maintained desktop contract: Reply replaces the active source while preserving its answer/identity; Add reply creates ordered accessible tabs with isolated answers, remove/reorder/clear actions, readable rendered-text selection and canonical `urn:quote` output. The full session, active tab, ordinary suspended draft and attachments are encrypted and process-restorable with aggregate size bounds | Complete the remaining controlled timeout/5xx/account-switch and cross-client selected-fragment acceptance; keep any action absent from desktop hidden pending an explicit product decision |
| Forwarding | Single/multi-message forwarding with destination picker | Present for the maintained desktop contract: single or ordered multi-selection (up to 32 unique canonical server messages), a retained real destination picker, direct-chat recovery, ambiguity verification, confirmation-gated selection clearing and exact-source navigation are implemented; the desktop-disabled no-op bulk Delete control stays hidden | Finish physical multi-select/post-request faults and cross-topic/direct recovery E2E |
| Rich content | Markdown, mentions, Workspace references, links | Partial: canonical URL URNs unwrap to exact safe external targets; canonical user, message, stream and scoped/unscoped topic URNs resolve unique active owner-bound routes, including exact focused-message context and authoritative direct-chat default topics; malformed, missing, duplicate, archived, cross-scope, response-mismatched, nested, credentialed and unsafe targets fail closed while ordinary HTTP(S)/mailto behavior remains intact. A bounded CommonMark structure model preserves quote/list/code meaning for TalkBack, and physical light/dark acceptance confirms readable ordered lists, blockquotes and inline/fenced code with theme-bound WCAG-AA tokens. Desktop-compatible inline/fenced spoilers and pinned Unicode emoji plus UUID-backed mention metadata are implemented with functional reveal/profile actions and physical Pixel light/dark/rotation acceptance | Finish physical off-page/cross-topic/direct and owner-switch/missing-catalog fault acceptance for Workspace references; retain unresolved/custom/ambiguous metadata as readable inert text |
| Files and media | Upload, cancel, retry, download, preview, gallery | Partial | General MIME multi-upload, progress, safe filenames, protected download/open, image preview and Android Share Sheet draft ingress are present; add durable cancellation/resume, richer video/document preview, camera/paste and gallery |
| Read state | Read-up-to, stream/topic read, unread counters | Partial: message rows carry the backend `read` flag; a strict `read=false&is_own=false`, ascending, one-row lookup finds the global first unread even outside the latest page; no message is marked merely by loading or opening an exact route; after a real list drag, at-least-half-visible incoming unread rows are coalesced to the newest composite boundary and confirmed through canonical `read_up_to`; the only gesture-free case is a resumed short chat whose entire authoritative unread set and fully loaded newest edge are visible; the newest pending boundary is encrypted under the exact account/conversation before POST, survives process death as an explicit Retry/Close decision, and never auto-retries an ambiguous interruption; full `message.read` and batch `messages.read` events update loaded rows, clear a confirmed retained intent and reconcile local badges without duplicate decrements or stale-page regression | Complete authoritative aggregate refresh plus controlled process-kill/storage-failure/cross-client lifecycle automation |
| Typing/readers | Typing indicators and message reader details | Missing | Implement where the current backend contract provides data; otherwise mark unavailable |
| Drafts | Server draft create/update/delete with revision protection | Partial: paginated list, POST/PUT/DELETE, strong ETag validation, strict `412` conflict parsing, bounded retry, exact server-only navigation, three conflict actions, and encrypted account/stream/topic/draft-slot persistence are implemented; multiple drafts in one conversation no longer overwrite each other and their outbox remains conversation-scoped | Complete physical two-draft exact-open/edit/delete acceptance, cross-client conflict/process-death/fault injection, accessibility, and long-running convergence |
| Calls | Jitsi links, active-call guard, incoming call surface | Partial: a new room is opened only after its call-link message receives a valid server confirmation; ambiguous or failed delivery remains in outbox | Foreground/background call lifecycle, audio permission, interruption and reconnect handling |
| Push | Encrypted device identity, register/rotate/delete token | Partial: one Keystore-wrapped HPKE key remains installation-stable while the server registration UUID is account-scoped; owner-fenced registration/switch/logout, legacy-registration cleanup, realm-scoped notification IDs/groups/sound, safe inactive-account switching, explicit same-realm account choice, private lock-screen content and pending-tap recreation are implemented | Complete real FCM foreground/background/force-stop/reboot delivery and token-rotation matrix; implement payload decryption only after the delivery envelope is specified by backend/desktop |
| Realtime | REST catch-up, websocket, epoch cursor reset | Partial: one retained repository owns a foreground-scoped socket; its generation/version cursor is Keystore-encrypted and account-scoped, restored before reconnect, advanced only after an event is applied, and cleared with that account on logout; every foreground/process recovery first drains bounded strict-generation REST pages and only then opens the socket; 20-second protocol pings close a half-open transport within 40 seconds, inbound frames are capped at 2 MiB, and flapping connections back off; REST `410` or socket `4410` clears expired server projections, preserves local outbox rows, and forces authoritative snapshot reload; the global banner reports delayed connecting/backoff and exposes a real foreground-only retry; streams/topics/latest conversations/folders/users/bindings are durable, stream-binding events reconcile the shared projection, and delayed disk hydration cannot revive an authoritative empty list | Persist the remaining sync metadata and complete controlled lifecycle/process-kill/410/4410/half-open E2E |
| Offline | Cache-first desktop state and outbox | Partial: encrypted drafts, realtime cursors and failed/ambiguous outgoing rows survive restart; owner-scoped AES-256-GCM Room snapshots restore bounded catalog/history/member data, Inbox success state, independent Feed/Starred projections, and exact conversation pagination modes/anchors/markers before network success; schema-v1→v4 migration is non-destructive, stale/retry UX is scoped and REST/realtime reconciliation cannot roll back newer rows | Extend the strict persistence contract to remaining sync metadata; add backend idempotency and controlled corruption/storage-pressure/long-outage E2E |
| User profile | View profile, shared channels, status and contact fields | Partial: authoritative refresh/error/retry, exact status/contact fields, authenticated avatar preview, copyable identity values, bounded external-identity badge, binding-backed non-DM shared channels, and safe reuse/create of a personal chat are wired | Expose calls only after the maintained mobile call bridge has a real profile contract; keep desktop media counters absent until they have a real handler |
| Personal profile | Avatar, name, timezone, status | Partial: authoritative self-profile refresh, status/away update and clear, bounded gallery preview/upload, and conditional avatar reset are wired; name/timezone controls stay hidden because the maintained desktop mutation is currently a no-op | Add a verified name/timezone backend contract, camera capture/crop, and complete upload/reset/account-switch fault acceptance |
| Settings | Theme, language, sound, sorting, folder layout, idle timeout | Partial: account-scoped system/light/dark mode, six real notification-sound modes, standard/compact chat rows, personal-unread priority and unmuted-channel priority are persisted and applied; unknown/corrupt values fail to independent safe defaults | Finish resource-backed language, validated mobile folder presentation and lifecycle-enforced idle timeout; keep every unsupported control hidden |
| Diagnostics | Logs, memory/runtime overview, export | Partial: offline redacted snapshot and functional Android share sheet cover build/device/network/notification/settings/cache/account-count state | Add bounded redacted logs, runtime/memory health and support correlation identifiers without identity or content leakage |
| Cache control | Clear cached data without losing credentials | Partial: exact-account attachment cache size and confirmed deletion are wired; credentials, encrypted Room history, drafts, outbox and sibling-account files are deliberately outside that narrow deletion boundary; logout clears the removed account's Room snapshot | Decide and implement a separately labelled Room/Coil cache policy instead of silently widening attachment deletion; finish physical failure/account-switch acceptance |
| Version/update | Version/build details, forced update, licenses | Partial / Adapt: exact installed version name/code/build type and a searchable offline license catalog generated from the selected variant's runtime graph are implemented | Add forced/update-required UX only after Android has a verified signed-distribution/version-policy contract; keep speculative update controls hidden |
| External accounts | Connect, edit, reconnect, disconnect providers | Partial: a functional mobile Zulip account screen now uses strict DTO/request contracts, write-only credential memory, `FLAG_SECURE` while the key is visible, strong-revision updates, bounded owner-fenced pagination, lifecycle mutations, authoritative refresh reconciliation, full-snapshot realtime projection/delete tombstones and bounded capability-unavailable reasons | Complete permission/capability-specific physical acceptance, backend fault injection and credential/process-death checks |
| External chats | Select, deselect, move projections and show operations | Partial: the account-scoped catalog is searchable and exposes real select/confirmed deselect/current-project move actions, bounded safe errors/statuses, same-origin HTTPS source navigation, owner-fenced pagination, authoritative refresh reconciliation and revision-aware projection cleanup; the desktop operation queue now supports paginated/realtime state, risk-confirmed retry and confirmed discard without inert controls | Add large-catalog/operation virtualization, controlled owner/fault/process-death cases and physical/backend acceptance |
| Provider admin | Provider policy, bridge health and lifecycle | Partial: the mobile external-integrations screen independently probes the exact permission-scoped policy/health/bridge resources, stays hidden when all return forbidden, validates bounded sanitized snapshots, updates limits/enabled/custom-CA with a strong revision, confirms realm suspension and bridge suspend/revoke, and hides impossible lifecycle actions | Complete partial-permission/backend fault/account-switch/process-recreation acceptance and large-instance accessibility/soak checks |
| Mail | Secure configured embedded mail surface | Missing | Show only after a validated, navigable Custom Tab/WebView integration exists |
| Calendar | Secure configured embedded calendar surface | Missing | Show only after a validated, navigable Custom Tab/WebView integration exists |
| Calls list | Recent calls placeholder | Missing | Omit until real call-history data and navigation exist; do not copy the placeholder |
| Services | Planned service cards | Missing | Expose only services with a working destination; do not copy placeholder cards |
| Accessibility | Keyboard, focus, touch, semantic labels, contrast | Partial: message contrast is token-gated in both themes and visually checked on Pixel; authentication fields expose their visual labels to accessibility services | Complete TalkBack traversal, 48 dp targets, font scaling, reduced-motion-safe behavior |
| Internationalization | English and Russian UI | Missing | Resource-based English/Russian UI with correct plurals and locale switching |
| White label | Product flavors and brand assets | Partial | No hardcoded product identity in shared UI or network behavior |

## Platform adaptations

Desktop-only mechanics are not copied literally:

- Tray, menu bar, and global shortcuts become notification actions, Android
  shortcuts, app badges, and predictable back navigation.
- Window and multi-window behaviors become activity/task restoration and
  responsive phone/tablet layouts.
- Desktop auto-update becomes the Android distribution update path and a
  mandatory-version gate.
- Browser drag-and-drop becomes picker, camera, share-sheet, and paste flows.
- Desktop hover states become pressed, selected, focus, and TalkBack states.

## Target architecture

### State and lifecycle

- One app-scoped account/session coordinator.
- One retained runtime owns the Ktor client, realtime repository, and push
  registration manager across Activity recreation.
- Unidirectional screen state exposed as immutable `StateFlow`.
- Saved-state keys contain identifiers only; secrets stay in encrypted storage.
- Every long-lived coroutine is owned by an explicit application, account,
  screen, or operation scope.
- Process recreation restores navigation and local state without replaying
  destructive operations.

### Networking

- One configured Ktor client with request timeouts, JSON policy, redacted
  logging, authenticated request middleware, and single-flight token refresh.
- Generic API bodies are streamed through an explicit upper bound before
  decoding so missing or dishonest `Content-Length` cannot exhaust memory.
- Typed endpoint modules mirror the current backend paths and payloads.
- Error taxonomy distinguishes validation, authorization, permission, conflict,
  throttling, unavailable service, timeout, offline, and malformed response.
- Mutations use backend idempotency/revision controls when available.

### Persistence

- The implemented Room slice stores owner-scoped streams, topics, bounded
  latest server messages and independent Feed/Starred projections: at most
  1,000 streams, 10,000 topics, 100 conversations, 100 messages per
  conversation, 5,000 conversation messages per account and 500 rows per
  timeline. Timeline metadata durably distinguishes "not cached" from an
  authoritative empty result and authenticates its continuation marker.
  Individual catalog/message payloads are capped at 256 KiB/1 MiB.
- Every Room payload is AES-256-GCM encrypted through Tink with an
  Android-Keystore-protected key. Owner, entity identity, conversation and row
  position are authenticated as associated data; invalid, oversized, replayed
  or cross-scope rows fail closed without discarding unrelated valid rows.
- Local `local-*` outbox rows are never duplicated into Room. Their existing
  encrypted conversation store remains authoritative.
- Room rows and their keyset are excluded from cloud backup and device transfer.
  Logout clears only the removed owner's rows.
- Expand Room incrementally to reactions and remaining sync metadata under the
  same bounds and owner fence.
- DataStore stores small non-secret preferences.
- Keystore-backed encryption also protects refresh/access credentials, push
  identity material, drafts and outbox state. Push registration UUIDs are
  independently stable per account, so the backend's UUID owner lock cannot
  make a second account overwrite or fail against the first.
- Cache invalidation is owner-scoped; switching organizations cannot show data
  from another account.

The current ignored Firebase file is a local build stub, so it is suitable only
for compiling and non-delivery push tests. A valid brand-matched configuration
must be injected from the team's secret build channel before claiming real FCM
delivery or rotation acceptance; Firebase credentials must never enter Git.

### UI

- Jetpack Compose remains the UI framework.
- Reusable tokens map the maintained Figma colors, typography, spacing, shape,
  and icon roles without absolute screen positioning.
- Phone navigation uses a bottom bar and full-screen detail routes.
- Wider screens use adaptive panes without changing the information hierarchy.
- Every screen defines loading, empty, content, stale, offline, recoverable
  error, forbidden, and destructive-confirmation states where applicable.
- Every reachable control is represented in the UI action registry and has a
  real handler plus an automated or explicit device-level acceptance check.

## Delivery milestones

### M0 — Contract and reliability foundation

- Correct stale endpoint paths and payload assumptions.
- Replace catch-all request errors with a typed error model.
- Add timeouts, safe retry rules, single-flight refresh, redacted logs, and
  lifecycle cleanup.
- Add deterministic unit/contract tests for every touched boundary.
- Introduce a local Firebase build stub workflow that never enters Git.

Exit: all baseline tests pass, new contract tests pass, no token/message content
is logged, no reachable control has an empty/mock/TODO action, and the
code-review gate has no critical/high failures.

### M1 — Authentication, accounts, navigation, and design system

- Integrate the best reviewed parts of the upstream OTP work.
- Complete login/OTP/recovery states.
- Add organization registry and switching.
- Implement adaptive main navigation, resource-based i18n, and shared design
  tokens/components.
- Add App Link routing and process-restoration tests.

Exit: a fresh user and a returning multi-organization user can reach every main
section on the physical device, including after process death.

### M2 — Catalog, folders, creation, and administration

- Rebuild catalog synchronization and local persistence.
- Complete folders/folder items, pinning, notification modes, unread actions.
- Complete direct/group/channel/topic creation and member management.
- Complete every desktop-visible channel/topic action. Keep backend-only
  archive/delete/default/move operations hidden unless the desktop contract or
  an explicit product decision defines their mobile UX.
- Preserve every open management dialog and unsent form across Activity
  recreation; all long forms must remain reachable at landscape heights.
- Keep mutating requests in retained ViewModels and correlate completion by a
  unique request ID so Activity recreation cannot cancel the request, replay
  it, or close a later unrelated dialog.
- Run lifecycle-changing E2E only in dedicated sandbox topics; active work
  topics are read-only test inputs.

Exit: all catalog/admin mutations reconcile through REST, realtime, restart, and
another client without duplicates or stale UI.

### M3 — Message parity

- Add persistent pagination, outbox, retry/cancel, edit/delete, reactions,
  replies, forwarding, files/media, read state, drafts, and rich rendering.
- Complete call-link and active-call behavior.
- Treat message creation as non-idempotent until the backend accepts a
  client-generated idempotency key. Persist before POST, never silently retry a
  timeout/5xx/malformed response, and require verification or an explicit
  duplicate-risk confirmation.
- Keep draft/outbox state Keystore-encrypted, account/conversation-scoped,
  excluded from backup, bounded before rendering, and cleared on logout.

Exit: the message parity suite passes against a real backend and after injected
network/process failures.

Current verified slice: encrypted state-store tests, draft/edit cancellation
restoration, accepted send, offline ambiguous send, manual verification,
warning-gated retry, process restoration, cross-client single-message
convergence, and failed call-link gating pass on the physical Android 14 Pixel.
A fully cold offline exact-topic start restores retained outbox state through
encrypted route metadata; returning online, verification and explicit retry
again converged to exactly one desktop article. The Room-backed stream, topic
and bounded latest-message snapshot now also restores the general catalog and
cached conversations before network startup. Owned native message deletion
now also passes confirmation cancellation, successful cross-client convergence,
offline failure retention, explicit online retry, realtime projection cleanup,
and cold-reload absence checks in the dedicated sandbox topic. Bounded
newest-first and older keyset history loading is implemented with strict cursor
validation and stale-realtime protection. Physical large-history pagination,
offline failure/online retry, exact viewport preservation and
portrait/landscape recreation now pass in the dedicated sandbox conversation;
delayed-request, process-death and controlled server-fault automation remain.
Desktop-compatible forwarding is implemented for text/media/call messages with
real channel/topic and user targets, duplicate-safe direct-chat recovery,
preflight/verification gating, strict `urn:quote` rendering, and exact-source
navigation. The maintained desktop ordered multi-selection contract is also
implemented with a 32-source bound, rotation-safe state, functional Forward and
Cancel controls, and selection clearing only after authoritative confirmation;
the desktop-disabled no-op bulk Delete control is intentionally absent. Unit/
build gates, two single-source and one two-source real physical-device sandbox
delivery, exactly-once visible-desktop convergence, exact multi-source ordering,
same-topic distant-source navigation, duplicate-topic disambiguation, rotation
retention, and offline preflight recovery pass. A deterministic post-request
matrix also proves that timeout, network, 5xx, malformed/unknown exceptions,
and wrong-chat success all require unique verification rather than blind
retry. Physical multi-select/post-request fault injection, cross-topic
navigation, and direct-chat creation/recovery remain open and must not be
claimed as complete.
Desktop-compatible message metadata rendering now includes the complete pinned
Emojibase 17/Zulip shortcode catalog and UUID-backed display, username,
canonical UUID and canonical-link mentions. Unknown/custom shortcodes and
ambiguous display labels stay readable without guessed navigation; code, image,
autolink and destination syntax stays inert. Focused unit and exact six-case
Compose instrumentation coverage pass. Physical sandbox acceptance confirms
known/unknown/inline-code shortcode rendering, both display and canonical-UUID
profile actions, light/dark readability and portrait/landscape recreation on
the Android 14 Pixel. Live ambiguous-catalog and username variants retain
focused automated coverage.

### M4 — Inbox, feed, activity, and search

- Add inbox, feed, mentions/starred/drafts activity, and global search.
- Preserve destination/focus/scroll semantics across navigation and recreation.
- Keep global message search hidden while the maintained desktop hides the same
  unfinished action and the API reports it unsupported; a visible mobile
  control must not get ahead of the product/backend contract.

Exit: every result opens the exact stream/topic/message and returns without
losing the prior list position.

Current verified slice: the mobile Inbox mirrors the desktop unread projection
and uses one real all-topics request rather than an N+1 request per stream.
Archived/read conversations are absent, direct messages and channels are
separate, only unread topics are rows, stream-level unread fallback remains
functional, and duplicate names navigate by exact UUID. Refresh is
single-flight and owner-bound; malformed/duplicate/foreign catalog rows fail
closed, and a catalog changed by realtime during the request is atomically
rejected and retried once rather than overwritten. The encrypted owner-scoped
catalog is paired with a zero-row Inbox success marker, so a cold offline start
can distinguish cached content or a successful empty result from a missing
snapshot without duplicating data or changing the Room schema. The full
110-task unit/lint/APK/test-APK gate passes.
On the physical Android 14 Pixel, the current dedicated sandbox supplied five
unread messages in two exact rows. Room v3 persisted 15 streams, 120 topics,
one Inbox marker and no duplicate Inbox-message rows; a 1.194-second fully cold
offline start restored both rows, kept a real Retry action and converged after
network recovery without a loading flash, crash or ANR. Earlier empty,
portrait/landscape, Back, semantics and 48 dp-control acceptance remains green.
Exact row navigation/read convergence was deliberately not run because it
would mutate the sandbox read state during this cache-only pass.

The mobile Feed now mirrors the supported desktop data contract: one global
`GET /messages/` request omits conversation filters, uses a bounded descending
`created_at + uuid` page and renders the result chronologically. Older pages
are single-flight, validate canonical conversation/message UUIDs and a
non-repeating final-row continuation marker, preserve the visible UUID anchor,
and retain the current page/marker across recoverable failure. Refresh is
account-owner fenced and keeps existing rows on failure. A separate encrypted
Room projection restores the last 500 real rows and its safe continuation
marker before REST succeeds. Realtime create/update/read/delete deltas update
the visible and persisted projection; a bounded sequence journal replays
events received during REST and refuses a stale overwrite if the journal
overflows. Every visible row
action is functional: open resolves a missing catalog target by exact,
owner-bound stream/topic UUID, focuses the exact message, and forward routes
that same UUID into the existing ambiguity-safe forwarding flow.

Unit and contract tests pass. On the physical Android 14 Pixel, online load,
exact focus, pagination, scroll-to-newest, refresh/older-page offline recovery,
same-coordinate prepend anchoring, completed-list rotation and labelled 48 dp
controls pass without crash or ANR. A schema-v3 snapshot encrypted 50 Feed
rows and restored them after a no-network force-stop without an empty/loading
replacement. One real forward received the mobile
server confirmation and appeared exactly once in the dedicated sandbox on a
second visible Workspace client. Controlled in-flight rotation/account switch,
timeout/5xx/malformed injection, large-font/TalkBack, physical realtime
reconciliation and long-running acceptance remain open.

The mobile Starred activity now exposes only the maintained desktop capability:
one real `GET /messages/?starred=true` projection. It reuses the Feed timeline's
bounded keyset validation, chronological rendering, owner fencing, stable
prepend anchor, exact message navigation, forwarding and functional
refresh/error/retry controls. Its independently encrypted Room projection
keeps authoritative empty state and only admits rows that still explicitly
confirm `starred=true`; realtime unstar/delete removes the row, and concurrent
create/update/read events use the same bounded replay fence. An unstarred or
malformed page fails closed. No mentions,
reactions or star/unstar control is rendered because those contracts remain
unsupported. On the physical Android 14 Pixel, the empty result matched the
visible desktop Starred page; online refresh, offline failure, restored-network
Retry, portrait/landscape recreation, Back and labelled 48 dp controls pass
without crash or ANR. Its distinct encrypted zero-row snapshot also restored
after a no-network force-stop: the truthful empty state remained visible beside
the recoverable error and functional Retry instead of becoming an uncached
blocking failure. The primary account currently has no starred row, so non-empty
physical open/forward/pagination is still required and is not claimed.

The mobile Drafts activity now uses the maintained desktop/backend UUID,
revision, and strong-ETag contract. It loads and validates bounded pages,
creates, updates and deletes exact server drafts, reconciles a create that
committed before a failed response, and exposes functional retry plus three
strictly validated conflict actions. Local state and its encrypted index are
account-bound; each selected server UUID has an independent conversation slot,
while the failed/uncertain send outbox remains shared by the conversation so it
cannot duplicate or resurrect during draft switching. Older slotless state
keeps its original key and authenticated-data format.

Focused unit/contract tests, Android instrumented-test compilation and the APK
build pass. A physical Android create/delete, cold absence check, and one
desktop-created draft list/delete convergence pass succeeded in the dedicated
sandbox. Physical two-draft exact open/edit/Back/restart retention, controlled
ETag conflict and post-request faults, large-font/TalkBack, and long-running
cross-client convergence remain open and are not claimed.

### M5 — Profiles, settings, diagnostics, and mobile platform integration

- Complete personal and other-user profiles.
- Add themes, language, sound, sorting, folder layout, idle timeout, cache,
  diagnostics, version/update, and licenses.
- Complete push, badges, notification actions, share sheet, camera/gallery, and
  Android shortcuts.

Current personal-profile slice: the signed-in profile is refreshed from the
authoritative users endpoint and applied only while the same account owner is
active. Status text and away state use the maintained presence action; the
request opts into explicit-null JSON so clearing text or emoji is a real server
mutation rather than an omitted field. Gallery selection opens a local preview
and uploads only after explicit confirmation. Reads are bounded to 25 MiB,
accepted MIME types are checked against both metadata and file signatures, and
the returned user UUID must match the active identity. Avatar reset is offered
only for a resettable uploaded/URL avatar, never for the default Gravatar.
Refresh, status, upload, and reset are single-flight operations with inline,
retryable errors; late results from another account are discarded.

Current version/licenses slice: Profile now exposes one functional About route.
It reports the installed APK's exact version name, version code, and build type.
The selected variant's resolved runtime dependency graph generates a bundled
offline license catalog during the Android build; users can search by
component, coordinate, version, or license and open readable bundled terms.
The generated resource has an instrumented parse/non-empty/license-text gate.
No update button is shown because the maintained Android project does not yet
define a signed distribution endpoint or authoritative version-policy
contract.

Focused contract/format/MIME tests pass. On the physical Android 14 device,
authoritative profile load/refresh, status plus away update, explicit empty
status restore, visible offline refresh and status failures with retry, cold-start
convergence, gallery choice/cancel, preview, and
portrait/landscape/portrait recreation pass. No avatar was uploaded during
this acceptance run, so real upload/reset, account-switch mid-flight,
process-death, server 5xx/malformed responses, cross-client convergence and
large-font/TalkBack remain open and are not claimed. Name and timezone remain
hidden because the maintained desktop `updateOwnProfile` path currently
resolves success without sending a backend mutation; exposing those controls
would create prohibited dead UI.

Current other-user profile slice: the target UUID is canonicalized and must
resolve to exactly one authoritative user. Profile, catalog and binding
requests are owner-bound and run in parallel; an unavailable refresh preserves
the last real profile and shared-channel projection while showing a visible
Retry. Unknown presence is omitted instead of being rendered as a fabricated
offline state. The Channels tab includes only real binding-backed channel
streams and excludes native/provider DMs. A legacy external private row whose
server payload has no channel/direct classifier is treated as ambiguous and
omitted; positively classified channels remain visible, so a legacy DM cannot
masquerade as a shared channel.

The maintained desktop `Open direct messages` action is now present for
internal users and remains hidden for external identities, matching desktop.
Mobile first reuses one exact native direct stream, refreshes the server catalog
before creating anything, aborts rather than creating when that preflight
cannot establish the catalog state, validates a newly returned stream against
the target user, resolves only an exact/default topic, and reconciles a failed or
malformed create response through one authoritative catalog refresh. Duplicate
matches fail closed, rapid taps are single-flight and account-switched results
cannot navigate. On the physical device, authoritative profile load, visible
offline refresh with stale-content retention and recovered Retry, shared
channels with both native and legacy-provider DMs excluded, bounded 48 dp+
navigation/refresh targets, an exact shared-channel-to-topic-list route,
portrait/landscape restoration, and reuse of an existing DM pass. Creating a
new DM was intentionally not run against the
working account; injected create/post-request faults, account switching,
process death and large-font/TalkBack remain open.

Valid HTTP(S) profile avatars now open the existing authenticated fullscreen
zoom viewer; initials and rejected schemes do not become misleading buttons.
Name, displayed email and an authoritative target UUID have explicit 48 dp
copy actions backed by Android's clipboard and a live-region result message.
External users receive a bounded provider badge while the unsupported native
DM action remains hidden. Desktop media-count rows are not copied: the current
desktop elements are buttons without maintained click handlers, so mirroring
them would add prohibited placeholder UI.

Physical Android 14 acceptance now covers the real internal-user avatar in the
fullscreen viewer, portrait/landscape/portrait recreation, accessible Close,
and all three copy actions with visible result feedback. A foreground
instrumented check confirms exact clipboard text and bounded labels and clears
the clipboard afterwards. The shared image viewer's previously clipped
text-only close control was replaced with a readable 48 dp icon action. An
external-user fixture, failed clipboard service and spoken TalkBack remain
open and are not claimed.

Current settings slice: the signed-in profile exposes five settings whose
effects are wired end to end. Theme selection rebuilds the application color
scheme and system-bar appearance. Compact density removes previews and reduces
chat-row height/avatar size. The unread sort flags participate in the real
folder projection after pin priority and before ordinary folder/activity
ordering. Notification sound offers the maintained desktop presets
Default/Subtle/Digital/Glass/Pulse/None. Five bounded WAV assets are decoded
through Android's notification audio usage; every audible preset owns a stable
named-resource channel, while None owns a visual-only channel with no sound or
vibration. Choosing a preset persists it, creates its channel immediately and
previews it; background push resolves the exact active account's preference
before building the notification. Separate channel IDs preserve Android user
overrides instead of attempting the unsupported mutation of an already-created
channel.

Preferences are stored in a dedicated DataStore under a one-way account key;
malformed JSON and unknown future enum values recover field by field. A switch
to another saved account emits safe defaults before loading that owner's
values, preventing transient cross-account reuse.

Language, folder-rail orientation/system-folder visibility and idle timeout
remain hidden. The Android UI is not yet resource-backed, and idle timeout is
not yet enforced by a lifecycle/session coordinator. The maintained desktop
`showSystemFolders` flag currently persists but does not alter its folder
projection, so copying that control would create a visible no-op.

The full unit/lint/APK/androidTest build gate passes. On the physical Pixel,
System/Light/Dark, Standard/Compact, both sorting switches, all six sound
choices, reinstall, force-stop/cold restoration and restoration to the original
defaults passed. The five audible channels have stable
`android.resource://…/raw/…` sound URIs and notification audio attributes; the
silent channel has `sound=null` and vibration disabled. Media metrics confirm
the WAV decoder and notification AudioTrack path; an audible human-listening
oracle and a real background FCM delivery for each preset remain open. The
accessibility dump exposes the sound chips as checked choices and each sorting
row as a checked/unchecked switch instead of an unlabeled clickable card. The
owner key is one-way hashed in the on-device DataStore; an inspection found no
raw server, project or email identifier. The isolated
per-owner/concurrent-update instrumented test, now including sound, also passes
through the device runner without uninstalling or clearing the signed-in
application.

The profile also exposes an exact-account attachment-cache card. It computes
only files inside the one-way owner directory, names the data class being
removed, disables itself when empty, requires an irreversible-action
confirmation, and re-reads the directory after deletion. It deliberately does
not call the encrypted conversation-state store, credential store or account
repository, so drafts, uncertain outbox rows, session tokens and other saved
accounts are outside the deletion boundary. Pure tests prove the exact byte
count and that clearing one account leaves a sibling directory intact.
On the physical Pixel, an isolated 1.5 MiB file produced the exact displayed
size, Cancel retained it, Confirm removed the exact account directory, the card
returned to its disabled empty state, and a cold start retained the signed-in
session. Injected filesystem failure and a two-account switch race remain open.

The diagnostics card builds a fresh report only from an allowlisted value
object: app/build version, generic Android/device fields, validated network
state, notification permission/global enablement, count of Workspace-owned
channels, saved-account count, attachment-cache bytes and non-secret settings.
There is no field capable of carrying a server URL, project/user identifier,
email, token, message or file path. The report opens the real Android text
share sheet and has a visible recovery dialog if no handler exists. Pure tests
pin the deterministic format, forbid identity-field vocabulary and bound/remove
control characters from environment labels. On the physical Pixel, both online
and fully offline snapshots rendered correctly and opened the system chooser;
Back returned to the signed-in app and connectivity was restored. The
permission-denied matrix and injected missing-handler failure remain open.

The Android Share Sheet slice registers both single and multiple share actions
for general MIME content. Incoming text and granted `content://` files enter a
retained destination chooser; archived chats are excluded and channels require
an exact topic, while an existing direct chat uses its authoritative default
topic. Receipt never calls the message endpoint. After confirmation, files are
stream-copied with per-file and account-cache bounds into a one-way
account-scoped directory, then text/attachments are merged in one
owner-fenced read/write transaction with the encrypted ordinary draft. A
concurrent edit keeps its replacement intact and receives the share in its
suspended draft. Cancel, invalid URI, missing grant, zero bytes, limit overflow
and stale account paths fail without a partial draft; ambiguous cancellation
after a storage write starts retains files to avoid an encrypted draft pointing
at deleted content. Parser, catalog, merge, manifest and no-auto-send source
contracts pass. Physical Android acceptance now covers text, a real
Files-provider document, cancel, rotation, cold restoration, missing-grant
failure, exact duplicate-topic disambiguation and deletion of the account-local
cached copy only after the empty encrypted draft is durable. A stable
activity-saved ingress UUID plus an encrypted per-conversation applied marker
make a double tap or process replay idempotent; composer, enqueue and Drafts
deletion paths share the same post-persistence cache cleanup. Physical
multi-file, measured oversize/lying provider, account-switch and
process-death-during-copy faults plus sandbox desktop cross-client acceptance
remain required before this slice is marked complete.

Exit: the settings/security/accessibility suites pass and logout removes all
account-specific background work and push registration.

### M6 — External services and groupware

- Add external account/chat/operation surfaces with permission checks.
- Add only fully navigable mail/calendar integrations, real calls state, and
  service destinations supported by verified contracts.
- Add provider administration only for authorized users.

Exit: mobile behavior matches the actual desktop capability without exposing
desktop placeholder cards or unavailable actions.

### M7 — Hardening and release acceptance

- Complete the E2E, failure, accessibility, security, performance, battery, and
  soak plans.
- Run review, regression, device, and backend compatibility gates.
- Produce a sanitized release report with exact revisions and known limits.

## Review gates

Run the repository code-review checklist after every milestone and after any
cross-cutting refactor. Apply its twelve categories to Kotlin/Compose
equivalents:

1. Architecture and dependency direction.
2. Kotlin type safety.
3. Security and input validation.
4. Compose and data performance.
5. Coroutine, callback, and lifecycle leak prevention.
6. Accessibility and touch behavior.
7. Internationalization and white label.
8. Error handling and recovery.
9. Redacted logging and observability.
10. Cache ownership and staleness.
11. Automated testing.
12. Maintained documentation.
13. UI action registry completeness and absence of dead or placeholder controls.

Critical security, crash, corruption, and authorization findings block the
milestone. High reliability, leak, and type-safety findings are fixed in the
same milestone.
