# Implemented features

This catalog describes flows available in the code. Google Play release status
is tracked separately. Source paths refer to the main Kotlin package
`ru.genesiscorporation.workspace.beta`.

| Area | Implemented behavior | Main code |
| --- | --- | --- |
| Organizations and login | Add a Workspace address, login/password, OTP, project selection, multiple connections, switching, relogin, and logout | `modules/chooseserver`, `login`, `otp`, `projects`, `profile`; `data/UserDataRepository.kt` |
| Session refresh | Refresh on 401, coalesce concurrent refreshes, reject late responses from another login | `data/remote/WorkspaceAPIClient.kt`, `ProtectedRequestAuthentication.kt` |
| Transport security | HTTPS/WSS for remote API, realtime and avatars, including redirects; exact loopback exceptions for local development | `data/remote/WorkspaceHttpClient.kt` |
| Conversation catalog | Channels and direct conversations, name/last message, unread badges, search, unread-first ordering | `modules/chatchannels`, `creationbase` |
| Channels and members | Create channels/direct conversations, select and add members, view channel information; protected settings and roles | `modules/createstream`, `createdirectstream`, `adduserstostream`, `streaminfo` |
| Topics | List, create, open conversations, mark done, set notification modes | `modules/topics`, `ui/CreateTopic.kt`, notification/done DTOs |
| Folders | Create folders, manage contents, delete folders, update counters and the selected tab through realtime events | `modules/addfolder`, `foldersettings`; `data/EventsRepository.kt` |
| Regular conversations | History, Markdown, sending and editing, optimistic sends reconciled with HTTP and events | `modules/chatdialog/ChatDialogViewModel.kt`, `ChatDialogScreen.kt` |
| Quotes and mentions | Full/selected quotes, source loading with retry, author snapshots, composer suggestions, atomic mention editing | `MessageQuoteResolver.kt`, `MarkdownPayloadParser` in `ChatDialogViewModel.kt`, `ComposerMention.kt`, `SendMessageView.kt` |
| Reactions | Display emoji/counts, add and remove reactions, handle aliases | `EmojiReactionsView.kt`, reaction DTOs, `ChatDialogScreen.kt` |
| Selection and deletion | Select multiple messages, confirm deletion, retain failed items for retry | `MessageSelection.kt`, `MessageDeletion.kt`, and related Compose controls |
| Forwarding | Forward to another Workspace conversation with materialized snapshots and attachments; share externally through the Android share sheet and temporary URI grants | `MessageForwarding.kt`, `ForwardContentPreparation.kt`, `ExternalMessageShare.kt`, `ForwardToWorkspaceActivity.kt` |
| Photos and files | Picker/camera, multipart upload, image preview/zoom, download and file sharing through FileProvider | `SendMessageView.kt`, `ImageMessageView.kt`, `AttachmentStorage` in `ChatDialogViewModel.kt` |
| My activity | Inbox based on active unread and topic overrides, navigation to the first unread message/mention, mentions list | `modules/home`, `homeinbounds`, `homementions` |
| Server drafts | Load the server list, open a regular conversation with draft text, delete drafts, remove a used draft after sending | `modules/homedrafts`, `ChatDialogViewModel.kt` |
| Profile and appearance | Profile, avatar, presence, connection and UI settings, navigation between profile screens | `modules/profile`, `ownusersettings`, `visualsettings`; `ui/Avatar*` |
| Links | Web links to the selected channel/contact, sharing through the Android chooser | `modules/share/WorkspaceShare.kt` |
| Events | WebSocket replay, backoff, HTTP probing, expired cursor recovery, open-chat history recovery | `data/EventsRepository.kt`, `MessageHistorySnapshot.kt` |
| Push | Firebase service, Android notification channel, navigation through notification intents | `data/MyFirebaseMessagingService.kt`, `MainActivity.kt` |
| Calls | Create a link/room, launch the Jitsi SDK from a chat/channel information screen, send a call-link message | `CallMessageView.kt`, `ChatDialogScreen.kt`, `streaminfo` |

## Encrypted chats

The client supports encrypted stream creation, `e2ee` ciphertext, key requests,
manual grants and rejections, owner rotation, local key-generation management,
and requests for historical key generations from a locked message's context menu.
Messages support regular chat actions through a separate plaintext projection:
quoting, editing, selection, forwarding, deletion, and reactions. List previews
are decrypted only with an available local key and a verified context. A device
without the key shows a locked state with a key-request path. Rotation and
uncertain HTTP results preserve the identity of unfinished operations.
See the [encryption contract](encrypted-chats.md) for details.

## Boundaries and unfinished areas

- `CalendarScreen` and `MailScreen` display an in-development placeholder; these
  screens do not implement a working calendar or mail client.
- `CallsScreen` is a placeholder for a separate section. Jitsi integration within
  a conversation does not provide call history or a complete VoIP implementation.
- `HomeFlow` declares more routes than `HomeScreen` exposes. The user menu
  includes Inbox, mentions, and drafts. Declaring `HomeFavorites`, `HomeReactions`,
  or `HomeFeed` does not establish an implemented feature.
- `CreateDraftRequest` and `UpdateDraftRequest` exist, but the composer does not
  call them. Automatic creation/saving of new drafts from Android is not
  implemented.
- Encrypted conversations use regular server files for attachments: Markdown
  links are encrypted, but file bytes, names, and metadata are not.
- Automatic key grants and secret-key transfer through backup are excluded.
  After migrating to another device, request the key again.
- Full offline history and a general durable action queue are not implemented.
  Push/Jitsi require their own infrastructure and real delivery/media checks;
  loopback tests do not establish those results.
- Restore Credentials API is not integrated into the current code. Requirements
  and release applicability are checked separately in the [quality plan](testing.md).
