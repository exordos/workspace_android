# Android E2E, Reliability, and UX Test Plan

## Objective

Prove that the Android application is functionally correct, recoverable,
usable, secure, and stable during realistic long-running messenger use.

This plan is a living acceptance contract. A scenario is not considered passed
without its stated oracle and captured revision/environment metadata.

## Test levels

| Level | Tooling | Purpose |
| --- | --- | --- |
| Unit | JUnit, coroutine test, Ktor MockEngine | Reducers, parsers, validation, retry, pagination, crypto wrappers |
| Contract | Recorded/synthetic current API payloads | Request path/body/header and response/event compatibility |
| Component | Compose UI test | Screen states, semantics, gestures, focus, keyboard, restoration |
| Instrumented | AndroidX test on emulator/device | Keystore, DataStore/Room, intents, permissions, process integration |
| Backend E2E | Isolated Workspace environment | Multi-client state, realtime, IAM permissions, push registration |
| Device acceptance | Physical phone plus `adb` | Notifications, lifecycle, radio changes, performance, battery, UX |
| Soak | Automated scenario driver and metrics capture | Leaks, cursor drift, duplicate work, battery/network growth |

## Device matrix

Required:

- Physical phone: Android 14, API 34, 1080 × 2340, high-density display.
- Emulator: minimum supported API 30.
- Emulator: API 33 for notification permission behavior.
- Emulator: target API 36.
- Tablet or resizable emulator for adaptive layout.

Run destructive clock, storage, and repeated-reboot experiments on emulators,
not on a personal physical device.

## Stateful test isolation

- Never use an active work, support, incident, or task-reporting topic for
  `done`, archive, rename, move, delete, membership, or notification mutation
  checks.
- Run shared-state checks only in a dedicated sandbox stream/topic owned by the
  test run. Record its baseline before the first mutation.
- Prefer reversible mutations. Restore the exact original name, done state,
  notification mode, membership, and folder placement before declaring the
  scenario passed.
- If an operation has no product-supported cleanup path, use a pre-existing
  sandbox fixture instead of creating persistent debris.
- Read-only rendering, navigation, and contrast checks may use a real
  conversation, but must not alter its lifecycle state.

## Global pass criteria

- No crash, ANR, strict-mode violation, leaked activity, or uncaught coroutine
  failure.
- No lost acknowledged mutation and no duplicate server object.
- No cross-account data, notification, draft, or cursor leakage.
- UI state converges to the server after reconnection or explicit refresh.
- A recoverable failure always exposes a retry or safe fallback.
- A forbidden action is not shown as successful.
- Every reachable enabled button, menu item, gesture, notification action, and
  deep link produces its stated effect or a tested recoverable error.
- No reachable production screen contains an empty callback, mock-backed
  product value, placeholder destination, or permanently disabled primary
  action.
- Logs and exported diagnostics contain no credential, message body, attachment
  bytes, personal email, or authorization header.
- The app remains navigable with TalkBack and enlarged text.

## Authentication and account scenarios

| ID | Scenario | Injection/variation | Oracle | Automation |
| --- | --- | --- | --- | --- |
| AUTH-001 | Add a valid server | URL with/without scheme and trailing slash | One canonical server, public settings shown | Unit + device |
| AUTH-002 | Reject invalid or non-Workspace server | DNS failure, timeout, malformed JSON, wrong product | Actionable error; no saved account | MockEngine + device |
| AUTH-003 | Login by nickname | Valid credentials | Tokens stored encrypted; main navigation opens | Backend E2E |
| AUTH-004 | Login by email | Valid credentials | Same account identity as server response | Backend E2E |
| AUTH-005 | Invalid credentials | Wrong password | Field-safe error; password not logged or persisted | Backend E2E |
| AUTH-006 | OTP challenge | Correct six-digit code | One continuation request; authenticated session | Backend E2E |
| AUTH-007 | OTP failure and retry | Wrong, expired, then valid code | Credentials retained safely; no duplicate session | Backend E2E |
| AUTH-008 | Rotate token during request burst | Concurrent 401 responses | Exactly one refresh; waiting calls replay once | Unit + contract |
| AUTH-009 | Refresh rejected | Revoked refresh token | Account-scoped drafts/outbox and private caches are cleared before the owner-bound account is removed; all work stops and login is shown once | Contract + instrumented + device |
| AUTH-010 | Multi-organization/project switch | Two projects on one server; warm switch; independent cold starts; account-aware message link | State and jobs switch atomically; no old data flash; each project restores its own catalog; the link activates the owning account before fetching/focusing the message | Instrumented + physical Pixel (passed for catalog, cold start, and message link) |
| AUTH-011 | Logout current organization | Push registered, cached messages, retained draft/outbox | Push registration is removed; encrypted conversation state and private cache for only that account are cleared before account removal | Backend E2E + instrumented |
| AUTH-012 | Process death during login/OTP | `am force-stop` | Safe resumable screen; no secret in saved state | Instrumented |
| AUTH-013 | Password recovery | Valid, unknown, throttled address | Non-enumerating result and verified reset flow | Backend E2E |
| AUTH-014 | Cancel adding another account | Existing authenticated account, server-discovery screen | Previous account and navigation restore without reauthentication or data loss | Unit + physical device |
| AUTH-015 | Stale request completes after account switch | Delay original response/refresh until a second account is active | Result is discarded; no token, logout, cache file, or event mutates the new account | MockEngine + instrumented |
| AUTH-016 | Account attachment-cache isolation | Same attachment UUID under two accounts, switch/logout/offline | Distinct opaque cache paths; logout clears only the removed account | Unit + instrumented |

## Navigation and deep-link scenarios

- Cold/warm start into every main tab.
- Back from profile, settings, search, channel information, media, and call.
- Restore each stack after configuration change and process recreation.
- Open stream, topic, message, user, organization, invitation, file, and call
  links when logged in and logged out.
- Reject links with unknown host, wrong organization, missing permission,
  malformed UUID, or unsupported scheme.
- Open notification actions into the exact account/project/conversation/message.
- Confirm repeated delivery of the same intent is idempotent.
- Confirm external links use a safe platform surface and cannot inject an
  authenticated Workspace origin.

| ID | Scenario | Main assertions |
| --- | --- | --- |
| NAV-001 | HTTPS stream/topic link, cold and warm process | Exact saved server/project is selected; channel or topic opens after catalog readiness |
| NAV-002 | Canonical desktop `ew://open/...` link | Exact project opens without inventing a second custom-link contract |
| NAV-003 | Message permalink outside the latest window | Message is fetched by UUID, merged into history, and focused only after the list observes it |
| NAV-004 | Link for another saved account | Request, realtime state, navigation, and attachment owner switch atomically before the route opens |
| NAV-005 | Link for an unsaved account/project | A functional connect-account screen is shown; dismiss returns to the current account |
| NAV-006 | Unsafe or ambiguous link | HTTP, user-info, query/fragment, malformed UUID, path traversal, wrong custom host, and overlong input fail closed |
| NAV-007 | Android domain verification | `/.well-known/assetlinks.json` contains the release certificate and a package-manager verified HTTPS link opens the app without an explicit package |
| NAV-008 | Activity recreation while authenticated | The retained HTTP/realtime/push runtime remains the single owner; the rebuilt UI observes the same repository, catalog, account, and route |
| NAV-009 | Activity recreation with a management form open | Dialog identity, entered text, toggles, selected UUIDs and scroll reachability survive portrait/landscape/portrait; no mutation occurs until explicit submit |
| NAV-010 | Exact topic link after process death while fully offline | If that exact account/stream/topic has retained local work, encrypted bounded route metadata opens only that dialog without a catalog; unrelated, stale, mismatched and route-only state fails closed |

## Catalog, folder, and administration scenarios

| ID | Scenario | Main assertions |
| --- | --- | --- |
| CAT-001 | First catalog sync | Streams, topics, bindings, folders, items and unread totals match server |
| CAT-002 | Cache-first restart | Cached content appears with stale marker, then reconciles without row jumps |
| CAT-003 | Create direct chat | Existing direct stream is reused; a duplicate is not created |
| CAT-004 | Create public/private channel | Role, members, default topic and visibility match request |
| CAT-005 | Create/rename/delete topic | Catalog, routes and realtime events converge on both clients |
| CAT-006 | Toggle topic done | Shared done state appears on another client and survives restart |
| CAT-007 | Add/remove members | Permission checks, role labels and shared channel profile update correctly |
| CAT-008 | Archive/unarchive channel | Catalog placement and open-route fallback remain valid |
| CAT-009 | Notification modes | All/mentions/muted persist and change push behavior |
| CAT-010 | Mark stream/topic read | Counts update locally, on server, in folders and on another client |
| CAT-011 | Folder CRUD | Create/rename/delete survives refresh and respects owner scope |
| CAT-012 | Assign/unassign/pin item | Ordering and unread aggregation remain stable |
| CAT-013 | Concurrent catalog edits | Conflict or newest authoritative event is handled without duplication |
| CAT-014 | Permission revoked mid-dialog | Submit fails safely and UI refreshes available actions |
| CAT-015 | API response races matching realtime event | Stream/topic upsert produces one entity; an older snapshot cannot roll back a newer realtime projection |
| CAT-016 | Partial mutation response | Missing optional owner/default-topic/color fields preserve the authoritative values already in the projection |
| CAT-017 | Member-binding load failure | Add-member candidates remain unavailable until bindings load successfully; retry cannot accidentally offer existing members |
| CAT-018 | Cancel channel form after recreation | Name, description, visibility, announcement and selected members restore, then Cancel closes with zero create/member requests |
| CAT-019 | Activity recreation during catalog mutation | The retained ViewModel owns the request; request-ID completion closes only the matching restored dialog; no duplicate POST/PUT/DELETE is possible |
| CAT-020 | Create accepted, follow-up topic/catalog fetch fails | Existing server stream is retained, default topic is recovered from response/topic/refetch when possible, partial success is explicit, and retry cannot create a duplicate channel |

### Current M2 acceptance coverage

- Physical Android ↔ desktop folder create/rename/delete converged through
  realtime without reload; the temporary folder was removed on both clients.
- In a dedicated sandbox topic, Android `done` and notification-mode mutations
  persisted, the inverse actions restored the exact baseline, and a temporary
  rename appeared immediately in the desktop header and topic list before the
  original name was restored.
- The active task-reporting topic is excluded from all stateful lifecycle
  tests.
- Remaining M2 gates: process restart during an in-flight mutation,
  cache-first restart, controlled HTTP/realtime failure injection, and
  cross-client member/channel creation with a guaranteed cleanup path.

## Inbox scenarios

The maintained desktop Inbox is the product contract. Android loads the
authoritative stream and all-topic catalogs, then projects unread conversations
without inventing a separate inbox endpoint. Stateful read/navigation checks
must use only the dedicated sandbox stream/topic; the active task-reporting
topic is never archived or used for destructive/read-state acceptance.

| ID | Scenario | Main assertions |
| --- | --- | --- |
| INBOX-001 | Mixed direct and channel unread catalog | Direct messages render before channels; catalog order remains stable inside each section |
| INBOX-002 | Read, archived and unread-topic-only streams | Read/archived streams are absent; a stream with zero stream count but an unread topic remains visible |
| INBOX-003 | Multiple topics and duplicate names | Only unread topics render, every row keeps its exact stream/topic UUID, and equal labels never redirect to the wrong topic |
| INBOX-004 | Stream-level unread without unread topics | One functional fallback row opens the channel topic list or resolves the authoritative direct default topic |
| INBOX-005 | Initial/loading/empty states | Initial load is explicit; a successful empty snapshot says there are no unread messages and exposes no inert controls |
| INBOX-006 | Offline, timeout, 5xx or malformed response | Existing projection remains visible; otherwise a bounded error appears; the real retry performs a new single-flight refresh |
| INBOX-007 | Duplicate/blank/foreign catalog identifiers | The complete new snapshot fails closed and the prior projection is not partially overwritten |
| INBOX-008 | Account switches during refresh | Old-owner responses are discarded before any stream/topic state is applied |
| INBOX-009 | Realtime update races refresh | A changed local catalog causes one full retry; a continuously changing catalog keeps its newer projection and exposes retry instead of accepting stale REST data |
| INBOX-010 | Cross-client read convergence | Opening an exact sandbox topic marks it read through the existing conversation path; the Android Inbox row and desktop Inbox converge without duplicates or stale badges |
| INBOX-011 | Rotation/background/back-stack | Selected route, list position and one retained refresh survive portrait/landscape/background; Back returns to the prior chat catalog |
| INBOX-012 | Accessibility and large catalog | All enabled actions have labels and at least 44 dp targets; TalkBack reads title/count/time; a large catalog scrolls without key collisions, ANR or excessive request fan-out |
| INBOX-013 | Cold offline restart after non-empty or empty sync | The exact owner's encrypted catalog plus Inbox success marker restore content-ready unread rows or a truthful empty state before REST; an account never synced remains loading/error rather than claiming empty |

### Current Inbox acceptance coverage

- Pure projection tests cover direct/channel grouping, read/archive filtering,
  unread-topic rows, stream fallback, blank labels, negative/overflow-safe
  counts, duplicate topic names and exact UUID destinations.
- Contract tests prove a per-stream request includes its exact UUID while the
  desktop-parity all-topics request omits the filter.
- Repository tests prove an authoritative snapshot removes stale topic rows,
  records streams with no topics, rejects foreign rows at the boundary and
  atomically refuses a REST catalog if realtime changed either projection.
- The deterministic refresh decision matrix proves a concurrent realtime
  change is applied only after a clean retry and is never silently overwritten.
- The owner-scoped Room catalog now records an independent zero-row Inbox
  success marker after the catalog write. This distinguishes a successful
  empty snapshot from an account that has never synced without duplicating
  catalog rows or changing the schema.
- The full 110-task unit/lint/APK/test-APK gate passes. On the physical Android
  14 Pixel, the online non-empty state reported five unread messages in two
  exact rows from the dedicated sandbox stream. Room v3 held 15 streams,
  120 topics, one Inbox marker and zero Inbox message rows. A 1.194-second
  fully cold offline start restored both rows, exposed a real recoverable
  network error/Retry, and converged after connectivity returned without
  loading flash, crash or ANR. Earlier empty, rotation, Back, semantics and
  48 dp header-action acceptance remains green.
- Exact row navigation and visible-desktop read convergence remain open because
  opening either available sandbox row would mutate its read state during this
  cache-only pass. Controlled authoritative-empty, account-switch and delayed
  response injection also remain open; the active task-reporting topic was not
  opened or mutated.

## Feed scenarios

The Feed is a cross-conversation chronological projection of the real global
Workspace messages endpoint. It never fabricates preview data and never
exposes a forwarding shortcut that bypasses the normal ambiguity-safe message
flow. Stateful acceptance uses only the dedicated sandbox stream/topic.

