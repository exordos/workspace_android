# Production UI Action Registry

## Contract

This registry is the release inventory for every production interaction. A
control may be reachable only when its **effect**, **failure state**, and
**acceptance check** are defined. Rows marked `GAP` block the milestone that
owns them. A capability without a verified product/backend contract stays
hidden and is recorded under **Intentionally not exposed**.

Static source scanning complements this registry, but does not replace runtime
Compose semantics traversal on every supported role and account.

## Authentication

| ID | Screen / interaction | Handler and effect | Failure/recovery | Acceptance |
| --- | --- | --- | --- | --- |
| AUTH-A01 | Organization URL field | `ChooseServerViewModel.onServerChange`; updates validated candidate URL | Inline validation, no persistence before validation | AUTH-001/002 |
| AUTH-A02 | Organization sign-in button / IME action | Fetches public settings, saves canonical server, opens credentials | Typed timeout, network, HTTP, and malformed-response error | AUTH-001/002 |
| AUTH-A03 | Public-server card | Selects the production public URL and runs the same validation path | Same as AUTH-A02 | AUTH-001/002 |
| AUTH-A04 | Login and password fields | Update in-memory credentials only | Field-specific validation; password is not logged or saved | AUTH-003/004/005 |
| AUTH-A05 | Password visibility | Toggles local visual transformation | No external failure | Compose semantics test |
| AUTH-A06 | Login button / IME action | Base IAM login, then project discovery | Credential/OTP/project/server errors remain on the active step | AUTH-003–009 |
| AUTH-A07 | Leave organization | Clears the current local session before returning to server choice | Awaited deletion; navigation occurs only afterwards | AUTH-011 |
| AUTH-A08 | OTP field and confirm | Repeats login with the six-digit OTP | Invalid/expired OTP is retryable; secrets remain in memory only | AUTH-006/007 |
| AUTH-A09 | Back from OTP | Clears OTP state and returns to credentials | No external failure | Compose state test |
| AUTH-A10 | Project radio row | Selects one real IAM project UUID | Selection is local and reversible | Instrumented state test |
| AUTH-A11 | Project confirm | Refreshes into project scope and atomically stores the session | Typed error; no partial DataStore session | AUTH-008/010 |
| AUTH-A12 | Back from project | Discards temporary base tokens and returns to credentials | No external failure | Compose state test |

## Messenger catalog and folders

| ID | Screen / interaction | Handler and effect | Failure/recovery | Acceptance |
| --- | --- | --- | --- | --- |
| CAT-A01 | Bottom-nav Chats | Restores the chat-list destination without duplicating it and exposes one labeled selected-tab semantic node | Back stack remains valid | Navigation suite + physical accessibility dump |
| CAT-A02 | Bottom-nav Profile | Opens the real signed-in profile and exposes one labeled selected-tab semantic node | Back stack remains valid | Navigation suite + physical accessibility dump |
| CAT-A03 | Back from channel detail | Clears selected stream and restores catalog | No external failure | Navigation suite |
| CAT-A04 | New direct chat | Opens real-user selector; selected user creates/reuses private stream | Selector exposes loading/error/retry; create error remains visible | CAT-003 |
| CAT-A05 | Catalog search field | Filters current authoritative streams by name | Empty query/result has explicit state | Component test |
| CAT-A06 | Folder tab | Selects a real folder and projects only its folder items | Missing items reconcile from folder refresh/realtime | CAT-011/012 |
| CAT-A07 | Add-folder tab | Opens folder-name dialog | Dialog is dismissible | CAT-011 |
| CAT-A08 | Create folder | POSTs trimmed nonblank title, reloads authoritative folders | Button disabled for blank input; API error banner is dismissible | CAT-011 |
| CAT-A09 | Chat row tap | Opens direct chat or loads channel topics | Loading/empty/error state leaves navigation usable | CAT-001/003 |
| CAT-A10 | Chat row long press in All | Opens assignment picker | Gesture is absent until a folder context exists | Component semantics test |
| CAT-A11 | Assign chat to folder | POSTs canonical folder item and reloads folders | API/reload error banner; realtime later reconciles | CAT-012 |
| CAT-A12 | Chat row long press in custom folder | Shows removal action | Gesture is absent without a selected folder | Component semantics test |
| CAT-A13 | Remove chat from folder | DELETEs exact folder-item UUID and reloads folders | Missing/already-removed item and API failure are visible | CAT-012/013 |
| CAT-A14 | Stream rail item | Selects stream and loads its topics | Loading/empty/error state remains navigable | CAT-001 |
| CAT-A15 | Topic row | Opens the exact stream/topic route | Missing default topic is never fabricated | Navigation suite |
| CAT-A16 | Custom-folder long press | Opens rename/delete actions only for user-managed folders | System folders expose no destructive menu | CAT-011 |
| CAT-A17 | Rename/delete folder | PUTs or DELETEs the exact folder UUID, then refreshes the authoritative folder list | Retained single-flight request, request-ID completion after recreation, confirmation and recoverable error | CAT-011/014/019 |
| CAT-A18 | Pin/unpin chat in folder | Invokes the exact folder-item action and refreshes ordering | Missing item or API failure is visible | CAT-012/013 |
| CAT-A19 | Mark channel read | POSTs stream read action and reconciles stream/folder unread totals | Single-flight mutation and error banner | CAT-010/013 |
| CAT-A20 | New channel | Opens a scrollable form for name, description, private/public mode, announcement and real-user selection | Blank name disables submit; form survives Activity recreation; a server-accepted create is never blindly retried when follow-up topic/catalog loading fails | CAT-004/014/018/020 + physical rotation |
| CAT-A21 | New topic | POSTs a nonblank name for the exact stream and upserts the returned topic | Retained single-flight request; request-ID completion closes only the matching restored form; error preserves retry context | CAT-005/013/019 |
| CAT-A22 | Topic long press | Opens desktop-parity notification/read/rename/done actions | No backend-only placeholder or unsupported action is rendered | CAT-005/006/009/010 |
| CAT-A23 | Topic rename/done/notification/read | Invokes the exact topic action and updates the shared stream/topic projection | Retained single-flight request survives recreation; request-ID completion and recoverable inline error | CAT-005/006/009/010/013/019 |

