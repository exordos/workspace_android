package ru.genesiscorporation.workspace.beta.modules.chatuserinfo

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.dto.ProviderReference
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class ChatUserInfoViewModelTest {
    @Test
    fun `shared channels follow selected user bindings`() {
        val streams = listOf(
            stream(SHARED_STREAM),
            stream(OTHER_STREAM),
            stream(
                uuid = DIRECT_STREAM,
                private = true,
                directUserUuid = SELECTED_USER,
            ),
        )
        val bindings = listOf(
            binding(SHARED_STREAM, SELECTED_USER),
            binding(OTHER_STREAM, OTHER_USER),
            binding(DIRECT_STREAM, SELECTED_USER),
        )

        val result = resolveSharedChannels(SELECTED_USER, streams, bindings)

        assertEquals(listOf(SHARED_STREAM), result.map(Stream::uuid))
    }

    @Test
    fun `channels are not fabricated when membership data is absent`() {
        val streams = listOf(
            stream(SHARED_STREAM),
            stream(OTHER_STREAM, private = true),
        )

        val result = resolveSharedChannels(
            SELECTED_USER,
            streams,
            emptyList(),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `channels stay empty while memberships load or identifiers are invalid`() {
        assertTrue(
            resolveSharedChannels(
                userUuid = SELECTED_USER,
                streams = listOf(stream(SHARED_STREAM)),
                bindings = null,
            ).isEmpty(),
        )
        assertTrue(
            resolveSharedChannels(
                userUuid = "not-a-uuid",
                streams = listOf(stream(SHARED_STREAM)),
                bindings = listOf(binding(SHARED_STREAM, SELECTED_USER)),
            ).isEmpty(),
        )
    }

    @Test
    fun `known direct channel stays excluded when refreshed catalog loses markers`() {
        val previouslyClassifiedDirect = stream(
            uuid = DIRECT_STREAM,
            private = true,
            directUserUuid = SELECTED_USER,
        )
        val degradedRefresh = previouslyClassifiedDirect.copy(
            directUserUuid = null,
        )
        val knownDirect = resolveKnownDirectStreamUuids(
            previousStreams = listOf(previouslyClassifiedDirect),
            refreshedStreams = listOf(degradedRefresh, stream(SHARED_STREAM)),
            previouslyKnownDirectStreamUuids = emptySet(),
        )

        assertEquals(setOf(DIRECT_STREAM), knownDirect)
        assertEquals(
            listOf(SHARED_STREAM),
            resolveSharedChannels(
                userUuid = SELECTED_USER,
                streams = listOf(degradedRefresh, stream(SHARED_STREAM)),
                bindings = listOf(
                    binding(DIRECT_STREAM, SELECTED_USER),
                    binding(SHARED_STREAM, SELECTED_USER),
                ),
                knownDirectStreamUuids = knownDirect,
            ).map(Stream::uuid),
        )
    }

    @Test
    fun `ambiguous legacy external private stream is not presented as a shared channel`() {
        val ambiguousLegacyStream = stream(
            uuid = OTHER_STREAM,
            private = true,
        ).copy(
            sourceName = "zulip",
            inviteOnly = true,
        )
        val confirmedExternalChannel = stream(
            uuid = SHARED_STREAM,
            private = true,
        ).copy(
            sourceName = "zulip",
            inviteOnly = true,
            provider = ProviderReference(
                kind = "zulip",
                externalId = "channel:42",
            ),
        )

        assertEquals(
            listOf(SHARED_STREAM),
            resolveSharedChannels(
                userUuid = SELECTED_USER,
                streams = listOf(ambiguousLegacyStream, confirmedExternalChannel),
                bindings = listOf(
                    binding(OTHER_STREAM, SELECTED_USER),
                    binding(SHARED_STREAM, SELECTED_USER),
                ),
            ).map(Stream::uuid),
        )
    }

    @Test
    fun `target profile requires one canonical matching user`() {
        val expected = user(SELECTED_USER)

        assertEquals(
            expected,
            resolveTargetUser(
                SELECTED_USER.uppercase(),
                listOf(expected, user(OTHER_USER)),
            ),
        )
        assertEquals(
            null,
            resolveTargetUser(
                SELECTED_USER,
                listOf(expected, expected.copy(username = "duplicate")),
            ),
        )
        assertEquals(
            null,
            resolveTargetUser("not-a-uuid", listOf(expected)),
        )
    }

    @Test
    fun `direct chat action requires a different internal user`() {
        assertTrue(
            canOpenDirectChatWith(
                profile = user(SELECTED_USER),
                currentUserUuid = OTHER_USER,
            ),
        )
        assertTrue(
            !canOpenDirectChatWith(
                profile = user(SELECTED_USER, identityKind = "external"),
                currentUserUuid = OTHER_USER,
            ),
        )
        assertTrue(
            !canOpenDirectChatWith(
                profile = user(SELECTED_USER),
                currentUserUuid = SELECTED_USER.uppercase(),
            ),
        )
        assertTrue(
            !canOpenDirectChatWith(
                profile = user(SELECTED_USER),
                currentUserUuid = "invalid",
            ),
        )
    }

    @Test
    fun `direct chat candidate is exact native private stream`() {
        val expected = stream(
            uuid = DIRECT_STREAM,
            private = true,
            directUserUuid = SELECTED_USER.uppercase(),
        )
        val result = resolveDirectChatCandidate(
            targetUserUuid = SELECTED_USER,
            streams = listOf(
                stream(SHARED_STREAM, directUserUuid = SELECTED_USER),
                expected,
                stream(
                    uuid = OTHER_STREAM,
                    private = true,
                    directUserUuid = OTHER_USER,
                ),
            ),
        )

        assertEquals(expected, (result as DirectChatCandidate.Found).stream)
    }

    @Test
    fun `direct chat candidate fails closed for duplicates`() {
        val result = resolveDirectChatCandidate(
            targetUserUuid = SELECTED_USER,
            streams = listOf(
                stream(
                    uuid = DIRECT_STREAM,
                    private = true,
                    directUserUuid = SELECTED_USER,
                ),
                stream(
                    uuid = OTHER_STREAM,
                    private = true,
                    directUserUuid = SELECTED_USER,
                ),
            ),
        )

        assertEquals(DirectChatCandidate.Ambiguous, result)
    }

    @Test
    fun `direct chat topic prefers response default then one server default`() {
        val defaultTopic = topic(DEFAULT_TOPIC, isDefault = true)
        val otherTopic = topic(OTHER_TOPIC, isDefault = false)

        assertEquals(
            OTHER_TOPIC,
            resolveDirectChatTopicUuid(
                stream(
                    uuid = DIRECT_STREAM,
                    private = true,
                    directUserUuid = SELECTED_USER,
                    defaultTopicUuid = OTHER_TOPIC.uppercase(),
                ),
                listOf(defaultTopic, otherTopic),
            ),
        )
        assertEquals(
            DEFAULT_TOPIC,
            resolveDirectChatTopicUuid(
                stream(
                    uuid = DIRECT_STREAM,
                    private = true,
                    directUserUuid = SELECTED_USER,
                ),
                listOf(defaultTopic, otherTopic),
            ),
        )
    }

    @Test
    fun `profile errors are actionable and hide server text`() {
        assertEquals(
            "Не удалось открыть личный чат: нет подключения к сети",
            userProfileErrorMessage(
                "Не удалось открыть личный чат",
                ApiError(
                    errorMessage = "internal host detail",
                    code = "NETWORK",
                    kind = ApiErrorKind.NETWORK,
                ),
            ),
        )
    }

    @Test
    fun `presence does not invent an offline state before profile loads`() {
        assertEquals(null, presenceLabel(null))
        assertEquals(null, presenceLabel("future"))
        assertEquals("В сети", presenceLabel("active"))
        assertEquals("Не беспокоить", presenceLabel("do_not_disturb"))
    }

    @Test
    fun `external identity badge is explicit and provider label is bounded`() {
        assertEquals(null, externalIdentityLabel(user(SELECTED_USER)))
        assertEquals(
            "Внешний профиль",
            externalIdentityLabel(
                user(SELECTED_USER, identityKind = "external"),
            ),
        )
        assertEquals(
            "Внешний профиль · zulip",
            externalIdentityLabel(
                user(
                    uuid = SELECTED_USER,
                    identityKind = "EXTERNAL",
                    provider = ProviderReference(kind = "  zulip  "),
                ),
            ),
        )
        assertEquals(
            "Внешний профиль · ${"p".repeat(32)}",
            externalIdentityLabel(
                user(
                    uuid = SELECTED_USER,
                    identityKind = "external",
                    provider = ProviderReference(kind = "p".repeat(40)),
                ),
            ),
        )
    }

    @Test
    fun `user provider identity is decoded from the maintained response contract`() {
        val profile = Json.decodeFromString<UserResponseData>(
            """
            {
              "username": "external-user",
              "uuid": "$SELECTED_USER",
              "status": "active",
              "avatar": "",
              "identity_kind": "external",
              "provider": {
                "kind": "zulip",
                "account_uuid": "$OTHER_USER",
                "external_id": "user:42"
              }
            }
            """.trimIndent(),
        )

        assertEquals("external", profile.identityKind)
        assertEquals("zulip", profile.provider?.kind)
        assertEquals("user:42", profile.provider?.externalId)
    }

    @Test
    fun `profile fields copy only useful and authoritative values`() {
        val loadedFields = buildProfileFields(
            user = user(SELECTED_USER).copy(
                email = "selected@example.com",
                statusText = "Фокус",
            ),
            targetUserId = SELECTED_USER.uppercase(),
            fallbackEmail = "stale@example.com",
        )

        assertEquals(
            listOf("Статус", "Email", "ID пользователя"),
            loadedFields.map(ProfileField::title),
        )
        assertEquals(
            listOf(false, true, true),
            loadedFields.map(ProfileField::copyable),
        )
        assertEquals("selected@example.com", loadedFields[1].value)
        assertEquals(SELECTED_USER, loadedFields[2].value)

        val unloadedFields = buildProfileFields(
            user = null,
            targetUserId = "invalid-route-id",
            fallbackEmail = "fallback@example.com",
        )
        assertEquals(listOf(true, false), unloadedFields.map(ProfileField::copyable))
    }

    private fun stream(
        uuid: String,
        private: Boolean = false,
        directUserUuid: String? = null,
        defaultTopicUuid: String? = null,
    ) = Stream(
        uuid = uuid,
        unreadCount = 0,
        updatedAt = "2026-07-26T00:00:00Z",
        name = uuid,
        isPrivate = private,
        color = 0xFF8138,
        directUserUuid = directUserUuid,
        defaultTopicUuid = defaultTopicUuid,
    )

    private fun binding(
        streamUuid: String,
        userUuid: String,
    ) = StreamBindingResponseData(
        uuid = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
        streamUuid = streamUuid,
        userUuid = userUuid,
        whoUuid = OTHER_USER,
    )

    private fun user(
        uuid: String,
        identityKind: String? = null,
        provider: ProviderReference? = null,
    ) = UserResponseData(
        username = uuid,
        uuid = uuid,
        status = "active",
        avatar = "",
        identityKind = identityKind,
        provider = provider,
    )

    private fun topic(
        uuid: String,
        isDefault: Boolean,
    ) = TopicsResponseData(
        uuid = uuid,
        name = uuid,
        streamUuid = DIRECT_STREAM,
        updatedAt = "2026-07-30T00:00:00Z",
        unreadCount = 0,
        isDone = false,
        isDefault = isDefault,
    )

    private companion object {
        const val SELECTED_USER = "11111111-2222-4333-8444-555555555555"
        const val OTHER_USER = "22222222-3333-4444-8555-666666666666"
        const val SHARED_STREAM = "33333333-4444-4555-8666-777777777777"
        const val OTHER_STREAM = "44444444-5555-4666-8777-888888888888"
        const val DIRECT_STREAM = "55555555-6666-4777-8888-999999999999"
        const val DEFAULT_TOPIC = "66666666-7777-4888-8999-aaaaaaaaaaaa"
        const val OTHER_TOPIC = "77777777-8888-4999-8aaa-bbbbbbbbbbbb"
    }
}