| ID | Scenario | Main assertions |
| --- | --- | --- |
| FEED-001 | Initial global page | One request omits `stream_uuid` and `topic_uuid`, uses bounded descending `created_at + uuid` pagination, renders chronologically and initially positions at the newest row |
| FEED-002 | Context and author projection | Every row resolves authoritative stream/topic/author labels while retaining exact canonical UUIDs; missing optional labels fall back visibly without inventing a destination |
| FEED-003 | Open row/open action | Both controls open the exact stream/topic and focus the exact message UUID; duplicate names cannot redirect the route |
| FEED-004 | Forward action | The exact source UUID opens the retained normal forwarding picker; one sandbox delivery has the same ambiguity verification, retry warning and exactly-once oracle as `MSG-FWD-*` |
| FEED-005 | Load older at top or explicit action | One request uses the current continuation marker, UUID overlap is deduplicated with the current row winning, and the visible anchor keeps its screen position |
| FEED-006 | Offline/timeout/5xx during older load | Existing messages and marker remain; inline Retry sends the same request after recovery and is never a dead control |
| FEED-007 | Malformed, repeated or unrelated marker | No bad page is merged; pagination fails closed with a recoverable error and cannot loop automatically |
| FEED-008 | Blank/malformed/case-variant/duplicate IDs | IDs normalize to canonical lowercase before merge/navigation; invalid or canonically duplicate pages fail closed |
| FEED-009 | Refresh with and without existing rows | Initial loading, empty, content-refresh overlay and bounded error states are distinct; prior content survives recoverable refresh failure |
| FEED-010 | Account switch during request | Old-owner completion cannot replace the new account's feed, navigate, or start forwarding |
| FEED-011 | Rotation/background/back stack | Retained state keeps pages, pending request and list position; Back returns to the prior catalog; no duplicate request or forward dialog appears |
| FEED-012 | Accessibility, contrast and large feed | All controls have meaningful labels and 48 dp targets, preview text meets theme contrast, long content/labels remain bounded, and rapid scrolling/pagination causes no ANR or request fan-out |
| FEED-013 | Cold offline launch after successful sync | The encrypted exact-owner timeline restores up to 500 real messages and its oldest-row continuation marker before REST succeeds; no fabricated row or login flash appears |
| FEED-014 | Realtime create/update/read/delete during and after REST | Deltas converge once in chronological order; an older edit cannot replace a newer row, read is monotonic, delete removes the row, and events received during a delayed page request are replayed over that page |
| FEED-015 | Realtime journal overflow during delayed REST | The stale REST response is discarded, current visible/cache state remains usable and a functional retry error appears instead of silently losing an event |
| FEED-016 | Damaged/cross-owner/oversized timeline cache | Invalid encrypted rows never render; unrelated valid rows remain, pagination restarts from the oldest survivor, authoritative empty remains distinguishable from no cache, and sibling accounts are untouched |

### Current Feed acceptance coverage

- Pure tests cover chronological ordering, current-row-wins overlap merge,
  malformed/repeated/unrelated cursors, invalid/duplicate conversation IDs,
  canonical UUID normalization, bounded summaries, realtime edit/read/delete/
  unstar reconciliation, stale-edit rejection and safe marker retention after
  bounded eviction.
- The request contract test proves the global feed omits both conversation
  filters while retaining bounded descending keyset parameters.
- The retained ViewModel serializes refresh and older-page work, fences every
  applied response to the active credential owner, keeps current rows across
  recoverable failure, replays a bounded realtime journal over delayed REST,
  refuses overflow rollback and leaves a functional retry marker after a
  rejected older page.
- Exact Room instrumentation covers encrypted/account-scoped timelines,
  authoritative empty state, 500-row bounds, cross-owner/kind ciphertext
  rejection, damaged-row isolation and safe continuation from the oldest
  survivor. Schema-v2→v3 and v1→v3 migration tests are defined.
- Open and Forward share the exact message route. A missing catalog entry is
  resolved by one exact, owner-bound stream/topic lookup; case-variant
  duplicates and foreign topics fail closed. Forward does not duplicate send
  logic: the chat loads/focuses that UUID once and opens the existing verified
  forwarding state machine.
- On the physical Android 14 Pixel, the real online feed, exact-message focus,
  scroll-to-newest, older-page loading and 48 dp labelled controls pass.
  Refresh and older-page network failures retain content plus a functional
  Retry; recovery preserves the same visible message at the same screen
  coordinate. A completed multi-page list also retains its rows and position
  through portrait/landscape recreation without crash or ANR. A schema-v3
  online snapshot persisted 50 encrypted Feed rows; with no active Android
  network, a force-stopped cold launch restored those rows before REST and
  exposed no empty/loading replacement or crash.
- One Feed-originated forward was delivered only to the dedicated sandbox.
  The mobile client received the server confirmation and a second visible
  Workspace client showed exactly one new forwarded article with the expected
  source content.
- Physical realtime/schema-v3 in-place migration acceptance, controlled
  rotation during an in-flight page request, account switching, injected
  timeout/5xx/malformed responses, large-font/TalkBack and long-running
  behavior remain open and are not implied by these passes.

## Starred activity scenarios

Starred is a read-only projection of messages the server explicitly marks
`starred=true`. Mobile does not expose mentions/reactions filters or
star/unstar actions while those maintained desktop/backend contracts remain
unsupported.

| ID | Scenario | Main assertions |
| --- | --- | --- |
| ACT-STAR-001 | Initial Starred page | One bounded global request sends `starred=true`, omits stream/topic filters, renders chronologically and starts at the newest row |
| ACT-STAR-002 | Server row validation | Every row has canonical message/conversation/user IDs, valid time and `starred=true`; a false/missing flag or canonical duplicate rejects the page |
| ACT-STAR-003 | Empty state parity | A genuinely empty response renders an explicit empty state matching desktop, without mock rows or a disabled primary action |
| ACT-STAR-004 | Exact open | Preview and open action resolve the exact stream/topic/message UUID and focus that message; duplicate labels cannot redirect it |
| ACT-STAR-005 | Forward | The exact starred source UUID enters the normal verified forwarding picker; no Activity-specific send shortcut exists |
| ACT-STAR-006 | Older pagination | Marker validation, current-row-wins UUID merge and visible-anchor preservation match Feed; `starred=true` remains on every page request |
| ACT-STAR-007 | Refresh/offline/timeout/5xx | Existing rows survive; empty/error/loading states are distinct and Retry repeats the filtered request after recovery |
| ACT-STAR-008 | Malformed/stalled marker or unstarred row | No rejected page is merged, the prior marker remains retryable and automatic pagination cannot loop |
| ACT-STAR-009 | Account switch during request | An old-owner page cannot replace the new account's Starred state, navigate or begin forwarding |
| ACT-STAR-010 | Rotation/background/back stack | Retained page/request/position survive recreation; Back returns to the catalog and no duplicate dialog/request appears |
| ACT-STAR-011 | Accessibility and contrast | Catalog/header/row actions have meaningful labels and 48 dp targets; text meets theme contrast and long labels remain bounded |
| ACT-STAR-012 | Unsupported-action absence | No mentions/reactions/star/unstar control or placeholder destination is reachable from the mobile Activity surface |
| ACT-STAR-013 | Cold offline empty/non-empty restore | An authoritative empty result remains empty rather than looking uncached; a non-empty encrypted exact-owner projection restores only rows with `starred=true` and its safe marker |
| ACT-STAR-014 | Realtime star snapshot, unstar and delete | Full updated snapshots add/replace confirmed starred rows; `starred=false` and delete remove the exact UUID without affecting Feed or another account |
| ACT-STAR-015 | Concurrent filtered REST and realtime | Events received during a delayed `starred=true` page are replayed; overflow retains current rows and exposes retry rather than accepting stale filtered state |

### Current Starred acceptance coverage

- Contract tests prove `starred=true` is sent with bounded descending keyset
  parameters and without conversation filters. Pure validation tests prove an
  unstarred row fails closed while a confirmed starred row is accepted.
- The shared retained timeline covers owner fencing, single-flight
  refresh/pagination, rejected-page preservation, stable UUID anchoring,
  exact open, encrypted authoritative-empty persistence, realtime reconciliation
  and the existing forwarding state machine.
- On the physical Android 14 Pixel, the primary account's empty result matched
  the visible desktop Starred page. Online refresh, offline
  `Network unavailable`, restored-connectivity Retry, portrait/landscape/
  portrait, Back and labelled 48 dp controls pass without crash or ANR. The
  schema-v3 cache persisted a distinct zero-row Starred snapshot; after a
  force-stop with no active Android network, the screen retained the truthful
  empty state together with a recoverable error and functional Retry.
- The account currently has no starred row and the maintained product exposes
  no supported star mutation with which to fabricate one. Physical non-empty
  row rendering/open/forward/pagination, controlled account/in-flight/5xx/
  malformed faults, large-font/TalkBack and long-running behavior remain open.

## Message scenarios

### History and read state

- Empty, one-message, and large conversations.
- Older/newer pagination with no gaps or duplicates.
- Open around a message from search, feed, activity, push, and a copied link.
- Preserve viewport after prepend, reaction update, edit, delete, and rotation.
- First-unread marker, read-up-to batching, fast scroll, background transition,
  and another-client read.
- Epoch reset/cursor expiry rebuilds projections without deleting messages.

| ID | Scenario | Main assertions |
| --- | --- | --- |
| MSG-HIST-001 | Open a conversation with more than one page | Initial request is a bounded newest-first `created_at + uuid` keyset page; rendering is chronological and starts at the newest message |
| MSG-HIST-002 | Scroll or tap to load older history | The response marker matches the final row, one older page is prepended, UUID merge removes overlap, and the visible anchor does not jump |
| MSG-HIST-003 | Realtime edit races an older page | A stale page cannot replace a row with a newer `updated_at`; UUID remains the stable Compose item key |
| MSG-HIST-004 | Repeated top-scroll/load input | One ViewModel-owned job uses one marker; concurrent input cannot issue duplicate page requests |
| MSG-HIST-005 | Offline/timeout/5xx while loading older | Existing messages and marker remain intact; inline retry requests the same page after connectivity returns |
| MSG-HIST-006 | Boundary marker deleted or rejected | 404/validation/malformed-page failure invalidates the cursor; retry refreshes the latest window instead of looping on a dead marker |
| MSG-HIST-007 | Malformed marker, cross-chat row, or stalled cursor | The entire page fails closed, no foreign/stale row is merged, and a recoverable history error is shown |
| MSG-HIST-008 | Edit while positioned in older history | Stable UUID keys preserve the viewport and the updated bubble replaces its content in place |
| MSG-HIST-009 | Rotation/background during page load | The retained ViewModel owns the single request; completion merges once and restored UI keeps a valid anchor |
| MSG-HIST-010 | Final page | Missing continuation header ends pagination without an empty/dead control |
| MSG-HIST-011 | Open an exact route to a message outside the latest page | The canonical detail endpoint validates the anchor and renders it before the two context requests finish; failure falls back to the usable conversation with an actionable error |
| MSG-HIST-012 | Load context around a route anchor | Descending rows are strictly older and ascending rows strictly newer by `(created_at, uuid)`; wrong-side, duplicate, out-of-order, malformed-time, malformed-UUID, and cross-conversation rows fail the whole affected page closed |
| MSG-HIST-013 | A latest-page projection already exists before opening an old route | The around-message window atomically replaces stale server rows instead of merging a hidden gap, while local `local-*` outbox rows and a newer overlapping realtime edit survive |
| MSG-HIST-014 | Scroll below an around-message window | The ascending continuation loads once near the lower boundary or through `Загрузить следующие`, appends in chronological order, and removes its status control at the real newest row |
| MSG-HIST-015 | One context direction fails | The anchor and successful side remain usable; the failed side exposes a real Retry using the safe boundary, while rejected/malformed later markers reset to the route anchor instead of looping |
| MSG-HIST-016 | Rotate while focused on an old route anchor | Portrait → landscape → portrait keeps the exact anchor visible and does not jump to the latest row when status rows or context pages relayout |
| MSG-HIST-017 | Process death with a latest, context or partially loaded history window | The exact owner's encrypted page mode, context anchor and both validated continuation markers restore with the bounded message rows; offline UI keeps real previous/next actions and a full-refresh Retry, while a missing, trimmed, malformed or damaged metadata/message row becomes a conservative two-sided unknown window instead of claiming a false oldest/newest boundary |
| MSG-UNREAD-001 | Open a latest window containing incoming unread rows | The list initially positions the first loaded incoming unread row at a non-interactive `Непрочитанные сообщения • N` marker; loading alone sends no read request |
| MSG-UNREAD-002 | Open an exact focused-message route while unread rows exist | Exact focus has priority over the first-unread anchor and opening the route still sends no read request |
| MSG-UNREAD-003 | Drag the list until unread rows are at least 50% visible | Only incoming unread rows qualify; one canonical `read_up_to` request uses the newest visible `(created_at, uuid)` boundary |
| MSG-UNREAD-004 | Visible boundaries change while a read request is in flight | One ViewModel-owned request remains active and the newest queued boundary is processed next without duplicate requests |
| MSG-UNREAD-005 | The read response is malformed, cross-topic, wrong-message, or still unread | Local rows and badges remain unread and a visible error is reported; no unconfirmed optimistic read is committed |
| MSG-UNREAD-006 | Receive full `message.read` and batch `messages.read` realtime events | Loaded row flags, topic count and stream count converge; replaying the batch does not decrement projections twice |
| MSG-UNREAD-007 | A page response races a confirmed read | A later/stale snapshot may update content and reactions but cannot resurrect a locally confirmed read flag |
| MSG-UNREAD-008 | Offline, timeout or 5xx during viewport read | Rows remain unread; a dedicated banner exposes functional Retry and Close; Retry keeps/coalesces the exact failed boundary and confirms it after connectivity returns |
| MSG-UNREAD-009 | Rotate before and after crossing the unread marker | The saved marker/gesture state does not cause eager read, duplicate read requests, a jump to latest, or a marker attached to an already-read row |
| MSG-UNREAD-010 | The first unread predates the bounded latest page | The client fetches and anchors the exact server-first unread window instead of silently treating the latest loaded unread as globally first |
| MSG-UNREAD-011 | All unread rows fit without a scrollable drag | Gesture-free read is allowed only while resumed, with no newer page pending, the fully loaded newest edge visible, and every authoritative unread row at least 50% visible; any missing condition preserves unread |
| MSG-UNREAD-012 | Kill the process after a viewport read is durably queued but before an exact response is accepted | The newest canonical boundary is encrypted under the exact account/stream/topic before POST; a storage failure prevents the mutation. Cold restart never auto-retries the ambiguous request and exposes functional Retry/Close: Retry uses the retained boundary even when it is outside the loaded window, while Close durably discards it. Exact REST/realtime confirmation clears it, newer visible boundaries coalesce without regression, draft cleanup cannot delete it, and another account or conversation cannot observe or replay it |
| MSG-RICH-001 | Tap `[label](urn:url:https://host/path?query#fragment)` or its HTTP equivalent | The exact embedded HTTP(S) target, including query and fragment, opens through a browsable platform intent without Workspace credentials or authenticated-origin reuse |
| MSG-RICH-002 | Render empty, malformed, whitespace-containing, nested, credentialed, `javascript`, `data`, `file`, or scheme-relative URL URNs | No external activity launches; the tap fails closed through the existing visible unsafe-link error, and the raw URN is never treated as a local/deep link |
| MSG-RICH-003 | Render ordinary HTTP(S)/mailto links and URL-URN-looking text inside inline or fenced code | Existing safe-link behavior remains unchanged, while code remains inert text rather than a clickable target |
| MSG-RICH-004 | Tap `[channel](urn:stream:<uuid>)` for a regular or direct stream | A unique active regular stream opens its exact UUID topic catalog; a direct/provider stream resolves its authoritative default topic and opens the real dialog instead of fabricating a route |
| MSG-RICH-005 | Tap `[topic](urn:topic:<topic-uuid>)` or `[topic](urn:topic:<stream-uuid>:<topic-uuid>)` | The unique exact topic and its authoritative stream open by UUID even when labels collide; the optional stream scope must match and direct-chat technical topic names stay hidden |
| MSG-RICH-006 | Tap malformed, missing, archived, duplicated, cross-stream, offline-uncached or account-switch-raced stream/topic URNs | No guessed or cross-account route opens; one owner-fenced lookup either produces the exact destination or a visible dismissible error, and repeated taps cannot start concurrent lookups |
| MSG-RICH-007 | Tap `[person](urn:user:<uuid>)` with and without a warm user catalog | The unique authoritative user opens the exact profile UUID; missing optional profile fields remain empty, a cold catalog uses one owner-fenced refresh, and duplicate labels cannot redirect the route |
| MSG-RICH-008 | Tap `[message](urn:message:<uuid>)` for a same-topic, off-page or cross-topic message | The canonical detail response must match the requested UUID and contain canonical stream/topic UUIDs; the client resolves their exact active catalog rows and opens a focused message route that loads the anchor plus bidirectional context |
| MSG-RICH-009 | Tap malformed, missing, response-mismatched or account-switch-raced user/message URNs | No local/external or guessed route opens; one single-flight owner-fenced lookup keeps the current conversation usable and exposes a visible dismissible error |
| MSG-RICH-010 | Render ordered/unordered lists, blockquotes, inline code and fenced code in light/dark themes and with TalkBack | Visual structure matches the maintained desktop CommonMark contract; code foreground/background and every message text pair meet WCAG AA; the shared bounded structure model preserves list/quote/code meaning for accessibility without turning code into links or duplicating message actions |
| MSG-RICH-011 | Render `||inline spoiler||` and fenced `spoiler` blocks, reveal and hide them, rotate, and change theme | Secrets are absent from the initial rendered/accessibility text; every visible spoiler control is functional, localized and exposes hidden/shown state; inline reveal/hide stays inside the message; block reveal/hide and both reveal states survive recreation; spoiler-like text inside inline/fenced code, link destinations, empty/unmatched delimiters and unclosed fences stays inert |
| MSG-RICH-012 | Render known/unknown `:emoji:` shortcodes plus `@**Display Name**`, `@username`, `<@user-uuid>` and `[label](urn:user:<uuid>)` mentions | The pinned desktop emoji catalog and Zulip overrides replace every known Unicode shortcode without inventing custom-emoji URLs; unknown/custom shortcodes stay readable. A unique live user match renders one canonical UUID-only profile link and canonical display label; duplicate or missing display/username matches stay inert readable text, while an explicit canonical UUID remains an exact actionable UUID reference. Inline/fenced code, images, escapes, autolinks and link destinations remain metadata-inert; revealed spoilers and quote text use the same rules |

