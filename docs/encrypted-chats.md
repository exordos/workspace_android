# Encrypted chats on Android

Android uses the same message and key-transfer wire format as Workspace web and desktop.
The normal mobile conversation remains the presentation layer: decrypted Markdown supports
replies, editing, selection, forwarding, deletion, reactions, images and files.

## Accepted attachment limitation

File and photo bytes, names and file metadata use the ordinary Workspace upload/download
endpoints. Only their Markdown references are encrypted inside the message body. They are
not end-to-end encrypted attachments. This is an explicitly accepted product limitation.

## Message contract

Streams declare `encryption` and `current_encryption_key_uuid`. Encryption cannot be changed
on an existing stream. A new protected stream stages its initial 32-byte group key locally
before creation, then binds it to the confirmed stream. Retries retain the creation identity.

Messages use six backend payload kinds: `markdown`, `e2ee`, `key_request`, `key_grant`,
`key_reject`, and `key_announced`. Service messages use the default topic; their requests
are collected across topics. A generation is identified by `key_uuid`.

- `e2ee.content` is standard canonical Base64 of salt (32 bytes), ciphertext and tag (16 bytes).
  HKDF-SHA256 derives a per-message key from the group key and random salt. Its info is the
  UTF-8 JSON array `["workspace-chat-crypto-v1","message",streamUuid,keyUuid]`. ChaCha20-Poly1305
  uses a zero 12-byte nonce with that unique derived key; AAD is info followed by salt.
- Each `key_request` creates a fresh X25519 request pair. The public half, device identity,
  display name and requested generation travel in the authenticated service payload.
- `key_grant` uses RFC 9180 HPKE base mode (X25519/HKDF-SHA256/ChaCha20-Poly1305).
  The envelope is `WSCG`, version 1, encapsulated key (32 bytes), encrypted group key and tag.
  Context binds stream, generation, request message UUID and both device identifiers.
- Grants and rejections must match a pending request and come from a currently authorized
  owner or administrator. An accepted receipt atomically records completion and removes
  the request private key. Out-of-order history/realtime delivery is supported.
- Only the owner can announce a new generation. Failed or uncertain rotation retains the
  staged material and keeps the previously available sending generation usable.

The API's public message placement UUID is UUIDv5(topic UUID, canonical message UUID).
Encrypted transport verifies the exact receipt and resolves an uncertain POST by reading
that placement. Retries of the same send intent reuse the prepared ciphertext.

## Local security and lifecycle

Group keys and pending request private keys use a separate EncryptedSharedPreferences store
protected by an Android Keystore key. Addresses include normalized server, project, user,
device, stream and generation. Secret material is excluded from Android cloud backup and
device transfer. Device migration uses the authenticated key-request flow.

Repositories, history caches and send acknowledgements retain ciphertext. Plaintext copies
exist only in mounted presentation (including visible chat-list previews), quote and composer state. Logout, account/project/server
changes and membership revocation invalidate a runtime. Removing a local key invalidates
its presentations and related quote/edit/selection state across open panels. Deletion
revisions prevent delayed grants from restoring material the user explicitly removed. Stream-removal
revisions also fence in-flight presentation returns and publication before flow observers execute.

Both profile logout and leaving an expired organization use the connection-removal operation.
A cleanup-only owner association from a successful `/me` is committed to the existing encrypted
token store; it never authorizes key access. Explicit removal purges that owner after a process
restart too, while expiry, refresh and organization switching preserve keys.

The key screen displays generation identifiers and the actual creation date from the matching
`key_announced` service event (or an unavailable marker), never key bytes. Removing
keys is local; it does not delete server messages. New and historical generations can be
requested separately. Manual bulk issuance processes current pending requests. Automatic key issuance is explicitly
excluded from this implementation. A locked message offers a context-menu request for that
message's exact generation; it must not substitute the current generation.

Unavailable message bodies use two blurred decorative rows inside the ordinary message bubble,
matching the web client. The shared Android shell keeps the author, avatar, time, colors
and incoming/outgoing alignment; only the body is replaced. Placeholder width is capped
at 60% of the viewport (480 dp maximum), bounded by the space inside the bubble.
Android 12 and newer use the platform blur; Android 11 uses a soft geometry fallback.
Only geometry is drawn: neither ciphertext nor hidden plaintext enters the placeholder.
Its accessible label remains "Encrypted message". Invalid ciphertext retains a readable
decryption error, and the context menu still requests the message's exact key generation.
Once the local key becomes available, the regular decrypted message replaces the placeholder.

List previews read only existing local keys for the exact message generation. Missing keys,
unconfirmed membership or a changed credential snapshot keep an opaque placeholder. A successful
existing `/me` load supplies the in-memory owner proof; no preview-specific network request is made.
The projection clears on its own account/configuration or visible local-key changes and never
updates the wire repository. Notifications for another organization, device or invisible stream
do not cancel an otherwise unchanged projection.

Protected mutations pin the authentication captured by their mounted account context,
including recovery reads. A delayed 401 cannot replay an old operation with a replacement
account token. Issuer decisions retain their canonical identity and encrypted envelope
across uncertain writes and restarts; the first staged grant or rejection wins.

## Verification

Tests live under `app/tests/unit`, `app/tests/instrumented` and `app/tests/interop/crypto`.
The interop fixtures are synthetic, including published RFC test material, and contain no
live credentials. `verify-web-interop.mjs` runs the unchanged web crypto provider against
Android-produced vectors. Device tests exercise the actual JCA provider and Keystore,
complete mobile chat interactions and account-scoped runtime flows against loopback fixtures.
`EncryptedMessagePlaceholderUiTest` checks the rendered soft edges in both themes and
narrow windows, the accessible label, error state and long-press key request.
`EncryptedConversationUiTest` also checks the complete locked bubble (author, time and
incoming/outgoing placement) and its locked-to-readable transition using real
local key storage, scoped decryption and the production chat screen.

A debug suite does not establish release/R8 correctness or production Android vitals.
Release checks and stand acceptance are recorded separately in the task's artifact report.