## Inbox

| ID | Screen / interaction | Handler and effect | Failure/recovery | Acceptance |
| --- | --- | --- | --- | --- |
| INBOX-A01 | Messenger-header Inbox icon/badge | Opens the real unread projection and reports its bounded aggregate count | The exact owner's encrypted catalog and success marker restore real cached rows or a confirmed empty state; missing cache still loads from the server and no notification placeholder is exposed | INBOX-001/002/005/013 |
| INBOX-A02 | Inbox back | Pops to the prior messenger catalog route | Back-stack remains valid after rotation and refresh | INBOX-011 |
| INBOX-A03 | Inbox refresh/retry | Fetches authoritative streams plus all visible topics in one single-flight, owner-bound refresh, then persists the catalog before its success marker | Existing rows or a confirmed empty state survive network/server/malformed failure; account changes fail closed and either realtime projection changing during the request causes one atomic retry | INBOX-005–009/013 |
| INBOX-A04 | Unread topic row | Opens the exact stream/topic UUID, independent of duplicate labels | Missing/deleted route produces a visible refresh instruction | INBOX-003/010 |
| INBOX-A05 | Stream fallback row | Opens a channel topic list or resolves a direct chat's authoritative default topic | Busy state blocks duplicate taps; unavailable/default-topic failure is visible and dismissible | INBOX-004/006 |

## Feed

| ID | Screen / interaction | Handler and effect | Failure/recovery | Acceptance |
| --- | --- | --- | --- | --- |
| FEED-A01 | Catalog Feed tab | Opens the real cross-conversation message projection | Route is stable across Back and configuration recreation; no placeholder destination exists | FEED-001/011 |
| FEED-A02 | Feed back | Pops to the prior messenger catalog route | Back stack and retained feed position remain valid | FEED-011 |
| FEED-A03 | Feed refresh/retry | Refreshes one owner-bound global newest page; missing navigation targets are resolved lazily by exact UUID | Existing cached/realtime rows survive network/server/malformed/journal-overflow failure; duplicate input is single-flight | FEED-009/010/013–015 |
| FEED-A04 | Message preview/open action | Opens the exact canonical stream/topic/message route | Missing/deleted catalog target produces a visible recovery instruction, never a guessed route | FEED-002/003 |
| FEED-A05 | Forward action | Opens the exact source message and immediately starts the retained production forwarding picker | Missing source reports a recoverable error; delivery uses existing ambiguity verification and never blindly retries | FEED-004 + MSG-FWD-001–012 |
| FEED-A06 | Top-scroll/load previous/retry | Requests one older page, preserves the visible UUID anchor and reuses the authenticated persisted marker on recoverable failure | Malformed/repeated/unrelated/damaged markers fail closed without merging, rollback or request loops | FEED-005–008/013–016 |
| FEED-A07 | Scroll-to-newest button | Returns to the latest loaded row and rearms top pagination | Hidden while already at bottom; no external failure | FEED-001/005/012 |

## Starred activity

| ID | Screen / interaction | Handler and effect | Failure/recovery | Acceptance |
| --- | --- | --- | --- | --- |
| ACT-A01 | Catalog Starred tab | Opens the real read-only `starred=true` projection | Stable route/Back behavior; unsupported Activity filters are not rendered beside it | ACT-STAR-001/010/012 |
| ACT-A02 | Starred back | Pops to the prior messenger catalog route | Back stack remains valid after refresh and recreation | ACT-STAR-010 |
| ACT-A03 | Starred refresh/retry | Requests one owner-bound newest page while retaining the `starred=true` filter | Existing cached/realtime rows survive recoverable or journal-overflow failure; duplicate input is single-flight | ACT-STAR-001/007/009/013–015 |
| ACT-A04 | Message preview/open | Resolves and focuses the exact canonical stream/topic/message route | Missing/deleted target produces a visible error and never a guessed route | ACT-STAR-002/004 |
| ACT-A05 | Forward | Opens the exact starred source in the normal retained forwarding picker | Missing source is recoverable; existing ambiguity/exactly-once protections apply | ACT-STAR-005 |
| ACT-A06 | Load previous/retry | Requests the same filtered older marker and preserves the visible UUID anchor | Unstarred/malformed/repeated/unrelated pages fail closed without merge or loop | ACT-STAR-006/008 |
| ACT-A07 | Scroll to newest | Returns to the latest loaded starred row | Hidden at bottom; no external failure | ACT-STAR-001/006/011 |