Current physical coverage: MSG-HIST-001/002, 009–014, 016 and the latest-window
path of 017 passed on the USB Android 14 Pixel using the sandbox topic's
55-message generated sequence. MSG-HIST-003/004/007/010–015/017 have focused
automated contract/model/store coverage; the context-window and deliberately
damaged-window physical paths of 017 remain pending.
MSG-HIST-005 controlled timeout/5xx, MSG-HIST-006 deleted-marker injection and
MSG-HIST-008 edit-in-old-history remain fault/interaction automation work;
merely disabling radios is not counted as proof because an already resolved
in-app route can reuse retained runtime state.

Current unread coverage: MSG-UNREAD-003–007 and 010–012 have focused
request/model/repository tests, including the strict earliest-unread filter,
candidate scope validation, composite-boundary selection, full and batch
realtime frames, duplicate delivery, stale-page regression and the complete
visible-tail decision matrix. The retained read intent has encrypted
account/conversation round-trip coverage, malformed-boundary rejection,
secondary-draft sharing, read-only retention and an unloaded confirmed-boundary
repository test. Latest and
earliest-unread requests run concurrently; an unread already in the latest page
reuses that response, while an off-page unread uses the strict bidirectional
context loader. Physical acceptance in a naturally unread sandbox conversation
is required before marking MSG-UNREAD-001–012 passed. Controlled post-request
offline/timeout/5xx Retry, process kill, storage failure and another-client
confirmation remain physical automation work.

Current rich-link coverage: strict URL-URN normalization, exact query/fragment
retention, case-insensitive prefix handling, ordinary-link compatibility and
malformed/credentialed/unsafe rejection have focused unit coverage.
MSG-RICH-001–003 passed on the physical Android 14 Pixel using messages sent
only in the dedicated sandbox topic: the canonical URL-URN opened Chrome at the
exact `example.com/cassi-mobile?stage=url-urn#pixel` target; a representative
`javascript:` URL-URN kept Workspace foregrounded and exposed the functional
unsafe-link error/Close action; and the same syntax inside inline code remained
inert text. The focused unit matrix covers every remaining unsafe class listed
by MSG-RICH-002 plus ordinary HTTP(S)/mailto compatibility.
MSG-RICH-004–006 have focused parser, unique-selection and route-construction
coverage for regular/direct streams, scoped/unscoped topics, duplicate UUIDs,
archived streams, cross-stream topics and malformed references. Physical
sandbox acceptance passed for a regular stream topic-list route, unscoped and
scoped exact-topic routes, a missing scoped topic and a malformed topic URN;
all five messages were created only in the dedicated sandbox topic. The
missing and malformed references kept Workspace foregrounded, preserved the
current conversation and exposed a functional visible error/Close action.
Direct/provider navigation, owner-switch cancellation and controlled offline
uncached catalog cases remain pending.
MSG-RICH-007–009 have focused parser, unique-user selection, exact-profile
route, message-response validation and focused-route construction coverage.
Physical sandbox acceptance passed for the exact current-user profile, a
same-topic message UUID recovered independently from the delivered server
projection, missing-message handling and a malformed user URN. The valid
message reference opened a focused route and positioned the known target in
the viewport; both invalid references kept the current conversation usable and
exposed a functional visible error/Close action. Off-page/cross-topic message,
cold missing-user catalog and account-switch failure acceptance remain pending.
MSG-RICH-010 has focused structural-model coverage for plain/inline Markdown,
nested quotes, ordered and unordered lists, fenced/indented code, CRLF,
language-label bounds and oversized input. A physical sandbox message containing
bold text, inline code, an ordered list, a blockquote and a fenced TypeScript
block rendered with distinct structure in both dark and light mode; code and
body text remained readable against their own/embedded surfaces. The renderer
uses that same bounded model to expose list/quote/code state to TalkBack while
retaining the established link-capable TextView. An exact two-case Compose
instrumentation run on the Android 14 Pixel confirmed the localized structural
state is present for rich blocks, the real rendered message text remains
visible, and plain/inline-only Markdown receives no fabricated block state.
MSG-RICH-011 has focused parser coverage for multiple inline spoilers, escaped
labels, strict bounded internal actions, matching backtick/tilde block fences,
default/custom headers, ordinary fenced-code isolation and malformed syntax.
An exact five-case Compose instrumentation run on the same Pixel confirmed the
localized block hidden/shown state, nested quote/spoiler composition, real
block and inline reveal/hide taps, and inline-link interception. A message sent
only in the dedicated sandbox proved that inline and block secrets are
initially absent, inline reveal and block
reveal/hide work, and the revealed inline plus block state survives
portrait-to-landscape recreation. The block was hidden again in landscape;
portrait restoration preserved that independent state. Both light and dark
surfaces remained readable, and no crash, ANR, Room or SQLite failure appeared.
MSG-RICH-012 has focused coverage for complete pinned emoji-catalog generation,
known/unknown/custom shortcode behavior, unique UUID/name/username resolution,
ambiguous and conflicting catalog rows, canonical user links, unresolved
readability, code/fence/image/autolink/destination isolation and revealed
spoiler composition. An exact six-case Compose instrumentation run passed on
the Android 14 Pixel. A message sent only in the dedicated sandbox proved that
`:smile:` uses the pinned Unicode catalog, `:party_parrot:` stays readable
without a custom-emoji guess, both `@**Display Name**` and `<@user-uuid>` open
the exact live profile, and the shortcode inside inline code stays literal.
The same delivered row remained readable in light and dark themes and after
portrait-to-landscape recreation; no crash, ANR, Room or SQLite failure
appeared. Username resolution, ambiguous live-catalog rows and the remaining
metadata-isolation matrix retain focused automated rather than live-server
acceptance.

### Current history acceptance coverage

- A physical Android 14 phone opened the newest bounded window in the dedicated
  sandbox conversation, then loaded the older server page through the real
  continuation marker. The older `22 Jul` messages were present after prepend.
- With the phone offline, the existing window stayed visible and the history
  row exposed a functional retry. After validated Wi-Fi returned, retry loaded
  the older page and three sampled message texts retained exactly the same
  vertical coordinates before and after prepend.
- Portrait → landscape → portrait recreation retained the old-history route,
  page and sampled messages. Launch deep links are consumed only on the first
  Activity creation, so configuration recreation no longer reopens the
  conversation at its newest message.
- Unit coverage includes request serialization, marker validation, malformed
  and cross-conversation page rejection, stale-page versus realtime merging,
  viewport correction and recreation positioning.
- A cold, force-stopped exact sandbox topic link with both radios disabled
  restored its Room-backed title and latest server messages in 1.411 seconds.
  The global stale banner remained visible. The chat now also retains a
  scoped cached-history refresh error with a functional Retry because
  continuation completeness is independently recoverable. Portrait → landscape →
  portrait kept the exact chat and cached rows, and restoring connectivity
  removed the banner while retaining the same route.
- Schema v4 model/store coverage persists latest/context/unknown window state,
  authenticates both markers with the owner and conversation, forces a
  trimmed oldest row back to `has more`, and converts damaged or incomplete
  rows to a two-sided unknown window.
- On the physical Pixel, the online sandbox conversation wrote one encrypted
  schema-v4 pagination row beside the retained messages. A force-stop with
  both radios disabled restored the cached latest window, exposed the real
  previous-page continuation as a recoverable network error instead of a false
  oldest boundary, and kept a separate full-refresh Retry. Restoring Wi-Fi and
  retrying removed both scoped/global errors and returned the control to
  `Загрузить предыдущие` without duplicate rows. The same live bubbles and
  metadata remained legible in dark and light themes.
- Remaining gates include automated rotation during an intentionally delayed
  page request, controlled timeout/5xx/deleted-marker injection, process death,
  physical context-window restoration, storage pressure and corrupt-row device
  injection.

### Send and outbox

- Send plain text, multiline Markdown, Unicode, emoji, mentions, links, code,
  quote, and maximum-size allowed content.
- Rapid repeated send and double-tap produce one server message per intent.
- Optimistic message transitions to the exact server UUID.
- Response before websocket and websocket before response converge to one
  server UUID. Timeout, lost response, 5xx, network failure, and malformed
  success remain explicitly ambiguous: there is no automatic POST retry.
- A valid POST response must be own, nonblank, in the exact stream/topic, and
  contain the exact submitted payload before it can clear the local outbox.
- Verification excludes identical messages already present before the attempt,
  requires one unique post-attempt candidate, and never lets one server message
  clear two identical local rows.
- Explicit retry of an ambiguous row requires a duplicate-risk confirmation;
  hiding it explains that only local state is removed.
- Failed rows support retry and removal after restart.
- Force-stop after local persistence but before response converts `SENDING` to
  `UNCERTAIN` and preserves the optimistic row.
- Type and attach new content while an earlier send is in flight; only the
  exact submitted snapshot is cleared.
- Start a call while online/offline/slow: Jitsi opens only after a valid call
  message confirmation and never on a failed or ambiguous result.
- Account switch or logout never sends an old-account outbox entry.

| ID | Scenario | Main assertions |
| --- | --- | --- |
| MSG-OUT-001 | Accepted text send | Encrypted outbox write precedes POST; exact response UUID replaces one local row |
| MSG-OUT-002 | Validation/permission/rate-limit rejection | Row becomes `FAILED`; retry/remove remain functional after restart |
| MSG-OUT-003 | Timeout/network/5xx/malformed response | Row becomes `UNCERTAIN`; no blind retry; verify/retry/hide choices explain consequences |
| MSG-OUT-004 | Process death while `SENDING` | Cold start changes the retained row to `UNCERTAIN` without losing content |
| MSG-OUT-005 | Existing identical message | Pre-attempt UUID baseline prevents false confirmation |
| MSG-OUT-006 | Two matching candidates or two local rows | Ambiguity remains visible; no heuristic deletion |
| MSG-OUT-007 | Type during send | New suffix/text, attachments and reply context not belonging to the snapshot remain |
| MSG-OUT-008 | Call-link send failure | Meeting UI does not launch; the recoverable row remains |
| MSG-OUT-009 | Logout with retained rows | Account-scoped encrypted rows and persisted URI metadata are cleared before account removal |
| MSG-OUT-010 | Fully cold offline exact-topic recovery | A retained row remains reachable and actionable after force-stop without a catalog; returning online permits verify and warning-gated retry |

### Current M3 outbox/draft acceptance coverage

- On the physical Android 14 Pixel, the Keystore-backed state-store suite passed
  encrypted/account-scoped round-trip and explicit corrupted-ciphertext
  reporting.
- A local draft survived `force-stop` plus a cold exact-topic start; cancelling
  edit restored the pre-existing draft without changing the server message.
- One normal send and one send explicitly retried after a proven offline
  attempt each appeared as exactly one message article in the visible desktop
  client. The Android optimistic row converged to the server timestamp/UUID.
- With Wi-Fi disabled, a send became `UNCERTAIN`, exposed
  verify/retry/hide controls, survived process restart, and did not
  automatically POST again. Online verification found no false confirmation;
  the warning-gated explicit retry then succeeded.
- Starting a call while offline retained the call-link row but did not open
  Jitsi. Online verification found no server message, and confirmed local hide
  removed only the device row.
- A second fault run proved the fully cold path: after offline enqueue and
  `force-stop`, an exact sandbox topic link opened from encrypted route
  metadata without a catalog, retained all outbox controls, and showed no
  catalog-resolution error. Returning online, verification found no false
  server match; warning-gated retry converged to exactly one desktop article.
- Full offline browsing of the currently cached stream/topic catalog and the
  bounded latest server history is now covered by Room. Offline older/newer
  continuation beyond those retained rows and non-conversation projections
  still require persisted cursors/entities.

### Composer preview

The maintained desktop client exposes a real local Workspace Markdown preview.
Android keeps the same outgoing-body contract and uses the production message
renderer instead of a second simplified preview grammar. Switching modes is a
local UI operation: it must never create, update or delete a server resource.

| ID | Scenario | Main assertions |
| --- | --- | --- |
| MSG-COMP-001 | Switch Write → Preview → Write | The exact draft stays unchanged, the keyboard/focus leaves the hidden editor, and returning to Write restores an editable field without a network mutation |
| MSG-COMP-002 | Preview rich Markdown | The production renderer shows the same bounded body syntax used by sent messages, including lists, quotes, code, spoilers, Unicode emoji and canonical mention/reference behavior |
| MSG-COMP-003 | Preview one or several replies | Preview receives the full canonical outgoing body, not only the active answer; every `[author](urn:quote:<uuid>)` section appears in visible tab order |
| MSG-COMP-004 | Empty or attachments-only draft | Empty preview has a localized honest state; selected image/document cards remain visible and removable above the preview, with no fake uploaded attachment URN |
| MSG-COMP-005 | Rotation, background or theme change | Selected mode and exact draft survive Activity recreation; the renderer rebuilds with readable light/dark tokens and no light-on-light text |
| MSG-COMP-006 | Send or save from Preview | The existing send/edit handler receives the unchanged outgoing snapshot once and the UI returns to Write; rejected/ambiguous delivery follows the durable outbox contract |
| MSG-COMP-007 | TalkBack and touch | Write/Preview expose selected tab semantics and 48 dp targets; the preview is a localized polite live region while rendered links and references retain their real actions |
| MSG-COMP-008 | Process death and account/conversation switch | The encrypted draft/reply state restores under its exact owner and slot; the presentation-only mode may restore only with the same saved Activity or defaults to Write on a fresh cold launch, and cannot leak into another conversation |
| MSG-COMP-009 | Apply bold, italic, strikethrough, inline code or spoiler to a selection | The exact selected range is wrapped with the desktop marker and the cursor collapses after the inserted syntax; no text outside the range changes |
| MSG-COMP-010 | Apply an inline action with an empty selection | A paired marker is inserted at the exact cursor and the cursor remains between the pair, ready for immediate typing |
| MSG-COMP-011 | Quote or list one or several lines | Quote/bullet prefixes every selected line; numbered list uses stable one-based indices; an empty selection inserts only the first prefix |
| MSG-COMP-012 | Insert a code block | Selected text is fenced with newlines; an empty selection creates two fences and positions the cursor on the blank body line |
| MSG-COMP-013 | Insert a link | Selected text or the localized fallback becomes the label and only the placeholder `https://` is selected, even when the label itself contains `https://` |
| MSG-COMP-014 | Scroll and invoke the formatting toolbar | All ten localized actions are reachable in one horizontal row, use 48 dp targets, retain selection when tapped, refocus the editor and expose no unsupported AI/schedule/snippet control |
| MSG-COMP-015 | Formatting at the 40,000-character or aggregate multi-reply bound | ViewModel acceptance is authoritative; the visible editor reconciles to the accepted text and clamped selection instead of displaying unsendable overflow |
| MSG-COMP-016 | Format, rotate, switch Preview/Write and change theme | Exact Markdown and selection-safe draft state survive recreation, Preview renders the result, and enabled glyphs remain readable without looking disabled in light or dark mode |
| MSG-COMP-017 | Type `@` at the beginning or after desktop-compatible boundary punctuation | A bounded list of real non-system Workspace users appears above the editor; email-like text, a non-collapsed selection, an inactive cursor and overlong query expose no picker |
| MSG-COMP-018 | Filter mention suggestions | Case-insensitive UUID, username, display-name and email matches retain the desktop priority order, canonical UUID duplicates collapse, malformed/system users are absent and at most eight rows render |
| MSG-COMP-019 | Select a mention in the middle of a draft | Only the active `@query` range becomes `[escaped display name](urn:user:<canonical uuid>) `; text before/after remains exact and the cursor lands after the inserted space |
| MSG-COMP-020 | Preview, send and reopen an inserted mention | Preview resolves the canonical user link to the real profile action; the sent body retains only the UUID URN and ordinary escaped label, and edit restores the exact source Markdown |
| MSG-COMP-021 | Mention picker touch, TalkBack, rotation and theme | Every row has a 56 dp target and localized name/username description; live rows remain readable at 2x font and after light/dark/portrait/landscape recreation without covering Send |
| MSG-COMP-022 | Catalog refresh, owner switch, offline restore and input bound | Suggestions use only the active owner-scoped real catalog; an empty/stale catalog exposes no fake rows, late account data cannot cross owners, and ViewModel-authoritative text/selection reconciliation prevents visible unsendable overflow |

Current automated coverage proves exact plain and ordered multi-reply outgoing
Markdown; every desktop formatting mutation has selection/cursor unit coverage,
including localized and URL-containing link labels plus bounded-state
reconciliation. Mention unit coverage proves trigger boundaries, match priority,
catalog validation/deduplication, maximum results, Markdown escaping and exact
cursor placement. Locale-independent Compose tests cover mode/selected/preview
semantics, horizontal scrolling from the first to the last functional toolbar
action and an enabled localized mention row.

Physical Android 14 acceptance in the dedicated sandbox rendered bold Markdown
and a Unicode emoji, wrapped one selected token as
`**CASSI_FORMAT**`, inserted `[текст ссылки](https://)` through the
horizontally scrolled Link action, and rendered the formatted result in
Preview. A real `@cas` query showed only the active Cassandra row, selection
inserted the canonical UUID link at the cursor, Preview rendered its functional
profile mention, and a sandbox send produced the exact readable message. Edit
restored the original `urn:user` Markdown; cancel preserved the message, then
the test message was explicitly deleted and its absence confirmed. The exact
draft and Preview survived portrait → landscape → portrait; empty state,
light/dark themes and enabled-action readability were visually inspected.

### Message actions

- Edit own message, edit a reply, cancel edit, conflict with remote edit.
- Delete own message with confirmation; cancellation is side-effect free.
- Copy plain text and selected quote without hidden markup.
- Add/remove reaction, repeated tap, another-client reaction, and rollback.
- Single and multi-select forwarding to direct, channel, and topic destinations.
- Reply to whole message and selected text; reorder/remove multiple reply
  contexts; preserve answer drafts.
- Navigate from quote/forward/reference to exact source.

| ID | Scenario | Main assertions |
| --- | --- | --- |
| MSG-ACT-001 | Delete confirmation cancellation | No REST mutation, local/server message and composer context remain unchanged |
| MSG-ACT-002 | Delete owned native message | One canonical UUID DELETE; no optimistic removal; Android/realtime/desktop converge only after success |
| MSG-ACT-003 | Repeated delete input | Per-message single-flight state prevents a duplicate concurrent mutation |
| MSG-ACT-004 | Offline/timeout/server delete failure | Message remains locally and on the server; actionable error clears without losing the row; explicit online retry may succeed |
| MSG-ACT-005 | Delete an edit/quote source | Successful deletion clears only matching edit/quote references and restores any suspended ordinary draft |
| MSG-ACT-006 | External-provider or non-owned message | Edit/delete are hidden until provider capability and preflight data prove the operation is supported |
| MSG-ACT-007 | Copy text from a text/image/file/call message | The exact source Markdown is written once to Android's plain-text clipboard, marked sensitive on supported Android versions, and a polite visible success/failure result with functional Close appears; blank payloads expose no copy action |
| MSG-ACT-008 | Quick or searched emoji reaction | Six labeled quick reactions plus the complete bundled Unicode catalog are selectable from text/image/file/call messages; search accepts glyphs and normalized aliases, an empty result is explicit, server-canonical names render as glyphs, and add/remove immediately reconcile the exact own-reaction/count projection |
| MSG-ACT-009 | Reaction race, malformed response, account switch or failure | Per-owner message/emoji single-flight blocks duplicate mutations; a concurrent authoritative count wins over stale local completion; mismatched UUID/user/message/control data fail closed; late foreign-owner results are discarded and network/server errors remain visible |
| MSG-ACT-010 | Reply to one or multiple whole messages | Reply replaces only the active quote and preserves its answer/identity; Add reply creates a new selected tab; canonical `[author](urn:quote:<uuid>)` sections are sent in visible tab order |
| MSG-ACT-011 | Reply to selected rendered text | Selection UI exposes readable rendered text rather than Markdown destinations; an empty/blank range cannot be confirmed; exact whitespace is percent-encoded into the quote URN |
| MSG-ACT-012 | Reply-tab editing | Selecting tabs restores each isolated answer; reorder arrows appear only when a move is possible; remove chooses the adjacent tab; clear-all restores the suspended ordinary draft |
| MSG-ACT-013 | Multi-reply recreation and process death | Ordered tabs, selected fragments, active tab, isolated answers, ordinary draft and attachments restore from the exact encrypted account/conversation/draft slot |
| MSG-ACT-014 | Send or edit a structured multi-reply | Send clears only the transmitted reply session and attachments while preserving an ordinary draft; editing a canonical reply restores structural tabs, fetches uncached accessible sources once, and falls back to raw Markdown only when a source is unavailable |
| MSG-ACT-015 | Multi-reply concurrent input and size limits | Input made after an in-flight snapshot is retained; aggregate answers/selections and bounded source previews stay below encrypted-state limits; oversize content is rejected visibly before mutation |
| MSG-ACT-016 | Multi-reply offline, timeout, restart and account switch | Durable outbox rules match ordinary messages: rejected results remain retryable, ambiguous results require verification, no automatic duplicate POST occurs, and late results cannot mutate another owner |

Current MSG-ACT-007 coverage: an exact two-case foreground clipboard
instrumentation run passed on the Android 14 Pixel, including exact text,
bounded label, blank rejection and the sensitive-content flag. In the
dedicated sandbox, long-pressing the rich metadata message exposed the
functional copy action; pasting into the composer reproduced the exact
unrendered Markdown (emoji shortcode, display mention, canonical UUID mention
and inline code), the system clipboard preview hid sensitive content, and the
visible success card's Close action worked. The temporary composer text and
clipboard were cleared without sending a message. The final build also
auto-dismissed the success card after the accessibility-adjusted timeout.
Its success text passed light/dark contrast checks (5.69:1 and 6.80:1).
Image/file/call menu
sampling and an injected missing-clipboard-service failure remain pending.

Current MSG-ACT-008/009 automated coverage includes deduplicated catalog
construction, alias/glyph search, Unicode/server-name display, stale-count
reconciliation, overflow/invalid-input rejection, strict response UUID/owner
validation, and a production-catalog Compose run through empty search and
selection. Repository tests additionally cover realtime create/update/delete
count projection and prove that the server echo of an optimistic own mutation
confirms rather than doubles the count.

Physical-device acceptance passed in the dedicated sandbox for both the quick
thumbs-up path and a searched `test_tube` selection: add/remove appeared
immediately, the canonical server shortcode always rendered as its Unicode
glyph with count `1`, the own chip exposed selected semantics, delayed server
confirmation preserved the projection, and reopening the topic after removal
showed no residual reaction. The complete picker, its search query and result
survived portrait → landscape → portrait and remained usable. Its light and
dark surfaces were inspected without light-on-light text. The sandbox was left
clean.

### Forwarding

Android follows the maintained desktop contract: forwarding sends a canonical
`urn:quote` reference through the ordinary message-create endpoint. It does not
copy mutable source text into a new message and does not invent a forwarding
endpoint. All state-changing acceptance runs use the dedicated sandbox stream
and never the active task-reporting topic.

| ID | Scenario | Main assertions |
| --- | --- | --- |
| MSG-FWD-001 | Long-press text/image/file/call message | One functional `Переслать` action appears only for a canonical server message; local outbox rows expose no false action |
| MSG-FWD-002 | Forward to public/private channel topic | Archived streams and direct streams are absent from the channel list; default topic sorts first; one exact `[author](urn:quote:<uuid>)` message appears in the chosen destination |
| MSG-FWD-003 | Forward to existing direct chat | Current user is absent; the existing private stream/default topic is reused and no new stream is created |
| MSG-FWD-004 | Forward to a user without a direct chat | A private stream is created once, its authoritative default topic is resolved, then the forward is sent |
| MSG-FWD-005 | Direct creation accepted but follow-up topic/catalog load fails | Retry refreshes and reuses the partially created direct stream before any create POST; no duplicate direct chat is possible |
| MSG-FWD-006 | Author contains Markdown punctuation or source is later edited | Escaping matches desktop, reference UUID remains canonical, and rendered content resolves from the current authoritative source |
| MSG-FWD-007 | Double tap / rotation / background during send | ViewModel-owned single-flight work issues one send; dialog state survives Activity recreation and cannot be dismissed or retargeted while delivery is unresolved |
| MSG-FWD-008 | Timeout/network/5xx/malformed or wrong-chat success response | No blind retry; Android queries the exact target, excludes pre-attempt identical UUIDs, and confirms only one unique new exact match |
| MSG-FWD-009 | Zero or multiple verification matches | Zero exposes check-again plus explicit duplicate-risk confirmation; multiple disables retry and remains visibly ambiguous |
| MSG-FWD-010 | Open forwarded source in same or another chat | Same-chat source is focused after bounded fetch; cross-chat source resolves stream/topic metadata and opens the exact UUID |
| MSG-FWD-011 | Deleted/forbidden/offline/malformed quote source | Block shows an unavailable state and functional retry; unsafe or malformed URNs never launch an external intent |
| MSG-FWD-012 | Account switches during catalog/create/send/verify/open | Stale completion cannot navigate, claim success, or mutate the new account projection; the user receives an actionable error |
| MSG-FWD-013 | Select several canonical messages | Long-press `Select message` enters selection mode; row checkboxes toggle independently and the localized count follows insertion order up to 32 unique sources |
| MSG-FWD-014 | Rotate/background while selecting | ViewModel-owned selection and its exact order survive Activity recreation; Cancel clears it without a request or draft mutation |
| MSG-FWD-015 | Forward an ordered selection | One ordinary message contains one canonical quote reference per source in selection order; duplicate or missing sources fail closed before POST |
| MSG-FWD-016 | Multi-forward confirmation or uncertain result | Confirmed delivery clears selection; timeout, malformed response, zero/multiple verification matches, dismissal and retry retain it until the user can safely resolve the outcome |
| MSG-FWD-017 | Selection actions have complete behavior | Forward and Cancel meet the 48 dp target and work with touch/TalkBack; the desktop no-op bulk Delete action is not rendered on Android |
| MSG-FWD-018 | Source changes during selection | Deleted, trimmed, local-outbox, malformed or account-foreign sources cannot be submitted; remaining valid selection is reconciled and an actionable error is shown |

### Current forwarding acceptance coverage

- Unit coverage passes for desktop-compatible escaping, Unicode selected-text
  URN encode/decode, strict malformed-query rejection, fenced-code isolation,
  stream/topic/user filtering, existing-direct resolution, exact response
  validation, unique post-attempt verification, ordered multi-source Markdown,
  duplicate/invalid-source rejection, the 32-source bound, and bounded source
  snapshots.
- The retained ViewModel owns catalog/topic loading, direct-chat recovery,
  preflight UUID baseline, send, and bounded verification. Target controls lock
  once delivery becomes uncertain, and retry requires an explicit duplicate
  warning.
- On the physical Android 14 phone, two different source messages were
  forwarded through the real backend into the dedicated sandbox topic. Each
  produced exactly one new desktop article; Android and visible desktop both
  rendered the authoritative author/current source text.
- The picker retained the exact duplicate-name topic UUID across
  portrait/landscape/portrait recreation and returned the selected row to the
  visible area. Tapping the second forwarded block moved Android from the end
  of the conversation to its distant source; visible desktop opened the exact
  source message URL.
- Physical Android 14 acceptance also selected two existing sandbox messages,
  retained their count and order across portrait/landscape/portrait recreation,
  sent one two-reference message, received authoritative server confirmation,
  cleared selection only after confirmation, and rendered both navigable quote
  blocks in exact selection order. The test-created result was then deleted
  through the ordinary confirmed message action, leaving the sandbox clean.
- Exact Compose instrumentation verifies that the localized selection bar has
  functional Forward and Cancel actions and exposes no dead bulk Delete
  control.
- A fully offline preflight kept the selected target and source, showed a
  recoverable `Network unavailable` error, exposed neither uncertain-delivery
  retry action, and created no message. Wi-Fi, data, and automatic rotation
  were restored after the run.
- The deterministic post-request fault matrix passes for timeout, network,
  server, malformed-response, unknown-exception, and wrong-chat success paths.
  Every ambiguous result enters verification, excludes preflight UUIDs, accepts
  exactly one new exact match, rejects multiple matches, and never becomes an
  ordinary blind retry; validation rejection remains safely retryable.
- Physical multi-selection fault injection after POST, post-request
  timeout/connection-cut and malformed/wrong-success injection, physical
  cross-topic navigation, and direct-chat creation/recovery remain required
  before this slice is marked fully accepted.

### Current message-action acceptance coverage

- On the physical Android 14 Pixel in the dedicated sandbox topic, delete
  confirmation was opened from a long press and cancellation preserved the
  exact server article.
- A confirmed native delete removed the row only after a successful response;
  the visible desktop client showed zero matching articles. A cold exact-topic
  restart reloaded the conversation without the deleted marker.
- With Wi-Fi disabled before confirmation, the row remained on Android and the
  visible desktop client retained exactly one article while Android exposed
  `Network unavailable`. After Wi-Fi validation returned, an explicit retry
  removed the row on both clients.
- Rapid duplicate confirmation input produced no duplicate visible mutation,
  and no crash or ANR was observed.
- Unit coverage verifies the canonical UUID request path, own/native action
  gating, malformed/local-ID rejection, realtime deletion, and stale
  stream/topic-preview clearing.

### Files and media

- Select image, video, audio, PDF, document, and unknown MIME file.
- Camera capture.
- Valid/invalid extension, MIME mismatch, zero bytes, maximum size, Unicode and
  path-like filenames.
- Upload progress, cancellation, timeout, retry, app background, process death,
  and token refresh.
- Preview supported media; download unsupported media through a safe URI.
- Expired/forbidden/deleted file and partial download.
- Zoom/pan large images without OOM; release decoded resources after closing.