## Drafts

| ID | Screen / interaction | Handler and effect | Failure/recovery | Acceptance |
| --- | --- | --- | --- | --- |
| DRAFT-A01 | Catalog Drafts tab | Opens the real server/local draft projection; multiple UUIDs for one conversation remain separate | Initial load has explicit loading/empty/error states; no placeholder page | DRAFT-004/007 |
| DRAFT-A02 | Drafts back | Pops to the prior messenger route | Back stack remains valid after refresh/recreation | DRAFT-018 |
| DRAFT-A03 | Refresh / initial retry | Loads all bounded server pages, validates owner/route/order/marker and reconciles encrypted local slots | Prior rows survive recoverable failure; request is single-flight and owner-fenced | DRAFT-007/012/015 |
| DRAFT-A04 | Draft card open | Resolves the authoritative stream/topic, persists the exact draft slot, then opens that UUID's composer | Missing route or secure-storage failure is visible; another draft in the same conversation is untouched | DRAFT-004/005/008 |
| DRAFT-A05 | Draft card delete | Persists a tombstone, then DELETEs the exact UUID with its strong ETag | Confirmation state is single-flight; `404` succeeds; 412/failure retains working recovery actions | DRAFT-003/009/010 |
| DRAFT-A06 | Failed draft retry | Reuses the stable pending create payload or current UUID/ETag update/delete operation | Bounded transient retry; no blind duplicate create or request loop | DRAFT-011/012/013 |
| DRAFT-A07 | Conflict Use server | Replaces only the selected local text/baseline with the strictly validated current server revision | Invalid conflict payload never enables this action | DRAFT-010 |
| DRAFT-A08 | Conflict Keep mine | Rebases selected local text on the current server ETag and performs one exact PUT | Further conflict remains visible and actionable | DRAFT-010 |
| DRAFT-A09 | Conflict Delete | Uses the conflict revision/ETag to delete the selected server draft | Failure retains its tombstone and Retry; other slots remain intact | DRAFT-009/010 |
| DRAFT-A10 | Composer change in selected slot | Debounces encrypted local persistence and POST/PUT synchronization for only that slot | Offline/server/storage error is visible; navigation and other drafts remain usable | DRAFT-001/002/004/014 |

## Conversation