### Android incoming share

`ACTION_SEND` and `ACTION_SEND_MULTIPLE` are accepted only as an explicit
draft-ingress flow. The app never posts on receipt: the user must select an
existing chat and exact topic, inspect the hydrated composer, and press the
ordinary Send control. Stateful cross-client checks use only the dedicated
sandbox stream/topic; the task-reporting topic is never selected or archived.

| ID | Scenario | Acceptance oracle |
| --- | --- | --- |
| SHARE-001 | Share plain text or subject + text | CRLF/NUL normalization is bounded to 40,000 characters; the chooser previews the content and no server send occurs |
| SHARE-002 | Share one or multiple files | Only distinct `content://` grants are accepted; each file is copied into the exact active account's private attachment directory before the external grant can expire |
| SHARE-003 | Choose existing channel/topic | Archived chats are absent; a channel never preselects a topic, duplicate names show a UUID suffix, and only the explicitly chosen exact stream/topic UUIDs are persisted before the composer opens |
| SHARE-004 | Choose existing direct chat | The authoritative default topic is used and the composer opens without exposing a meaningless topic choice |
| SHARE-005 | Cancel destination chooser | No encrypted draft, cache copy or server mutation is created |
| SHARE-006 | Existing ordinary draft | Shared text is appended with a visible separator; attachments merge by URI; original quote/outbox/server-draft metadata stays intact |
| SHARE-007 | Existing edit in progress | The edited replacement remains untouched; shared content is merged into the suspended ordinary draft and returns after edit exit |
| SHARE-008 | Existing 40,000-character or ten-file draft | Merge rejects atomically with a visible limit error; neither partial text nor partial attachments are saved |
| SHARE-009 | Zero-byte, over-25-MiB, unknown-size or lying provider | Streaming copy enforces the measured bound, removes partial files on known failure and gives a recoverable error |
| SHARE-010 | `file://`, HTTP URI, missing grant or throwing provider | Input fails closed; no path is exposed and no draft is mutated |
| SHARE-011 | Account switch during copy/commit | Owner is checked before copy and the read/merge/write transaction holds the session owner fence; another account never receives the content |
| SHARE-012 | Rotation, background, process death before or immediately after commit | The activity saves a stable request UUID, the encrypted conversation stores the last applied UUID, concurrent/replayed confirmation is idempotent, and the original intent remains available until explicit consume |
| SHARE-013 | Cancellation or process death during file/store write | Temporary copy is not exposed as an attachment; once encrypted storage may have committed, referenced files are retained rather than deleted ambiguously |
| SHARE-014 | Offline destination and composer | Cached catalog remains selectable where available; draft hydration works without auto-send and later uses the normal draft/outbox recovery paths |

Current coverage: parser/merge/catalog/source-contract unit tests and four
instrumented parser cases pass. On the physical Android 14 phone, plain text
and a real Files-provider document reached the exact UUID-disambiguated
sandbox topic without auto-send; Cancel was side-effect free, portrait /
landscape / portrait retained the chooser, a missing grant failed visibly, and
both text and file drafts survived a cold start. Removing the incoming file
persisted the empty encrypted draft before deleting its private cached copy;
another cold start proved that neither the attachment nor an orphan
`incoming-*` file remained. The same durable-state-before-file-delete rule is
shared by composer removal, successful enqueue and deletion from the Drafts
screen. Physical multi-file, measured oversize/lying
provider, owner-switch, process-death-during-copy, offline catalog and desktop
cross-client checks remain required before the slice is fully accepted.

## Draft scenarios

The maintained desktop draft UUID and the backend revision/ETag contract are
the cross-client source of truth. Test data must use the dedicated sandbox
stream/topic; the task-reporting topic is never mutated or archived.

| ID | Scenario | Acceptance oracle |
| --- | --- | --- |
| DRAFT-001 | First nonblank composer change | One stable client UUID is persisted before POST; wire JSON contains explicit `kind=markdown`; validated response UUID/route/owner/revision and strong ETag become the local baseline |
| DRAFT-002 | Further edit after save | PUT targets only that UUID with exact `If-Match`; successful content/revision/ETag converge on Android and desktop |
| DRAFT-003 | Clear or send the draft | A local deletion tombstone is durable before DELETE; `404` is success; only the exact draft is removed and text typed after an in-flight send remains |
| DRAFT-004 | Two server drafts in one conversation | Both list as distinct UUIDs, open their exact independent composers, retain their own text/revision through Back/refresh/recreation, and never overwrite each other |
| DRAFT-005 | Default local draft plus selected server draft | Legacy/base composer state remains readable; selected server draft uses a separate encrypted slot; switching between them preserves both |
| DRAFT-006 | Shared outbox across draft slots | Failed/uncertain messages remain in one conversation-scoped outbox, never duplicate or resurrect when another draft is opened, and reconcile once |
| DRAFT-007 | Paginated Drafts list | Every page is descending by `updated_at,uuid`; canonical marker equals the last row; duplicate, repeated, unrelated, malformed, or overlong pagination fails closed while prior rows remain |
| DRAFT-008 | Open a desktop-created server-only draft | Android stores it in its exact UUID slot before navigation and renders the authoritative text; another local draft in the conversation is untouched |
| DRAFT-009 | Delete from Drafts | Exact UUID and current ETag are used; row is single-flight; success/`404` clears only its slot; failure retains an actionable tombstone |
| DRAFT-010 | `412` conflict | Body UUID/project/user/stream/topic plus response ETag/revision are strictly validated; UI offers working Use server, Keep mine, and Delete actions with no dead control |
| DRAFT-011 | `404` during PUT | Local content is retained, a new UUID is generated, and one safe create is attempted; no loop or stale ETag reuse |
| DRAFT-012 | Timeout/network/429/5xx/malformed response | State becomes visible and retryable; bounded automatic retry has no request storm; a create that committed before a failed response is reconciled by UUID/content before any retry |
| DRAFT-013 | Process death during SAVING/DELETING | Restore converts forever-busy state into actionable FAILED state, preserves the create payload or delete tombstone, and completes without resurrection |
| DRAFT-014 | Offline edit and reconnect | Encrypted text remains usable offline; reconnect converges through one owner-fenced sync without blocking navigation |
| DRAFT-015 | Account switch/logout | Every list/mutation result is owner-bound; another account cannot read, overwrite, delete, or inherit slots; logout clears the exact account index and ciphertext |
| DRAFT-016 | Encryption and migration | Plaintext route/text/outbox is absent from preferences and backup; old slotless state remains readable; authenticated data prevents swapping ciphertext between account/route/slot keys |
| DRAFT-017 | Edit/quote/attachment suspension | Enter/cancel/complete edit restores the prior full draft; missing referenced messages recover all user content into a normal actionable draft |
| DRAFT-018 | Accessibility and adaptive layout | Back/refresh/open/delete/retry/conflict controls are labelled, reachable at 200% font, have adequate touch targets/contrast, and never overlap or truncate the only recovery path |
| DRAFT-019 | Long-running cross-client convergence | Repeated Android/desktop create/edit/delete, foreground/background, Wi-Fi/data transitions, and periodic outages produce no missing/duplicate draft, unbounded index, ANR, or request/retry loop |

Current verified coverage: request/response validation, wire-default encoding,
state transitions, interrupted mutation recovery, encrypted index/state
isolation, exact multi-slot merge, conversation-scoped outbox planning, Android
build, and one physical Android create/delete plus desktop-to-Android list/delete
convergence pass. DRAFT-004 physical exact-open/edit retention, DRAFT-010
cross-client conflict, controlled post-request faults, large-font/TalkBack, and
the soak profile remain required and are not claimed.

## Realtime and failure-injection scenarios

| ID | Failure | Expected recovery |
| --- | --- | --- |
| RT-001 | Websocket closes normally | Bounded backoff, REST catch-up, one new socket |
| RT-002 | Websocket error loop | No tight loop; visible connection state; eventual recovery |
| RT-003 | Network lost while active | Offline state, cached read access, safe queued mutations |
| RT-004 | Network changes Wi-Fi ↔ cellular | Catch-up from last durable cursor, no duplicate notification |
| RT-005 | App backgrounded for 1/10/60 minutes | Background policy respected; foreground catch-up completes |
| RT-006 | Doze/app standby | Push or foreground recovery restores state |
| RT-007 | Server 429 | Retry-after respected; no request storm |
| RT-008 | Server 500/502/503 | Safe operations retry with jitter; mutations avoid blind duplication |
| RT-009 | Slow body / timeout | Operation cancels cleanly and exposes retry |
| RT-010 | Malformed/truncated JSON | Error isolated; cache/cursor not corrupted |
| RT-011 | Duplicate/out-of-order event | Idempotent dispatcher produces one final entity state |
| RT-012 | Cursor expired | Reset projections, reload snapshots, preserve domain data/outbox |
| RT-013 | Process killed during catch-up | Durable cursor is never advanced past unapplied events |
| RT-014 | Backend permission changes | Cached forbidden actions disappear after reconciliation |

Current verified coverage: the retry decision matrix proves that failed or
short-lived sockets back off from 1 to at most 30 seconds and that the delay
resets only after a ready connection remains stable for 60 seconds. Repository
tests prove duplicate event suppression and exact cursor advancement, plus
`4410` recovery that clears the expired cursor and server-derived projections,
retains `local-*` outbox rows, increments one recovery generation, and leaves
ordinary closes untouched. The retained runtime observes process
foreground/background lifecycle (so configuration changes do not create socket
churn); the catalog and an open conversation observe the recovery generation
and refetch authoritative snapshots. The generation/version cursor is now
Keystore-encrypted, account-scoped, removed with account-local logout data, and
restored before reconnect. Foreground recovery drains strict-generation REST
pages before the websocket; each cursor is persisted only after its event was
successfully applied, duplicate/non-advancing pages fail closed, pagination is
bounded, and REST `410` follows the same snapshot-recovery path as socket
`4410`. Unit/contract tests cover isolation, selective cleanup, validation,
ordering and exact query fields; an Android Keystore test verifies encrypted
round-trip and cross-account separation. The Ktor client now sends protocol
pings every 20 seconds; its 40-second pong deadline turns a half-open socket
into the normal reconnect/catch-up path, and a 2 MiB frame ceiling prevents an
unbounded single-frame allocation. On the physical Android 14 Pixel, removing
active network connectivity kept the same process alive and produced catch-up
retries at approximately 2/4/8/16/30-second intervals; restoring connectivity
produced exactly one new connection after the active backoff. RT-001/002/005
still require controlled socket/request-count device automation, while
RT-004/012/013 still require process-kill and injected real
`410`/`4410`/packet-blackhole cross-client convergence passes. The durable
catalog/message snapshot is independent from the cursor store and cannot make
an unapplied cursor durable.

## Cold-offline snapshot scenarios

The Room snapshot is a read cache, not a second outbox or mutation queue. It is
hydrated before realtime startup, merges below newer REST/realtime/local state,
and is replaced only while the same credential owner remains active. Pending
send and read intents live in the separate encrypted owner/conversation state;
they are never inferred from, duplicated into or automatically replayed from
the Room snapshot.

| ID | Scenario | Main assertions | Current coverage |
| --- | --- | --- | --- |
| OFFLINE-001 | Online sync followed by force-stop and radio-off cold launch | Cached stream/topic/folder catalog, users and memberships appear with an explicit stale banner before any network success; no login flash, crash or blank primary screen | Physical Android 14 Pixel passed; schema-v2 database contained 15 streams, 3 folders, 42 users and 124 bindings before the cold launch |
| OFFLINE-002 | Cold exact cached-topic `ew://` deep link | The exact saved account/stream/topic resolves from Room and opens bounded server history without waiting for catalog REST success | Physical Android 14 Pixel passed; 1.411 s exact sandbox route |
| OFFLINE-003 | Offline general catalog navigation | Only retained real streams/topics/previews/unread data render; no fabricated destination or enabled inert control appears | Physical cached catalog and exact topic passed; broad row sampling pending |
| OFFLINE-004 | Restore Wi-Fi/mobile data while cached chat is open | One catch-up/realtime recovery converges in place, hides the stale banner and retains the exact route without duplicate rows | Physical recovery passed; controlled request-count oracle pending |
| OFFLINE-005 | Newer REST/realtime/local row races cached hydration | Current/newer state wins by UUID and timestamps; an authoritative empty folder/user/binding snapshot cannot be repopulated by a delayed disk read; local `local-*` outbox rows survive and are never written into Room | Repository/unit coverage, including the empty-snapshot race; delayed device injection pending |
| OFFLINE-006 | Switch accounts during read or debounced write | Completion is owner-fenced; old rows never flash or write under the new owner | Source/repository coverage; delayed-I/O two-account instrumentation pending |
| OFFLINE-007 | Logout one account | Only that owner's stream/topic/message/folder/user/binding and Feed/Starred rows are deleted before account removal; sibling account rows remain decryptable | Store instrumentation covers selective clear across the complete schema; two-account UI E2E pending |
| OFFLINE-008 | Corrupt, moved, replayed, oversized or cross-scope row | AES-GCM associated-data validation and bounds reject only invalid rows; invalid folder relationships, membership enums and cross-stream bindings cannot enter the projection; a damaged timeline continues from its oldest survivor; unrelated valid rows remain available | Exact instrumentation covers ciphertext replay, invalid/local/catalog exclusion, timeline corruption and bounds; raw-DB device injection pending |
| OFFLINE-009 | Cache bounds under a large account | At most 1,000 streams, 10,000 topics, 500 folders with 1,000 items each, 10,000 users, 50,000 bindings, 100 conversations, 100 messages/conversation, 5,000 messages/account and 500 messages per Feed/Starred timeline persist; payload-specific byte caps and deterministic newest-message retention apply | Source constants and store instrumentation; generated maximum-volume performance run pending |
| OFFLINE-010 | Rotation/background while fully offline | Route, catalog/history and one global stale surface survive recreation; no request, mutation or duplicate banner is created | Physical portrait/landscape/portrait passed; long background pending |
| OFFLINE-011 | Cache database/keyset backup or device transfer | Room database and keyset are excluded; undecryptable transplanted data cannot render | Backup-rule source gate; emulator backup/restore acceptance pending |
| OFFLINE-012 | Keystore/Room read or write failure | App remains usable online, logs no payload/credential, skips persistence for that runtime and never overwrites another owner | Fail-closed source path; injected Keystore/SQLite faults pending |
| OFFLINE-013 | Upgrade an installed schema-v1 cache to schema v2 | Existing encrypted streams/topics/messages survive; empty folder/user/binding tables are added with exact indices; startup requires no destructive fallback | Exact migration instrumentation and physical in-place Pixel upgrade passed |
| OFFLINE-014 | Upgrade schema v2 to v3 or directly v1 to v3 | Existing catalog/history/member rows survive; empty Feed/Starred tables and exact owner/kind/position index are created without destructive fallback | Exact sequential migration instrumentation defined; physical v2→v3 in-place acceptance pending |
| OFFLINE-015 | Authoritative empty timeline versus missing cache | An empty successful Feed/Starred response is encrypted as metadata with zero rows and restores as content-ready empty; an account/kind never cached returns no projection and still attempts REST | Store instrumentation and physical offline empty Starred acceptance passed |
| OFFLINE-016 | Authoritative Inbox marker versus missing cache | A successful catalog write is followed by an encrypted owner-scoped zero-row Inbox marker; cached rows or confirmed empty remain usable on a cold failed refresh, while a missing/corrupt marker never fabricates an empty Inbox | Store instrumentation and repository/UI-model coverage; physical non-empty cold-offline restore/Retry passed with one marker and zero duplicate message rows; physical authoritative-empty injection pending |
| OFFLINE-017 | Upgrade schema v3 to v4 and restore conversation pagination | Existing catalog/history/timeline rows survive; the new exact-owner stream/topic pagination table starts empty. Subsequent snapshots atomically retain at most 100 encrypted page-state rows matching retained conversations; cross-owner/replayed/corrupt/incomplete metadata cannot hide a missing history side | Sequential migration, normalization unit and exact store instrumentation passed on an Android 14 Pixel; cold-offline latest continuation/Retry passed, while context and injected-corruption device paths remain pending |
| OFFLINE-018 | Process stops around `read_up_to` or its local persistence | The canonical newest boundary is committed to separate encrypted account/conversation state before network mutation; process restart exposes a user decision instead of replaying it. A failed local write blocks POST, exact server/realtime confirmation clears the intent, Close discards it durably, and draft lifecycle operations preserve the read-only state | Focused state/repository/unit coverage plus exact encrypted-store instrumentation passed on an Android 14 Pixel; physical process-kill, storage-fault and cross-client confirmation pending |