| ID | Screen / interaction | Handler and effect | Failure/recovery | Acceptance |
| --- | --- | --- | --- | --- |
| MSG-A01 | Header back | Pops the conversation route | No external failure | Navigation suite |
| MSG-A02 | Channel header | Opens real channel information; direct-chat header is not clickable | No external failure | Navigation suite |
| MSG-A03 | Start call | Visible only for a valid HTTPS Jitsi server; persists and sends the call message, then opens Jitsi only after the exact server confirmation | Failed/ambiguous delivery remains visible in outbox and the meeting is not opened | Calls + outbox suite |
| MSG-A04 | Attachment picker | Opens Android multi-document picker and previews up to ten selected image/video/document/unknown-MIME URIs | Cancellation is side-effect free; inaccessible URI and selection-limit errors remain visible | Files suite + physical-device TXT selection |
| MSG-A05 | Remove selected attachment | Clears only the selected pending attachment | No external failure | Component test |
| MSG-A06 | Composer text | Updates a debounced, Keystore-encrypted draft scoped to the exact account/stream/topic/draft slot and synchronizes it through the server draft contract | Text, quote, edit context and persisted document permissions restore after recreation/process death; storage/sync/conflict failure is visible | DRAFT-001–006/010–017 |
| MSG-A07 | Send/save | Sends nonblank text, quote, and/or sequential file uploads using desktop-compatible `urn:image/video/file` metadata, or edits the selected message | The outbox is encrypted and persisted before POST; rejected sends are retryable, while timeout/network/5xx/malformed outcomes require verification or explicit duplicate-risk confirmation | Send/outbox suite + cross-client file E2E |
| MSG-A08 | Close reply/edit context | Cancels exact reply/edit state; cancelling or completing an edit restores the previously suspended draft | Missing referenced messages recover both edited and prior text into a normal draft | Component + process-restoration test |
| MSG-A09 | Message long press | Opens actions; no empty tap action is exposed | Gesture has TalkBack long-click semantics | Component semantics test |
| MSG-A10 | Quick reaction, full emoji picker or existing reaction | Exposes six labeled quick reactions and the complete bundled searchable Unicode catalog for every message type; sends one exact reaction and immediately reconciles the server-canonical own UUID, aggregate count and glyph chip; realtime events update other-client counts without doubling an optimistic own echo | Per-owner message/emoji single-flight blocks duplicate requests; malformed/mismatched responses and account races fail closed, concurrent authoritative counts beat stale completions, API errors remain visible, unknown custom names stay readable; picker target/query survive rotation | MSG-ACT-008/009 + catalog/projection/response/realtime unit tests + production-catalog Compose instrumentation + physical sandbox add/remove/light/dark/rotation |
| MSG-A11 | Edit own native message | Suspends the existing draft and loads original content into the composer; provider-backed edit stays hidden without a verified capability/preflight contract | Server failure keeps the edit recoverable; missing source preserves both texts | Message-actions + process-restoration suite |
| MSG-A12 | Quote message | Adds exact author/message reference to composer | Source remains navigable | Message-actions suite |
| MSG-A13 | Sender avatar | Opens authoritative user profile | Missing optional fields are omitted, never invented | Profile suite |
| MSG-A14 | Image thumbnail | Opens authenticated fullscreen preview | Load failure stays in message; close/back remains available | Media suite |
| MSG-A15 | Fullscreen image gestures | Pinch/pan, double-tap reset, tap/close dismiss | Bounds prevent invalid scale | Media suite |
| MSG-A16 | Call-message card | Joins only a same-host HTTPS configured Jitsi room; unavailable cards open the functional message-actions menu instead of a dead tap | Invalid/unconfigured call never launches Jitsi and remains actionable through quote/reaction and owned-native edit/delete | Calls + message-actions suite |
| MSG-A17 | File/video attachment card | Authenticates a protected download, validates UUID/size/name, writes only private cache, and opens a temporary read-only FileProvider URI | Timeout/auth/network/oversize/no-handler errors remain visible; repeated tap is single-flight | Files suite + physical-device download/open |
| MSG-A18 | Image/file/call message long press | Opens the same permission-aware reaction/edit/delete/quote action menu as text messages | Gesture has TalkBack long-click semantics | Component semantics test |
| MSG-A19 | Rich message rendering and references | Renders body, links, quotes, ordered/unordered lists, inline/fenced code, inline/fenced spoilers, known Unicode emoji shortcodes, UUID-backed mentions, metadata and deterministic author accents with theme-bound tokens; a bounded CommonMark structure model preserves list/quote/code meaning for TalkBack; localized spoiler controls reveal or hide content in place; ordinary safe links and canonical URL, user, message, stream and scoped/unscoped topic URNs open their exact external or owner-bound mobile destination | Theme changes rebuild the renderer; all normal-size text pairs meet WCAG AA 4.5:1; spoiler state survives recreation while code/link/image/autolink syntax remains metadata-inert; unknown/custom emoji and unresolved/ambiguous display mentions remain readable; unsafe URL URNs never receive Workspace credentials, while malformed, missing, archived, duplicate, cross-scope, response-mismatched or account-raced Workspace references fail closed with a visible error instead of guessing a destination | MSG-RICH-001–012 + structure/spoiler/metadata/contrast unit tests + exact Compose instrumentation + physical-device light/dark/rotation/reference navigation |
| MSG-A20 | Failed outbox row | Removes or retries a request known to be rejected | The exact local row persists across restart until confirmation or explicit removal | Send/outbox suite |
| MSG-A21 | Ambiguous outbox row | Verifies against current server history, explicitly retries with a duplicate warning, or hides only the local row after confirmation | No automatic retry; a unique post-attempt candidate can reconcile, while zero/multiple candidates remain visible; exact-topic recovery remains actionable after a fully cold offline start | Fault-injection + cross-client + physical cold-start suite |
| MSG-A22 | Delete owned native message | Shows an irreversible confirmation and sends one canonical UUID DELETE; the row and matching edit/quote references are removed only after success | Per-message single-flight; cancellation is side-effect free; offline/server failure keeps the row and exposes a dismissible error; provider/non-owned actions stay hidden | MSG-ACT-001–006 + physical Pixel + visible desktop |
| MSG-A23 | Load previous/next history and exact-message context | Normal entry loads the bounded latest page; an exact route first renders its canonical detail anchor, atomically replaces stale projection rows with strict older/anchor/newer context, and exposes automatic plus explicit continuation in both directions; process recreation restores the encrypted exact-owner mode, anchor and both markers | Local outbox and newer overlapping realtime edits survive replacement; one failed side remains retryable, rejected/malformed markers reset safely, account switches discard late results, and cross-chat/wrong-side/out-of-order rows fail closed; missing/trimmed/damaged cache becomes an honest two-sided unknown window with functional load and refresh actions | MSG-HIST-001–017 + physical Pixel 55-message bidirectional/rotation acceptance; physical persisted-marker acceptance pending |
| MSG-A24 | Forward message | Opens a retained recipient picker for a real channel/topic or user and sends one or more ordered desktop-compatible `[author](urn:quote:<uuid>)` references through the normal message endpoint | Archived/direct streams are separated correctly; partial direct-chat creation is rediscovered before retry; a timeout/network/5xx/malformed result is verified against a pre-attempt UUID baseline and never blindly retried | MSG-FWD-001–018 + unit/contract gate; deterministic ambiguous-result matrix, two single-source plus one multi-source physical sandbox send, rotation, offline preflight, and visible-desktop exactly-once convergence passed; physical multi-select/post-request fault injection pending |
| MSG-A25 | Forwarded quote block / source action | Resolves one canonical source UUID, renders the real author and source content (or selected plain text), and opens the exact source stream/topic/message | Bounded cache and single-flight loading; malformed references fail closed; unavailable sources expose a real retry; cross-chat navigation reloads missing catalog metadata | MSG-FWD-006/009–012; physical same-topic distant-source focus and visible-desktop exact-source navigation passed; physical cross-topic navigation pending |
| MSG-A26 | First-unread marker and viewport read | Latest history and the strict earliest incoming-unread lookup run concurrently; an off-page unread loads a bidirectional context window and anchors a non-interactive marker; a real list drag plus at-least-half visibility coalesces rows into one canonical `read_up_to`; the only no-gesture path requires the resumed, complete, fully visible unread tail and loaded newest edge; the newest boundary is encrypted for the exact account/conversation before POST | Exact routes retain priority; malformed/cross-chat/read/own candidates fail closed; no general eager read on load/focus; confirmation is exact-scope/read-true; realtime full/batch events are idempotent and stale pages cannot restore unread; failures and process interruption preserve unread and expose functional Retry/Close without automatic replay; storage failure blocks the mutation | MSG-UNREAD-001–012; focused automated 003–007/010–012, physical sandbox, process-kill and controlled post-request faults pending |
| MSG-A27 | Copy message text | Copies the exact source Markdown of a text/image/file/call message to Android's plain-text clipboard and announces a visible result | Clipboard content is marked sensitive where supported; blank payloads expose no inert action and clipboard failure remains visible with functional Close | MSG-ACT-007 + exact foreground clipboard instrumentation + physical text-message menu/exact-paste/Close acceptance; media-menu/failure sampling pending |
| MSG-A28 | Whole-message, fragment and multi-reply composer | Reply replaces the active quote without losing its answer; Add reply creates an accessible tab; selected rendered text, per-tab answers, reordering, removal and clear-all produce the desktop-compatible ordered `urn:quote` Markdown contract | Aggregate draft data is bounded before encrypted persistence; process death restores exact state; uncached edit sources are fetched once with owner/revision fences; rejected and ambiguous sends use the durable outbox without duplicate automatic retry | MSG-ACT-010–016 + reply/session/parser unit tests + exact Compose instrumentation + physical sandbox send/edit/rotation/process-restoration/fault matrix |
| MSG-A29 | Message multi-selection | Select/Deselect on a canonical server message enters a 48 dp checkbox mode; Forward opens the shared retained picker with up to 32 unique sources in insertion order and Cancel clears the mode | Missing/invalid sources fail closed; rotation retains selection; only authoritative forward confirmation clears it; the desktop-disabled no-op bulk Delete action is hidden instead of rendered inert | MSG-FWD-013–018 + selection/Markdown unit tests + exact Compose instrumentation + physical Android 14 rotation/send/order/cleanup acceptance |
| MSG-A30 | Write/Preview composer modes | Switches between the editable draft and the production renderer for the exact plain or ordered multi-reply outgoing body; selected local attachments stay visible, Send/Save and attachment removal remain real actions, and successful submit returns to Write | Mode switching is network-free and preserves the draft; localized empty/attachments-only states replace fake content; 48 dp selected tab semantics and a polite preview region support TalkBack; presentation state cannot cross conversation/account ownership | MSG-COMP-001–008 + exact Markdown unit tests + locale-independent Compose instrumentation + physical Android 14 rich/empty/light/dark/rotation/no-mutation acceptance |
| MSG-A31 | Markdown formatting toolbar | Applies desktop-compatible bold, italic, strikethrough, quote, bullet/numbered list, inline code, spoiler, code block and link mutations to the exact editor selection/cursor; all localized 48 dp actions are horizontally reachable and refocus the editor | Empty selections receive safe insertion templates; URL selection is deterministic; ViewModel bounds reconcile visible text/selection to the accepted answer; unsupported AI, schedule and snippets controls are absent instead of inert | MSG-COMP-009–016 + mutation/reconciliation unit tests + horizontally scrolled exact Compose instrumentation + physical Android 14 selection/link/preview/rotation/light/dark/no-send acceptance |