Current verified coverage: the exact owner-scoped instrumentation class passes
encrypted round-trip, account separation, ciphertext replay rejection,
invalid/local/catalog-row exclusion, bounds and selective clear. The same
class now covers independent encrypted Feed/Starred timelines, authoritative
empty state, 500-row retention, damaged-row isolation and cross-owner/kind
replay rejection. It also covers the zero-row Inbox success marker without
storing duplicate message rows. Schema-v4 coverage adds encrypted
conversation mode/anchor/marker round-trip and degrades a damaged message
window to conservative bidirectional continuation. The same physical store
suite now verifies that the composite pending-read boundary round-trips under
the exact account/conversation key and is absent from plaintext preferences.
Exact migrations preserve schema-v1 history,
add the three schema-v2 catalog tables and then add the schema-v3 timeline
tables plus the schema-v4 pagination table. Repository
tests prove cache-under-current merging, an
authoritative-empty race fence, and realtime binding add/delete projection. On
the physical Pixel, an installed schema-v1 cache upgraded in place, an online
snapshot populated 3 encrypted folders, 42 users and 124 bindings, both radios
were disabled, the process was force-stopped, and the general catalog reopened
cold. A later schema-v3 run persisted 50 encrypted Feed rows plus a distinct
zero-row Starred snapshot; both restored after a no-network force-stop, and
the empty Starred screen retained its recoverable Retry rather than becoming
an uncached blocking error. The schema-v4 run then persisted one encrypted
conversation-pagination row, restored its latest-window continuation after a
radio-off force-stop, and converged in place after Wi-Fi recovery. No stateful
Workspace mutation was performed.
Message text and the yellow
stale banner have readable dark-theme contrast; the banner's **Retry now** is a
real action, while an uncached quote source retains its own truthful,
functional Retry.

Use Android network controls and a controllable backend proxy for latency,
disconnect, status-code, body-corruption, and reorder cases. Do not rely on
visual observation alone; assert request counts, cursor values, entity counts,
and final server state.

## Push and notification scenarios

| ID | Scenario | Expected result | Current coverage |
| --- | --- | --- | --- |
| PUSH-001 | Android 13+ permission accepted, denied, denied permanently, then granted in Settings | Messenger stays usable; Open settings and Not now are real actions; delivery starts only after grant | UI/source covered; physical permission reset matrix pending |
| PUSH-002 | First login and repeated app start for account A | One stable A registration UUID and one Keystore-wrapped HPKE public identity are PUT with the exact backend algorithm; repeats replace instead of duplicate | Unit contract + exact identity instrumentation + authenticated physical remote PUT/delete passed |
| PUSH-003 | Save/switch to account B | B receives a distinct stable registration UUID; A remains registered; neither owner can update/delete the other's UUID | Unit owner/UUID fence; two-account backend/FCM delivery pending |
| PUSH-004 | Account changes while token/key preparation or PUT is in flight | Old-owner work is cancelled/fenced before a remote mutation; the new owner alone can finish | Unit verifier/source; delayed backend injection pending |
| PUSH-005 | FCM token rotates while A or B is active | Only the exact active credential owner is updated; selecting another retained account immediately refreshes that account with the current token; no stale-owner retry lands | Unit/source; real FCM rotation and inactive-account delivery window pending |
| PUSH-006 | Upgrade from the former installation-global registration UUID | Each active owner attempts an idempotent owned DELETE for the legacy UUID before using its new scoped UUID; a different owner cannot delete it | Unit legacy cleanup; upgrade-on-device/backend pending |
| PUSH-007 | Logout succeeds online | Exact owner's registration DELETE succeeds before local account/cache removal; missing registration is idempotent success; sibling registrations and data remain | Unit/source + physical idempotent double-DELETE passed; two-account logout pending |
| PUSH-008 | Logout DELETE times out/fails | Account and credentials remain, error is readable, controls unlock, Retry repeats the same owner UUID; no orphaned push target is silently accepted | Source; injected network/5xx pending |
| PUSH-009 | Push for a uniquely saved inactive realm | Notification uses that owner's sound, a realm-scoped ID/group and private public version; tap switches to the unique account before resolving the provider target | Unit routing/sound/source; real inactive-account FCM pending |
| PUSH-010 | Two saved accounts share the same realm URL | Tap presents a functional account chooser; no provider ID is resolved until the user explicitly selects a matching account | Pure selector unit + UI source; physical chooser pending |
| PUSH-011 | Missing, HTTP, credential-bearing or malformed `realm_url`, invalid kind/IDs/topic | Payload fails closed without notification or navigation; no active account is guessed | Parser unit |
| PUSH-012 | Same message ID from two realms, multiple messages in one conversation, remove event | Realm-scoped IDs do not overwrite cross-realm rows; conversation group keys are stable; remove cancels only the realm/message pair | Unit/source; system tray inspection pending |
| PUSH-013 | Rotate/recreate while an unopened notification target or account chooser is pending | Content-free realm/chat/message identifiers restore once; no duplicate route or destructive action is replayed | Saved-state source; physical rotation/process death pending |
| PUSH-014 | Foreground, background, force-stopped and rebooted delivery under Default/Subtle/Digital/Glass/Pulse/None | Exactly one notification uses the resolved owner's channel; None remains silent; tap remains exact | Channels tested separately; real FCM matrix pending |
| PUSH-015 | Lock screen is public/private and message contains long or styled text | Public version says only Workspace/New message; private body is bounded; notification extras contain no author/message content | Source; physical lock-screen inspection pending |
| PUSH-016 | Encrypted payload valid, wrong key, corrupt ciphertext, replay or unsupported version | Valid envelope decrypts only with its registered key; every invalid envelope fails closed without plaintext fallback | BLOCKED: backend/desktop explicitly do not yet define/consume the delivery envelope; do not invent a mobile-only contract |

Current physical-device gate: the ignored local `google-services.json` is a
non-production build stub and cannot obtain an FCM token. Real delivery,
rotation, process-state, reboot, channel and lock-screen acceptance therefore
remain `BLOCKED`, not passed. Resume them only with a valid brand-matched
Firebase configuration supplied through the team's secret build channel; never
commit that file or its credentials.

## Calls

- Start from header/profile/message link in direct and channel/topic context.
- Accept/decline incoming call in foreground/background.
- Microphone/camera permission accept/deny/revoke.
- Network switch, Bluetooth/wired route change, phone-call interruption.
- A second different call is blocked or prompts explicitly.
- Resume after background and clean all call resources on hang-up.
- Invalid/untrusted meeting URL is rejected.

## Profile and settings

- View self and other user with missing/partial/full profile fields.
- Shared-channel list uses real bindings only.
- Edit name, status, avatar, and timezone with validation and rollback.
- Camera/gallery denied and media removed while picker is open.
- Theme and locale persist across restart and account switch as specified.
- Russian/English plurals and long labels fit all maintained layouts.
- Notification sound preview and mute.
- Chat sorting and folder layout update catalog predictably.
- Idle timeout during foreground, background, active call, and unsent draft.
- Clear cache does not remove credentials and rebuilds state.
- Diagnostics export is redacted; version/licenses are available offline.

### Other-user profile matrix

| ID | Scenario | Expected result | Current coverage |
| --- | --- | --- | --- |
| UPROF-001 | Open a user from a message/mention | One canonical target UUID resolves to authoritative name, avatar, presence, status and contacts; no unrelated user can render | Canonical matcher unit + physical exact user passed |
| UPROF-002 | Profile data is not loaded yet or presence is missing/unknown | Loading is explicit; fallback route fields are bounded; no fabricated `Не в сети`, phone, manager, birthday or local time appears | Presence unit + physical loading path passed |
| UPROF-003 | Refresh online, then refresh offline with existing content | Requests are single-flight and owner-bound; offline error is visible while the last real profile remains usable; Retry converges after recovery | Physical offline/stale/Retry passed |
| UPROF-004 | Binding list contains channels, another user's channels, native/provider DMs or a legacy private provider row without classification metadata | Only exact selected-user, positively classified non-DM channel bindings render in stable catalog order; ambiguous legacy private rows fail closed | Unit + physical native/legacy DM exclusion with real channels retained passed |
| UPROF-005 | Initial bindings load, successful empty list and recoverable failure | Spinner, `Общих каналов нет`, unavailable+Retry and real content are distinct; failure never masquerades as empty | Source/unit + physical content/failure passed; empty fixture pending |
| UPROF-006 | Open a shared-channel card with missing/cached/default topic metadata | Exact channel UUID opens its real topic list; no guessed default topic or fabricated destination is used | Navigation source contract + physical sandbox channel/topic-list route passed |
| UPROF-007 | Target already has one native personal stream | Button opens that exact stream/topic without creating another chat | Candidate unit + physical existing-DM reuse passed |
| UPROF-008 | No local stream, but authoritative catalog already contains the DM or catalog preflight fails | One successful preflight refresh finds and opens it; a failed preflight aborts visibly and sends no create request | Resolver/source covered; request-count/fault integration pending |
| UPROF-009 | No DM exists and create succeeds | Exactly one POST is sent; private stream, target UUID and stream UUID are validated; exact default topic opens | Contract/source covered; real create intentionally not run |
| UPROF-010 | Create times out/returns 5xx/malformed after server commit | One authoritative catalog reconciliation opens exactly one committed DM; retry never blindly creates before preflight | Source + injected backend pending |
| UPROF-011 | Duplicate matching DMs, foreign target, invalid stream UUID or ambiguous default topics | Navigation and creation fail closed with a readable recovery path | Candidate/topic unit covered |
| UPROF-012 | Existing DM while fully offline versus missing DM while offline | Existing local exact route opens; missing route produces a network error and no fake success | Architecture/source; physical matrix pending |
| UPROF-013 | Account switches/removes while profile or DM request is in flight | Old-owner response cannot replace profile/catalog, emit navigation or create state in the new owner | Owner fence/source; two-account fault test pending |
| UPROF-014 | Rapid refresh/open-chat taps | At most one refresh and one DM operation are active; controls disable synchronously and never leave a stuck spinner | Source + stress pending |
| UPROF-015 | Rotate/background/process death on Profile and Channels tabs or while opening DM | Selected tab, last real data and route remain safe; no implicit retry/create or duplicate navigation occurs | Physical portrait/landscape/portrait with selected Channels tab passed; background/process-death pending |
| UPROF-016 | TalkBack and large fonts traverse back, refresh, tabs, Retry, DM and channel rows | Every action has a readable state and at least a 48 dp target; light/dark contrast and long labels remain usable | Physical semantic tree confirms bounded Back plus 48 dp refresh/close/tab/action targets; spoken/font matrix pending |
| UPROF-017 | Own/external profile and unsupported call/search/notification shortcuts | Self-DM and external-identity DM are hidden/rejected to match desktop; only the maintained internal-user DM action is exposed; unsupported shortcuts remain absent | Internal/external/self availability unit + physical internal profile passed |
| UPROF-018 | Tap a real profile avatar, zoom, rotate and close | Only a valid HTTP(S) avatar opens the authenticated fullscreen viewer; initials and invalid/local schemes expose no inert preview action; Back/Close returns to the same profile | URL-policy unit/source + physical authenticated preview, portrait/landscape restoration and fixed 48 dp Close passed |
| UPROF-019 | Copy displayed name, email and user ID | Each available action writes the exact visible plain text once, announces success/failure and dismisses feedback; blank name and an unverified route ID expose no dead copy control | Field/identity unit + exact foreground clipboard instrumented test + physical three-action feedback/48 dp semantics passed |
| UPROF-020 | View an external identity with absent, normal, padded or overlong provider metadata | A bounded explicit external badge renders, provider text cannot break layout, and the unsupported native-DM action remains absent | Label-boundary unit covered; external fixture physical pending |

### Personal-profile matrix

| ID | Scenario | Expected result | Current coverage |
| --- | --- | --- | --- |
| PROF-001 | Open signed-in profile after login or cold start | Only authoritative current-user identity, avatar and presence render; actions stay disabled until that snapshot is loaded | Unit/source + physical online/cold-start passed |
| PROF-002 | Pull/press refresh repeatedly | At most one current-user request runs, progress is visible and the accepted snapshot updates profile plus account identity | Source + physical online/offline/recovered sequential refresh passed; stress pending |
| PROF-003 | Missing optional name, handle, email, avatar, text or emoji | Missing values are omitted or use the documented default-avatar/status presentation; no identity or status is invented | Formatting unit covered; full physical fixture matrix pending |
| PROF-004 | Set text status with existing emoji | Exact bounded text is sent while the current emoji is preserved and the refreshed server value renders | Contract unit + physical text update passed |
| PROF-005 | Clear status text and/or emoji | Explicit JSON `null` is sent for every cleared field; cold refresh shows it absent instead of resurrecting the old value | Raw-body contract unit + physical empty-text clear/cold start passed |
| PROF-006 | Toggle away on and off | Presence action changes only the intended away state and profile renders `Нет на месте`/`В сети` consistently | Contract unit + physical both directions passed |
| PROF-007 | Empty/whitespace/256/257-character status | Empty clears, whitespace normalizes as specified, 256 is accepted and input beyond 256 cannot be submitted | Source limit + unit formatting; boundary physical pending |
| PROF-008 | Close or rotate status dialog before submit | Input and away choice survive recreation; close has no server side effect | Portrait/landscape and cancellation physical pending |
| PROF-009 | Pick PNG/JPEG/GIF/WebP, then cancel preview | Exact local preview is shown, cancel uploads nothing, and picker grants are not retained beyond need | Unit signature matrix + physical pick/preview/cancel passed |
| PROF-010 | Confirm a valid avatar | One bounded multipart request uploads the exact file, validates the returned user UUID, refreshes the avatar and survives cold start | Request/source covered; real physical upload intentionally not run |
| PROF-011 | Spoofed MIME, unsupported type, malformed/empty input, or file over 25 MiB | Validation fails before upload with an actionable message; app remains usable and existing avatar is unchanged | MIME/signature/size unit covered; provider fault physical pending |
| PROF-012 | Default Gravatar versus uploaded/URL avatar | Reset is hidden for the default source and appears only for a resettable source; confirmed reset restores server default | Default-source physical passed; upload/reset physical pending |
| PROF-013 | Picker or preview through portrait/landscape/portrait | Selected URI, preview and explicit Use/Cancel controls survive without implicit upload, crash or clipping | Physical passed |
| PROF-014 | Offline, timeout, 429, 5xx or malformed body during refresh/status/upload/reset | Input/current avatar are retained, operation unlocks, inline error is readable, and an explicit retry is safe | Offline refresh and status failure/retry physical passed; remaining fault injection pending |
| PROF-015 | Rapid refresh/save/upload/reset taps | Each mutation class is single-flight; no duplicate multipart body, stale spinner, double reset or accidental reversal occurs | Synchronous ViewModel guards/source covered; stress pending |
| PROF-016 | Switch/remove account while profile request is in flight | Late results fail with `ACCOUNT_CHANGED` or are discarded; no identity, status or avatar crosses owners | Owner-fence source covered; two-account physical pending |
| PROF-017 | Background, process death, reconnect and desktop/mobile concurrent edit | Authoritative server value converges after refresh without fabricated optimistic success or resurrection | Cold restart passed; process death/cross-client/soak pending |
| PROF-018 | Traverse every visible profile control with TalkBack, large fonts and light/dark themes | Every visible control performs its declared action, has a readable 48 dp target and sufficient contrast; unsupported name/timezone controls are absent | Physical semantics/visual light-theme pass; spoken TalkBack, font and dark-theme matrix pending |