## Channel and user information

| ID | Screen / interaction | Handler and effect | Failure/recovery | Acceptance |
| --- | --- | --- | --- | --- |
| INFO-A01 | Channel back | Pops route | No external failure | Navigation suite |
| INFO-A02 | Channel mute/unmute | PUTs real notification mode and updates stream projection | Single in-flight action; inline error | CAT-009 |
| INFO-A03 | User-profile back/close | Pops route | No external failure | Navigation suite |
| INFO-A04 | Profile/Channels tabs | Switches between exact user fields and successfully loaded non-DM channel bindings | Loading, unavailable and truly empty states are distinct; stale real rows survive a failed refresh | UPROF-001–006 |
| INFO-A05 | Shared-channel card | Opens the exact bound channel's real topic list | Direct/provider chats and unclassified legacy external private rows are excluded; no guessed default topic or fabricated destination is used | UPROF-004–006 |
| INFO-A06 | Signed-in profile logout | Shows destructive confirmation, disables further actions, attempts push cleanup, then enters the same serialized removal path as auth rejection | Encrypted drafts/outbox are cleared before credentials/account removal; a cleanup failure keeps the account intact, and another saved account becomes active only after success | AUTH-009/011 + instrumented + physical-device confirm/cancel |
| INFO-A07 | Saved-account row | Atomically switches the active organization/project identity and rebuilds navigation/realtime state | In-flight results are discarded with `ACCOUNT_CHANGED`; token refresh and 401 cleanup are owner-bound and cannot clear another account's local state | AUTH-008, AUTH-010 |
| INFO-A08 | Add organization/account | Suspends the current UI session and opens server discovery without deleting saved accounts | Back or explicit return restores the previous account without reauthentication | AUTH-001, AUTH-010 + physical-device add/return |
| INFO-A09 | Profile identity and account metadata | Renders only authoritative user/server/project data; legacy unknown project names use a labeled shortened identifier | Missing optional data is omitted; no fabricated organization name | Profile suite |
| INFO-A10 | Add channel members | Loads bindings for the exact stream, offers only non-members, and POSTs selected UUIDs | Binding-load failure blocks unsafe candidates; retained request and request-ID completion preserve the restored selection/dialog | CAT-007/014/017/019 |
| INFO-A11 | Remove member / leave channel | Owner may remove another member; any user may remove self using the exact binding UUID | Destructive confirmation, permission-aware visibility, retained single-flight action and inline error | CAT-007/014/019 |
| INFO-A12 | Refresh signed-in profile | Loads the authoritative current-user snapshot and updates account identity only while the same owner remains active | Single-flight progress; network/server/malformed-response errors remain inline and retryable; foreign UUID fails closed | PROF-001–003/016 |
| INFO-A13 | Edit or clear personal status | Sends text/emoji/away through the maintained presence action; explicit-null JSON makes clear a real mutation | Dialog stays open on failure, preserves input, disables duplicate submits and can retry | PROF-004–008/014 |
| INFO-A14 | Change profile photo | Opens Android's document/photo picker, previews the exact selection and uploads only after explicit confirmation | MIME metadata plus signature and 25 MiB bound fail closed; cancel has no remote side effect; upload failure remains retryable | PROF-009–013/015 |
| INFO-A15 | Remove profile photo | Appears only for a resettable uploaded/URL avatar and invokes the maintained reset action after confirmation | Default Gravatar never exposes a misleading reset; single-flight failure preserves the current avatar | PROF-012/014/016 |
| INFO-A16 | Name/timezone editing | Intentionally absent until desktop/backend exposes a real mutation contract | The maintained desktop handler currently resolves a local success without a server write, so mobile exposes no dead controls | PROF-018 |
| INFO-A17 | Refresh another user's profile | Loads user, stream catalog and bindings in parallel for the active owner; requires exactly one canonical target user | A failed refresh keeps the last real content, exposes an actionable error/Retry and never invents offline status or membership | UPROF-001–006/012/013 |
| INFO-A18 | Open personal chat from an internal user profile | Reuses one exact native DM or performs a preflight refresh, one validated create and exact default-topic resolution; external identities expose no misleading native-DM action | Duplicate streams fail closed; failed/ambiguous create is reconciled once from the authoritative catalog; rapid taps and account switches cannot duplicate or misroute | UPROF-007–017 |
| INFO-A19 | Profile avatar | Opens the existing authenticated fullscreen zoom viewer only when the server/public avatar resolves to a valid HTTP(S) image | Initials, malformed URNs and rejected local schemes expose no click action; Close/Back returns without mutation | UPROF-018 |
| INFO-A20 | Copy profile identity value | Writes the exact visible name/email or authoritative user UUID to Android's plain-text clipboard and announces the result | Blank names and unverified route IDs expose no inert action; clipboard failure is visible and feedback auto-dismisses | UPROF-019 |
| INFO-A21 | External-identity badge | Renders a bounded provider label and keeps unsupported native-DM controls absent | Missing provider metadata falls back to an explicit generic badge; overlong labels are truncated | UPROF-017/020 |
| SET-A01 | Theme System/Light/Dark choice | Atomically persists the active account's mode and rebuilds app palette plus system-bar appearance | Failed write preserves the previous mode, clears saving state and shows a dismissible error | SET-001–009 |
| SET-A02 | Standard/Compact density choice | Persists the active account's choice; compact chat rows reduce height/avatar and omit sender/preview while preserving title/time/unread/actions | Failed write preserves the previous layout and restores controls | SET-007–012/019/020 |
| SET-A03 | Personal unread priority switch | Persists the owner-scoped flag and promotes only unread one-to-one DMs among other unread, non-pinned rows | Read rows/group DMs remain on normal order; failed write is visible | SET-007/009/013/014/017–020 |
| SET-A04 | Active channel priority switch | Persists the owner-scoped flag and promotes unmuted unread channels over muted unread channels among non-pinned rows | Read/direct rows remain on normal order; failed write is visible | SET-007/009/015–020 |
| SET-A05 | Notification sound choice | Persists Default/Subtle/Digital/Glass/Pulse/None for the active account, creates the matching stable Android channel and previews audible choices | Android channel failure is dismissible, push retries with Default fallback, None remains visual-only and system channel overrides win | SET-007–009/019–023/025–033 |
| SET-A06 | Clear attachment cache | Shows the exact active-account byte count, requires confirmation and deletes only that account's temporary attachment directory | Empty state is disabled and explicit; failure is dismissible; credentials, drafts, outbox and sibling accounts are outside the deletion boundary | CACHE-001–010 |
| SET-A07 | Share diagnostics | Collects a fresh allowlisted local snapshot and opens the real Android `text/plain` chooser | No identity/content fields exist in the report model; missing share handler produces a dismissible error | DIAG-001–010 |
| SET-A08 | About app and open-source licenses | Opens an offline screen with the exact version name/code/build type plus a searchable catalog generated from the current runtime dependency graph; every component opens details, bundled terms are readable, and Close/Back return safely | Empty search is explicit and clearable; missing license metadata is stated instead of leaving an inert row; generated-resource parsing is an instrumented build gate; no update action is exposed until Android has a verified distribution contract | ABOUT-001–010 |

## External integrations

| ID | Screen / interaction | Handler and effect | Failure/recovery | Acceptance |
| --- | --- | --- | --- | --- |
| EXT-A01 | Profile external-integrations row | Opens the typed external-integration destination | Back stack remains valid; the row is a button, not a placeholder | EXT-001 |
| EXT-A02 | Header Back / Refresh | Pops the route or starts one owner-fenced authoritative account/chat/operation refresh | Refresh is single-flight; network, permission, malformed and owner-change failures are bounded and retryable | EXT-001/006/007/026/032 |
| EXT-A03 | Connect Zulip | Opens the real credential/settings dialog and submits one canonical create request | Blank/invalid data cannot submit; rejected or ambiguous requests keep the form and expose a safe error | EXT-002–005 |
| EXT-A04 | Server, email and API-key fields | Keep bounded input in memory; the key is password-masked, never saveable or prefilled, and enables temporary window screenshot protection | Cancel/success clears the key; rotation/process-death/device checks remain release gates | EXT-003/005/010 |
| EXT-A05 | Account sync settings | Sends selection/history/current-project settings with the current strong revision | No-op settings disable Save; conflicts keep the dialog and offer functional refresh/cancel | EXT-008/009 |
| EXT-A06 | Reconnect account | Submits one replacement server/email/write-only key with the current strong revision | Unsupported/invalid/conflicting responses retain bounded form state and never reveal the prior key | EXT-009–011 |
| EXT-A07 | Disconnect account | Requires confirmation and invokes the exact non-destructive disconnect action | Cancel is side-effect free; failure returns to a usable account card | EXT-011 |
| EXT-A08 | Delete external account | Requires destructive confirmation and deletes the exact account | State is removed only after success/realtime authority; failure preserves the account and retry path | EXT-012–014 |
| EXT-A09 | Chat search / clear | Locally filters the current authoritative catalog by bounded display name and clears the query | Empty result is explicit; no unbounded network request is started | EXT-021/024 |
| EXT-A10 | Open original chat | Launches only a validated same-origin HTTPS URL from the provider snapshot | Unsafe/missing URL exposes no control; missing platform handler shows a dismissible error | EXT-022 |
| EXT-A11 | Select external chat | Adds the exact available chat to the current project | Missing project disables the action; pending, conflict and transport states remain visible and retryable | EXT-016/019 |
| EXT-A12 | Deselect external chat | Requires confirmation and removes only the Workspace projection | Source-provider chat is untouched; failure preserves the authoritative row | EXT-017/019 |
| EXT-A13 | Move projected chat here | Moves the exact selected chat to the active project with its strong revision | Control appears only for a different known project; conflict never overwrites a newer row | EXT-018/019 |
| EXT-A14 | Error/success cards | Refreshes where applicable or dismisses only the visible bounded message | Refresh is hidden by state transition once accepted; no control silently no-ops | EXT-001/004/009/023 |
| EXT-A15 | External capability notice | Hides capability-dependent catalog/actions and renders the backend's bounded sanitized reason | Malformed descriptors fail closed; absent capabilities never create an enabled control | EXT-020/031 |
| EXT-A16 | Open original operation | Launches only the operation URL that matches the configured provider HTTPS origin and port | Unsafe/missing URL has no control; launcher failure is dismissible | EXT-022/033 |
| EXT-A17 | Retry external operation | Invokes retry once only when `can_retry`; ordinary retries send explicit no-risk consent | Failure retains the authoritative operation and exposes refresh/retry | EXT-027–029/032 |
| EXT-A18 | Retry operation with duplicate risk | Requires a destructive confirmation and sends `confirm_duplicate_risk=true` only after acceptance | Cancel/rotation is side-effect free; a stale or no-longer-retryable row cannot submit | EXT-029/034 |
| EXT-A19 | Discard external operation | Requires confirmation and deletes only an operation whose snapshot says `can_discard` | Existing provider effects are explicitly not represented as undone; failure retains the row | EXT-030/032/034 |
| EXT-A20 | Provider-admin discovery and Refresh | Independently reads realm policy, aggregate health and bridge instances; the section exists only when at least one resource is authorized or a real transient failure must be retried | All-forbidden is silent; partial permission keeps authorized data, and malformed/owner-changed responses fail closed | EXT-037/043/047–049 |
| EXT-A21 | Edit provider enabled state and limits | Opens a revision-bound editor and PUTs all validated policy settings once with strong `If-Match` | No changes cannot submit; invalid/overflow input and conflict preserve the dialog and expose refresh/cancel | EXT-038–040/049 |
| EXT-A22 | Replace/remove provider custom CA | Accepts bounded complete PEM certificate blocks or an explicit removal and sends exact `custom_ca_bundle` intent | Existing write-only CA must be explicitly replaced or removed; private-key/foreign/oversized input never reaches the network | EXT-039–041 |
| EXT-A23 | Emergency suspend/resume provider | Confirms realm-wide impact before suspend and invokes resume only from the suspended snapshot | Cancel/rotation is side-effect free; stale policy closes the dialog and permission/failure never displays success | EXT-042/047–049 |
| EXT-A24 | Suspend/resume bridge instance | Shows only actions valid for the current sanitized instance state and invokes the exact UUID once | Revision/state changes close pending confirmation; returned UUID mismatch, denial or transport failure retains the prior row | EXT-044/045/047–049 |
| EXT-A25 | Revoke bridge certificate | Requires an irreversible warning, invokes revoke once and removes every lifecycle action after the authoritative revoked snapshot | Cancel/recreation sends nothing; platform reenrollment remains intentionally outside the Messenger UI | EXT-046/048–050 |