### Settings matrix

| ID | Scenario | Expected result | Current coverage |
| --- | --- | --- | --- |
| SET-001 | First launch for account A | System theme, standard rows, Default sound and both unread priorities off | Unit + physical passed |
| SET-002 | Select light, dark and system modes | Whole app and system bars change immediately; System follows OS mode | Physical passed |
| SET-003 | Force-stop/cold-start after each theme selection | Exact selected mode restores before normal navigation becomes interactive | Light plus compact cold restore passed; remaining matrix open |
| SET-004 | Rotate/profile-chat-profile while changing theme | No Activity crash, stale palette, duplicate navigation or lost selection | Physical pending |
| SET-005 | Switch A → B → A with different preferences | B never receives A's transient or persisted values; A restores exactly | Owner-scoped contract + two-account physical pending |
| SET-006 | Remove and later reconnect the same account | Non-secret UI preferences remain owner-scoped and restore; credentials remain independently cleared | Instrumented/physical pending |
| SET-007 | Corrupt JSON, missing fields, unknown future enum | Each unsupported field, including sound, falls back safely without discarding valid independent fields | Unit covered; file-level corruption handler compiled |
| SET-008 | Concurrent edits to different preferences | Atomic edits merge; the later edit cannot reset another field, including sound, to a stale snapshot | Instrumented device runner passed |
| SET-009 | Storage I/O failure while changing a setting | Old value remains active, saving ends, dismissible error is shown, controls become usable | Fault injection pending |
| SET-010 | Standard density | Avatar, sender and single-line message preview are visible with normal row height | Physical passed |
| SET-011 | Compact density | Row/avatar shrink and sender/message preview disappear; title/time/unread and actions remain usable | Physical + cold restore passed |
| SET-012 | Density at font scale 1.0/1.3/1.5/2.0 | No clipped title, unread badge, menu gesture or inaccessible 48 dp interaction target | Accessibility physical pending |
| SET-013 | Enable personal-unread priority with an older unread 1:1 DM and newer unread channel/group DM | 1:1 DM rises; group DM and channel retain their relative fallback order | Unit covered |
| SET-014 | Personal DM is read or comparison chat is read | Preference does not promote the read row merely because it is personal | Unit covered |
| SET-015 | Enable active-channel priority with older unmuted and newer muted unread channels | Unmuted unread channel rises | Unit covered |
| SET-016 | Active-channel priority off, mixed read state, or comparison includes a DM | Normal folder/activity order remains; unrelated rows are not moved | Unit + physical pending |
| SET-017 | A pinned chat conflicts with either unread preference | Pin remains first; enabled preference overrides only ordinary folder/activity order | Unit covered |
| SET-018 | Change sorting while a filtered/custom folder is selected | Same membership remains, only eligible row order changes, selection and scroll stay valid | Physical pending |
| SET-019 | Rapid repeated taps while save is in progress | At most one visible write is active; disabled state prevents accidental reversal; no stuck spinner | ViewModel/source + stress pending |
| SET-020 | Offline theme/density/sort/sound changes | Local settings remain fully usable and persist; no network request is required | Architecture + sound physical passed; full matrix pending |
| SET-021 | Inspect on-device preference file and logs | No raw server/project/user owner key, credential or message content is stored/logged | Physical DataStore inspection passed for raw owner identifiers; log audit pending |
| SET-022 | TalkBack traverses all setting controls | Section labels, selected choice, switch state and saving/error state are understandable and actionable | Physical accessibility tree exposes checked state; spoken TalkBack traversal pending |
| SET-023 | Functional-control traversal | Every visible setting produces the declared effect; language/layout/idle controls are absent until real implementations exist | Static gate + all five setting groups physically exercised; injected-failure traversal pending |
| SET-024 | 10k-row synthetic catalog and repeated toggles | Reordering remains bounded, scrolling has no sustained jank, no recomposition or DataStore write loop | Benchmark/soak pending |
| SET-025 | Select Default/Subtle/Digital/Glass/Pulse | Exact account-scoped value persists, a bounded preview uses notification audio usage, and a stable named-resource channel is created immediately | Unit + physical selection/channel/audio-path passed |
| SET-026 | Select None | Notification remains visual, but the selected channel has no sound and no vibration; no preview player starts | Unit + physical channel inspection passed; real push pending |
| SET-027 | Reinstall/upgrade and cold start with a selected sound | Selection restores; channel URI uses resource type/name rather than unstable numeric resource ID | Reinstall + Default cold restore + channel inspection passed; non-default/version-to-version matrix pending |
| SET-028 | Android user overrides a preset channel | App keeps the selected logical preset while Android's user-locked channel sound/importance remains authoritative | Documented architecture; physical override/reset pending |
| SET-029 | Switch A → B while their sound choices differ | New notifications resolve B only after B is active; A's choice never leaks into B's profile or push channel selection | Owner-scoped resolver unit covered; two-account push physical pending |
| SET-030 | Push arrives foreground/background/force-stopped/rebooted for each sound | Correct active-owner channel is used once, tap route remains exact, and None stays silent | Real FCM matrix pending; active task topic is excluded |
| SET-031 | Notification permission denied while changing sound | Local selection, preview and channel setup remain usable; no notification is posted until Android permission is granted | Permission/sound combined physical pending |
| SET-032 | Channel creation or preview failure | Saved state remains explicit, saving unlocks, and a dismissible channel-preparation error appears; later push retries channel creation with Default fallback | Source path covered; injected platform failure pending |
| SET-033 | Rapid sound changes during persistence | Disabled chips prevent overlapping writes; every accepted value owns one preview attempt and the final accepted value survives restart | Source + sequential physical passed; stress/fault injection pending |

### About and licenses matrix

The catalog is generated at build time from the selected Android variant's
resolved runtime dependencies. It is packaged as an offline resource; the
screen does not contact a license server and does not expose a speculative
update control.

| ID | Scenario | Expected result | Current coverage |
| --- | --- | --- | --- |
| ABOUT-001 | Open About from Profile | Exact `versionName`, `versionCode` and build type for the installed APK render; Back returns to the same Profile stack | Pure model + physical exact-build/Back/bottom-navigation restore passed |
| ABOUT-002 | Inspect the generated catalog | Every rendered row comes from the selected variant's resolved runtime graph; the catalog is non-empty and dependency names/versions are not hand-maintained | Gradle generation + instrumented parser covered |
| ABOUT-003 | Select components with and without bundled license terms | Every row opens details; a scrollable offline dialog shows readable matching terms or explicitly reports absent metadata; Close returns to the same list/search state | Bundled-text instrumented gate + physical licensed/no-license details, scroll and Close passed |
| ABOUT-004 | Airplane mode, server outage and offline cold start | About, search and license text remain fully usable with zero Workspace/API dependency | Physical Wi-Fi/mobile-data-off cold start and navigation passed; prolonged outage pending |
| ABOUT-005 | Search by name, coordinate, version and license; clear; no match | Matching is locale-stable and case-insensitive; result count is exact; no-match state is explicit; Clear restores all rows | Pure model + physical `ktor`, exact-count, zero-result and Clear paths passed |
| ABOUT-006 | Rotate/background while query or license dialog is open | Query survives recreation, no navigation duplicates, Back/Close remain deterministic and no remote mutation occurs | Physical portrait/landscape/portrait query and open-dialog retention plus Back passed; background pending |
| ABOUT-007 | Generated catalog is absent, blank or unparseable | Build/instrumented acceptance fails before distribution; a shipped variant cannot silently claim an empty license catalog | Exact-resource parser instrumented gate covered |
| ABOUT-008 | Light/dark theme, font scale 1.0–2.0 and TalkBack traversal | Text and dialogs retain sufficient contrast, rows remain readable, and Back/Search/Clear/Close expose meaningful targets | Physical light/dark/landscape visual and semantic-node checks passed; font scale/spoken TalkBack pending |
| ABOUT-009 | Release minification/resource shrinking | The generated `aboutlibraries` raw resource survives and the release catalog/text open without reflection/resource-name assumptions | Explicit resource-ID API used; release APK/instrumented run pending |
| ABOUT-010 | Add/remove/upgrade a runtime dependency | The next APK catalog changes automatically; no stale manual list or dead update button remains | Gradle graph generation covered; dependency-delta CI test pending |

### Cache-control matrix

Cache control remains deliberately narrow even though Room now has an explicit
account partition. The current card is labelled for downloaded attachments and
deletes only those files for the active account; it never silently treats Room
history, encrypted draft/outbox state or credentials as attachment cache.

| ID | Scenario | Expected result | Current coverage |
| --- | --- | --- | --- |
| CACHE-001 | Empty attachment cache | Card reports `0 Б`, the clear control is disabled and visibly says the cache is empty | Physical passed |
| CACHE-002 | Populated cache with files of mixed sizes | Exact bounded size is shown without exposing paths or owner identifiers | Pure formatter/size tests + 1.5 MiB physical passed |
| CACHE-003 | Cancel confirmation | No file is removed, size is unchanged and the dialog closes | Physical passed |
| CACHE-004 | Confirm clear | Only the active account's attachment directory is removed and the card re-reads as empty | Pure filesystem + physical exact-directory passed |
| CACHE-005 | Credentials, drafts, outbox and conversation routes exist | All remain byte-for-byte/logically available after clear and after cold restart | Deletion boundary source proof + signed-in cold restart passed; draft/outbox byte oracle pending |
| CACHE-006 | Another account has cached attachments | Clearing A leaves B's directory and byte count unchanged | Pure filesystem test passed; two-account physical pending |
| CACHE-007 | Account switches while clear is in flight | Captured A path may finish safely, but its result never overwrites B's displayed size or data | Owner fence source path; delayed-I/O test pending |
| CACHE-008 | Read/delete permission or I/O failure | No credential/session mutation occurs; busy state clears and a dismissible error remains | Source path covered; fault injection pending |
| CACHE-009 | Rapid repeated confirmation/taps | One deletion runs; modal and busy state prevent duplicate work or stuck progress | Source path covered; stress pending |
| CACHE-010 | Offline and process recreation | Size and deletion remain fully local; confirmation never submits implicitly after recreation | Local architecture + post-clear cold restart passed; open-dialog recreation pending |

### Diagnostics matrix

| ID | Scenario | Expected result | Current coverage |
| --- | --- | --- | --- |
| DIAG-001 | Open profile online/offline/limited | Card reports the current validated network class without a server request | Online/offline physical passed; limited pending |
| DIAG-002 | Notification permission/global switch/channel variants | Report separates runtime permission from global enablement and counts only `workspace_messages_*` channels | Source/unit covered; physical permission matrix pending |
| DIAG-003 | Share report | Real Android chooser opens with `text/plain`, stable subject and freshly collected values | Online/offline physical chooser passed |
| DIAG-004 | Inspect report with multiple accounts and content-rich chats | No server/project/user/email/token/message/file path or conversation identifier is representable | Allowlist model + forbidden-vocabulary unit test passed; dynamic leak scan pending |
| DIAG-005 | Settings/cache change before share | Fresh report reflects the accepted values and exact current cache bytes, not the first-render snapshot | Source path covered; physical pending |
| DIAG-006 | No share target / activity launch failure | A dismissible error appears and Profile remains usable | Source path covered; injected failure pending |
| DIAG-007 | Offline/cold start | Report remains available without API access and includes current build/platform state | Physical offline cold start/share passed |
| DIAG-008 | Large/hostile environment strings | Report remains bounded, plain text and contains no executable attachment or URI | Control/newline injection and 96-character bound unit covered; broader fuzz pending |
| DIAG-009 | Background/recreation while chooser is open | Returning preserves the signed-in app and does not submit, duplicate or mutate state | Chooser Back physical passed; recreation pending |
| DIAG-010 | Future logs/runtime expansion | Logs are bounded/redacted and correlation identifiers cannot be reversed to account identity | Not implemented; control stays limited to the current truthful snapshot |

## External accounts and projected chats

All destructive scenarios use a dedicated provider/account fixture and the
isolated sandbox stream. The reporting topic is never selected, moved, muted,
archived, renamed, or deleted by this suite. Credentials are injected only into
the create/reconnect request and must be absent from response state, saved
state, screenshots, diagnostics, logs, crash reports, and process snapshots.