## Platform interactions

| ID | Interaction | Handler and effect | Failure/recovery | Acceptance |
| --- | --- | --- | --- | --- |
| PLAT-A01 | Notification permission prompt | Android permission launcher; denial dialog can open the app notification settings or be dismissed | Denial keeps app usable and both follow-up actions are functional | Push suite |
| PLAT-A02 | Push notification tap | Realm-scoped immutable intent activates the unique matching saved account and opens the exact provider/message route; same-realm ambiguity exposes a real account chooser | Missing/unsafe realm, invalid/stale targets and an unavailable selected account fail closed; connect/dismiss/each account row are functional and pending identifiers survive recreation | PUSH-009–013 |
| PLAT-A03 | Incoming-call decline | Clears exact call surface | No external failure | Calls suite |
| PLAT-A04 | Incoming-call accept | Validates same-host HTTPS room, clears surface, launches Jitsi | Invalid calls are dismissed before rendering | Calls suite |
| PLAT-A05 | HTTPS Workspace stream/topic/message link | Parses the desktop route, selects the exact saved server/project, waits for catalog readiness, and opens/focuses the target | Unsafe routes fail closed; unavailable targets produce an inline recoverable error | NAV-001/003/006 + physical Pixel |
| PLAT-A06 | Desktop `ew://open/...` link | Reuses the existing desktop custom-protocol contract and resolves the project against saved accounts | Ambiguous projects require account choice; unknown projects open server discovery | NAV-002/004/005 + physical Pixel |
| PLAT-A07 | Deep-link saved-account row | Atomically activates that account before opening the target | Missing account stays on the decision screen with an actionable error | NAV-004 |
| PLAT-A08 | Deep-link connect/current-account buttons | Starts real account discovery, optionally prefilled from HTTPS, or dismisses the target and returns to the current chat list | Busy state disables duplicate actions; storage failure remains visible | NAV-005 |
| PLAT-A09 | Configuration change | Recreates the Activity while retaining the single network/realtime/push runtime, account-scoped repository, open dialog and unsent form state | No logout, duplicate socket owner, empty catalog, stale repository, lost selection, or implicit submit | NAV-008/009 + physical portrait/landscape/portrait |
| PLAT-A10 | Fully cold offline exact-topic link | Reads only the active owner's encrypted state for the exact stream/topic and opens bounded persisted route metadata when local work is retained | Unknown, mismatched, route-only, stream-only and message targets fail closed; the dialog exposes its offline load error and retained outbox actions | NAV-010 + MSG-OUT-010 + physical Pixel |
| PLAT-A11 | Android Share Sheet target | Accepts bounded text and distinct granted files, requires an explicit existing channel/topic choice, disambiguates duplicate topic names, copies files into the exact account cache and idempotently merges one stable request UUID into the encrypted ordinary draft | Never auto-sends; Cancel is side-effect free; unsafe/missing grants, size/count/text limits, account switches, double taps/replays and ambiguous storage cancellation fail closed without cross-account leakage | SHARE-001–014 |
| PLAT-A12 | Realtime recovery banner and **Retry now** | After a bounded initial delay or immediately on backoff, reports that live updates are connecting/stale; retry conflates taps and interrupts only the active foreground backoff | Healthy/paused states hide the banner; transport open without protocol `ready` remains connecting; background/account change clears or rejects queued work | RT-001–012 |
| PLAT-A13 | Owner-scoped cold-offline Room snapshot | Before realtime startup, migrates schema-v1 history in place, then decrypts and hydrates bounded streams, topics, latest server messages, exact conversation page modes/anchors/markers, full folders/items, users and stream bindings for only the active credential owner; independent Feed/Starred projections and the Inbox success marker preserve exact content-ready state | AES-GCM associated data rejects moved/replayed/cross-scope rows; invalid relationships/enums/oversized rows are skipped individually; incomplete page metadata fails open to real continuation; newer or authoritatively empty REST/realtime/local projections win; logout clears only that owner and no `local-*` outbox row is duplicated | OFFLINE-001–017 + exact store/migration instrumentation + physical Pixel |

## Intentionally not exposed

The following controls from the old mobile branch are hidden until their real
end-to-end behavior exists:

- Channel settings and channel search shortcuts.
- Media/file/link channel shortcuts.
- Demonstration call rooms and fabricated call history.
- Legacy standalone add-member icon outside the permission-aware channel-member
  flow.
- User-profile call, notification, search, and more shortcuts without a verified
  maintained mobile contract.
- User-group tab with an unavailable placeholder page.
- Mentions/activity shortcut without a real destination.
- Activity mentions/reactions and star/unstar controls while the maintained
  desktop/backend explicitly report those paths unsupported. The supported
  read-only Starred destination is functional and remains visible.
- Hamburger menu without a navigation destination.
- Mail, calendar, services, and calls-list placeholders.

Mock channel memberships, mock shared channels, phone numbers, work profiles,
managers, birthdays, local times, and fallback message previews are prohibited.