| ID | Scenario | Oracle | Coverage |
| --- | --- | --- | --- |
| EXT-001 | Open external integrations without read permission | The entry point is absent or a real forbidden state is shown; no empty catalog is presented as success | Functional unavailable UI implemented; backend/device permission fixture pending |
| EXT-002 | Add a valid Zulip account | One client UUID is POSTed with canonical HTTPS origin, email, explicit selection/history/default project and one write-only API key; returned revision/ETag becomes authoritative | Functional UI + request contract covered; backend/device mutation pending |
| EXT-003 | Reject unsafe provider coordinates | HTTP, userinfo, path, query, fragment, malformed host/UUID/email, blank/overlong key and unsupported provider fail before network I/O | Unit covered |
| EXT-004 | Duplicate create or ambiguous timeout | Conflict or uncertain result preserves the form without automatic duplicate create; refresh reconciles a uniquely matching server account | Backend fault injection pending |
| EXT-005 | Credential non-retention | Rotation, process death, diagnostics export and account switching never reveal or prefill the API key | Non-saveable key state + temporary `FLAG_SECURE` implemented; process-death/device security pending |
| EXT-006 | Paginate accounts and chats | Every page uses a canonical marker, bounded page count and exact account filter; loops, invalid markers, duplicates, oversized/malformed rows fail closed; a completed authoritative refresh prunes only rows unchanged since its baseline so a concurrent newer realtime snapshot cannot be erased | Contract/reconciliation unit covered; MockEngine pagination pending |
| EXT-007 | Switch owner during any page or mutation | Late response is discarded as `ACCOUNT_CHANGED`; no account/chat projection crosses owners | Owner fence implemented; delayed-response acceptance pending |
| EXT-008 | Edit selection/history/default project | One PUT carries only non-secret settings and the current strong `If-Match`; complete returned snapshot replaces no newer revision | Functional settings UI + request/reducer covered; backend/device pending |
| EXT-009 | Concurrent desktop edit | `412` keeps local input, shows current server state and offers functional refresh/reapply/cancel paths; it never blindly overwrites | Bounded conflict + refresh/cancel UI implemented; backend race pending |
| EXT-010 | Reconnect with replacement credential | One POST carries server/email/key plus strong `If-Match`; key clears from UI memory after completion or cancellation | Functional secure dialog + request contract covered; device pending |
| EXT-011 | Disconnect and reconnect lifecycle | Disconnect uses the real no-body action, converges through connecting/live or explicit safe error, and does not delete mappings | Functional confirmation/status UI + request contract covered; backend/device pending |
| EXT-012 | Delete account confirm/cancel | Cancel is side-effect free; confirm sends one DELETE, removes account/chats/provider streams only after success and remains idempotent with realtime delete | Functional confirmation UI + reducer covered; backend/device pending |
| EXT-013 | Realtime create/update ordering | A newer full snapshot wins; duplicate or lower revisions cannot roll status/settings/capabilities back | Unit covered |
| EXT-014 | Realtime account delete replay | Delete tombstone removes child chat state and provider streams; an equal/older create cannot resurrect them, while a genuinely newer create can | Unit covered |
| EXT-015 | Poison external event | Kind/action/UUID/snapshot mismatch or malformed row is bounded, skipped and cursor-advanced without crash/reconnect loop | Unit covered; live fixture pending |
| EXT-016 | Select an available external chat | Exact chat/project UUID POST starts one transition; progress is visible until the returned/realtime projection supplies the real Workspace stream | Functional UI + request contract covered; backend/device pending |
| EXT-017 | Deselect a projected chat | Confirmed action removes the projection stream/topics/messages/folder rows without touching the source provider chat | Functional confirmation UI + reducer foundation covered; backend/device pending |
| EXT-018 | Move a projected chat | Exact target project and strong revision POST once; old projection disappears and new projection is navigable only after authoritative state arrives | Functional current-project UI + request contract covered; backend/device pending |
| EXT-019 | Chat assignment conflict | `412`, transition already pending, deleted target and permission loss preserve the last real snapshot and expose refresh/cancel rather than optimistic success | Bounded conflict/transition UI implemented; backend race pending |
| EXT-020 | Account status and safe errors | connecting/backfill/live/degraded/auth-required/disconnected/suspended render distinct bounded states; only capability-backed actions appear | Status/error UI implemented; capability fixture/device pending |
| EXT-021 | External chat types | Channel, personal and group rows remain distinguishable, searchable and correctly labeled at 1x/1.5x/2x font scale | Search/type UI implemented; font-scale device pending |
| EXT-022 | Provider-origin navigation | Only validated HTTPS original URLs open in a safe external surface; missing/unsafe URL exposes no inert control | Same-origin/port unit + functional launcher failure UI covered; device pending |
| EXT-023 | Offline and radio interruption | Cached real rows remain labeled stale; mutations stay unsent and retryable, reconnect drains REST events before one socket resumes | Device/backend fault injection pending |
| EXT-024 | Large provider catalog | 25k bounded rows remain responsive, cancellable and memory-stable; search/filter do not start an unbounded request fan-out | Performance/soak pending |
| EXT-025 | Eight-hour bridge convergence | Repeated provider traffic, auth expiry, server restart, background/foreground and radio changes converge without duplicate streams, revision rollback, reconnect storm or battery runaway | Soak pending |
| EXT-026 | Paginate pending/failed operations | Every page carries the exact account UUID and canonical marker; duplicate UUIDs, marker loops, oversized history/metadata, invalid revisions and cross-account rows fail closed without partially pruning the old snapshot | Request/validator/reconciliation unit covered; MockEngine pagination pending |
| EXT-027 | Operation realtime create/update/delete | Full snapshots converge by revision; a delete tombstone rejects an equal/older replay and account deletion clears the child queue | Realtime/tombstone unit covered; live fixture pending |
| EXT-028 | Retry an eligible operation without duplicate risk | Exactly one retry POST sends `confirm_duplicate_risk=false`, merges only the matching operation/account snapshot and refreshes to the authoritative queue | Functional UI + request contract covered; backend/device pending |
| EXT-029 | Retry with possible provider-side duplicate | The first tap opens an explicit risk dialog and performs no request; confirmation sends `confirm_duplicate_risk=true` once, cancel sends none, and a changed/non-retryable snapshot cannot submit | DTO invariants + functional confirmation implemented; delayed realtime/device acceptance pending |
| EXT-030 | Discard eligible queued/failed work | Confirmation sends one DELETE only for `can_discard=true`; success removes only that operation, while cancellation, timeout and rejection preserve it and explain that an already-applied provider effect is not undone | Functional confirmation/reducer implemented; backend/device pending |
| EXT-031 | Capability unavailable reasons | `available=false` hides dependent controls and displays the bounded sanitized reason; malformed/unknown descriptors fail closed, and light/dark/high-contrast states remain readable | Capability parser/model unit covered; permission/capability device matrix pending |
| EXT-032 | Owner switch during operation list/retry/discard | Every late page or mutation becomes `ACCOUNT_CHANGED`; no queue row, notice or error from account A appears in account B and one accepted tap cannot double-submit | Owner fence/single-flight source covered; controlled delayed-response test pending |
| EXT-033 | Operation provider-origin navigation | Only same-origin HTTPS and the exact effective port open; HTTP, userinfo, other host/port, malformed and overlong URLs expose no action | Shared same-origin unit + functional launcher failure state covered; device pending |
| EXT-034 | Retry/discard dialogs across rotation/process recreation | Dialog identity and warning survive Activity recreation without implicit request; stale/disappeared rows close safely and no secret or raw operation evidence enters saved state | Saveable UUID/no-secret architecture implemented; instrumented and process-death acceptance pending |
| EXT-035 | Large and hostile operation queue | 25k rows, 256 bounded attempts and maximum sanitized metadata remain scrollable and memory-stable; controls retain 48 dp targets at 2x font without overlapping status text | Limits enforced in validator; macrobenchmark/font-scale/soak pending |
| EXT-036 | Operation failure matrix | 401 removes the expired session, 403/409/412/429/timeout/5xx/malformed map to bounded recovery, busy always clears, and an ambiguous retry is never automatically repeated | Shared error mapping + single-flight implemented; MockEngine/backend matrix pending |
| EXT-037 | Provider-admin permission discovery | Policy, health and bridge reads run independently; three `403` responses keep the whole admin surface absent, while any authorized resource exposes only its own real section | Independent nullable authorization implemented; IAM/device matrix pending |
| EXT-038 | Read provider policy | Exact `zulip` policy, non-negative bounded limits, custom-CA metadata, revision and matching strong ETag are required; malformed provider/digest/count/revision/ETag fails closed | DTO/validator unit covered; backend fixture pending |
| EXT-039 | Update provider policy | One PUT carries `kind=zulip`, enabled state, all three bounded limits, explicit custom-CA intent and the current `If-Match`; no-change UI cannot submit | Request contract + dirty editor implemented; backend/device mutation pending |
| EXT-040 | Reject unsafe policy input | Negative/overflow limits, non-numeric UI input, weak/mismatched ETag, oversized JSON and unsupported provider are rejected without network mutation | Unit/UI bounds covered |
| EXT-041 | Replace or remove custom CA | Existing CA requires an explicit replacement or removal; 1–32 complete ASCII PEM certificate blocks are accepted, surrounding data/private-key markers/oversize input fail locally, and backend certificate validation remains authoritative | Parser/request unit + explicit-null UI implemented; real CA backend fixture pending |
| EXT-042 | Emergency suspend/resume provider | Suspend requires impact confirmation and sends exactly one realm-wide action; resume appears only while suspended; cancel, rotation and a stale refreshed policy send nothing | Functional confirmation + stale-state guard implemented; backend/device pending |
| EXT-043 | Aggregate provider health privacy | Status and bounded account/chat/bridge/operation counts render without account email, server, chat names, credentials or message content; unknown status remains bounded text | Validator/model unit covered; live privacy inspection pending |
| EXT-044 | Paginate bridge instances | Bounded pages use canonical markers; duplicates, loops, malformed Zulip rows and owner changes fail closed, while future non-Zulip kinds are omitted from this panel | Repository/DTO contract implemented; MockEngine pagination pending |
| EXT-045 | Bridge suspend/resume lifecycle | Only state-valid actions are visible; suspend is confirmed, resume sends once, updated snapshot UUID must match, and a changed revision closes stale confirmation | State-gating/model unit + functional UI implemented; backend/device pending |
| EXT-046 | Revoke bridge identity | Irreversible warning precedes one revoke; cancellation and recreation have no effect, revoked state hides all actions, and returned generation/revision must remain positive | Functional confirmation/validator covered; platform reenrollment acceptance pending |
| EXT-047 | Partial admin permissions | Read-only policy, health-only, bridge-read-only and action-denied roles render useful authorized data; a denied mutation returns a bounded error and never looks successful | Independent-resource architecture implemented; IAM role matrix pending |
| EXT-048 | Owner switch during admin read or mutation | Late policy/health/page/action responses are rejected by owner key; no admin data, notice, dialog or error crosses saved organizations | Owner fencing implemented; controlled delayed-response test pending |
| EXT-049 | Provider-admin failure matrix | 401, 403, 409/412, 429, timeout, 5xx, truncated/oversized/malformed payload and page-loop keep prior real state, clear busy, and expose Refresh/Close without automatic destructive retry | Shared error/recovery path implemented; MockEngine/backend matrix pending |
| EXT-050 | Large admin surface and long-running health | 10k bridge rows, maximum count maps/CA input, 2x font, rotation, repeated refresh and an eight-hour health/heartbeat soak stay responsive, bounded and battery-stable | Bounds/virtualized list implemented; macrobenchmark/font/soak pending |

## UX and accessibility

### Functional-control audit

- Generate or maintain an inventory of every reachable interactive Compose
  semantics node and its declared product action.
- Traverse all navigation states for each role and account capability set.
- Exercise every enabled control and assert its navigation, local state,
  platform effect, or authoritative server mutation.
- Inject offline, timeout, permission-denied, conflict, and authorization
  failures and assert that the control exits loading and offers a valid next
  step.
- Fail the suite for empty callbacks, TODO/placeholder text, mock member or
  profile data, destinations with no meaningful content, and controls that
  appear enabled but cannot complete.
- Compare the runtime action inventory with the maintained UI action registry;
  unexplained additions or missing actions block the milestone.

### Automated checks

- Every interactive control has a meaningful semantic role and label.
- No duplicate labels in a screen scope.
- Minimum touch target is 48 dp.
- Focus order follows visual order.
- Text fields expose label, error, password semantics, and IME action.
- Dialogs trap focus semantically and expose title/action hierarchy.
- Long dialogs remain vertically scrollable at landscape height and preserve
  their scroll/form state across Activity recreation.
- Screens remain usable at font scales 1.0, 1.3, 1.5, and 2.0.
- Contrast meets WCAG AA for text and essential icons in light/dark themes.
  Message body, secondary metadata, author accents, timestamps, and inline-code
  foreground/background pairs are covered by token-level ratio tests and the
  same live conversation is visually checked in both themes on the physical
  device.

### Manual physical-device checks

- One-handed reachability and accidental-tap resistance.
- TalkBack traversal, rotor/actions, announcements for send/retry/error.
- Software keyboard show/hide, multiline composer, emoji keyboard, and
  predictive text.
- Back gesture from edges, overscroll, fling, long press, selection, and media
  gestures.
- Bright/dim display, outdoor readability, and dark-room dark theme.
- Interrupted actions remain understandable without relying on toast-only
  feedback.

## Realtime connection health and recovery

| ID | Scenario | Expected result | Current evidence |
| --- | --- | --- | --- |
| RT-001 | Healthy cold/warm foreground connection completes within 1.5 seconds | No transient banner flashes; the catalog remains usable | Physical Android 14 Pixel pass |
| RT-002 | Cursor bootstrap, REST catch-up or websocket handshake remains in progress beyond 1.5 seconds | A resource-backed connecting banner appears without an inert retry control | Mapping/compile covered; controlled delay pending |
| RT-003 | Websocket transport opens but no protocol `ready` frame arrives | The connection never reports healthy; heartbeat timeout reaches bounded backoff | Ready gating implemented; controlled half-open pending |
| RT-004 | Valid `ready` frame arrives after strict catch-up | The banner disappears only after the server generation/version is accepted and persisted | Cursor tests + physical Android 14 Pixel pass |
| RT-005 | Disable every network transport while the app is foregrounded | Existing rows remain visible, the half-open socket closes within 40 seconds, and an assertive recovery banner explains that shown data may be stale | Ping policy + physical Android 14 Pixel pass |
| RT-006 | Tap **Retry now** during backoff | The current delay is interrupted once and the retained owner immediately starts cursor/bootstrap recovery; rapid taps are conflated | Guard/channel tests + physical Android 14 Pixel pass |
| RT-007 | Restore network after one or more failed attempts | REST catch-up completes before a new socket opens, missed events converge once, and the banner clears only on `ready` | Catch-up ordering + physical Android 14 Pixel pass |
| RT-008 | Background and foreground the app during connect/backoff | Background pause is silent and produces no retry loop; foreground resumes through the single retained runtime | Lifecycle owner implemented; controlled lifecycle pending |
| RT-009 | Rotate and switch light/dark theme while either banner is visible | Content remains reachable, contrast stays legible, TalkBack has one live-region announcement, and retry remains functional | Portrait/landscape and light/dark physical pass; TalkBack traversal pending |
| RT-010 | Switch or remove the active account after queuing manual retry | Pending retry is discarded and cannot start work with the previous owner credentials | Owner guard + queue clearing implemented; delayed switch pending |
| RT-011 | Receive REST `410` or websocket `4410` during recovery | Server-derived projections reset, local outbox rows survive, catch-up restarts from a fresh cursor and health remains visible until ready | Repository unit covered; backend/device pending |
| RT-012 | Repeated flapping through maximum backoff | Delay remains bounded, no duplicate socket owner appears, banner semantics do not spam on unchanged state, and manual retry never runs in background | Backoff/unit partly covered; soak pending |

## Security and privacy

- Reject cleartext server URLs outside an explicit development-only policy.
- Validate every host, deep link, redirect, meeting URL, and downloaded filename.
- Verify access/refresh tokens and push keys are Keystore-backed and excluded
  from backup, logs, screenshots, intents, and saved state.
- Verify account data separation in database keys, files, notifications, and
  worker input.
- Protected Coil image and avatar requests use account-derived opaque
  memory/disk keys; Workspace authorization is never attached to a public
  avatar URL or reused for an inactive account.
- Verify exported components and intent extras cannot trigger unauthorized
  actions.
- Verify file URI grants are minimal and revoked.
- Verify WebView/Custom Tab integration has an explicit origin and navigation
  policy.
- Run dependency, manifest, network security, backup, and release minification
  audits.

## Performance and longevity

### Performance budgets

- Cold start to usable cached catalog: target under 2.5 seconds on the physical
  acceptance phone.
- Warm start: target under 1 second.
- Main chat list and message list: no sustained jank during normal scrolling.
- Search typing remains responsive with a large local catalog.
- Image preview does not exceed a bounded decoded size.
- Background idle produces no request/reconnect loop.

### Soak profiles

1. **Interactive two-hour soak**
   - Continuous send/receive, navigation, search, reactions, files, and calls.
   - Capture crash/ANR, memory, CPU, network, database size, and wakeups.

2. **Eight-hour lifecycle soak**
   - Repeat foreground/background, screen off/on, network changes, and push.
   - Assert one active realtime owner and bounded workers/timers.

3. **Twenty-four-hour synchronization soak**
   - Moderate multi-client traffic with periodic outages and cursor catch-up.
   - Assert no missing/duplicate messages and stable unread totals.

4. **Large-account soak**
   - Large user/stream/topic/folder catalogs and long conversations.
   - Measure startup, sync, search, scroll, memory, and database growth.

Memory acceptance uses repeated post-GC samples: retained activity/view-model
counts must return to baseline and the heap must not show an unbounded upward
slope. Battery/network acceptance compares active use and background idle
against a recorded baseline on the same device.

## Automation entry points to add

- `testDebugUnitTest`: all unit and contract suites.
- `connectedExordosDebugAndroidTest`: Compose and Android integration suites.
- A device E2E runner that records revision, device/API, app version, scenario,
  result, duration, and sanitized artifact paths.
- A controllable backend test fixture for accounts, permissions, conversations,
  message traffic, and fault injection.
- A soak runner with periodic `dumpsys`, process, database, and network metrics.

Do not put raw run logs or environment details in the application repository.
Store sanitized historical run reports in the designated CASSI test-run archive.

## Milestone gates

For every milestone:

1. Run focused unit/contract tests while developing.
2. Run all unit tests for both flavors.
3. Run the code-review checklist and fix blocking findings.
4. Run affected Compose/instrumented tests.
5. Run affected backend E2E scenarios.
6. Install the exact revision on the physical phone and perform the affected UX
   and failure checks.
7. Re-run the review checklist on the final diff.
8. Run the functional-control audit and resolve every reachable dead action.
9. Record verified results and unresolved limitations without claiming unrun
   scenarios.
