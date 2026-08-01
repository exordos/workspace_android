package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class ChatCreationFlowTest {
    @Test
    fun channelBrowserKeepsOnlyVisibleChannelsForSelectedSubscriptionFilter() {
        val subscribed = stream(uuid = "subscribed", name = "Alpha")
        val unsubscribed = stream(uuid = "unsubscribed", name = "Beta")
        val direct = stream(
            uuid = "direct",
            name = "Direct",
            isPrivate = true,
            directUserUuid = "user-2",
        )
        val archived = stream(uuid = "archived", name = "Archived", archived = true)
        val streams = listOf(unsubscribed, direct, archived, subscribed)

        assertEquals(
            listOf("subscribed"),
            channelBrowseChannels(
                streams,
                setOf("subscribed"),
                ChannelBrowseFilter.SUBSCRIBED,
            ).map(Stream::uuid),
        )
        assertEquals(
            listOf("unsubscribed"),
            channelBrowseChannels(
                streams,
                setOf("subscribed"),
                ChannelBrowseFilter.UNSUBSCRIBED,
            ).map(Stream::uuid),
        )
        assertEquals(
            listOf("subscribed", "unsubscribed"),
            channelBrowseChannels(
                streams,
                setOf("subscribed"),
                ChannelBrowseFilter.ALL,
            ).map(Stream::uuid),
        )
    }

    @Test
    fun directPickerSearchesNamesUsernameAndEmailAndSortsDisplayNames() {
        val users = listOf(
            user(
                uuid = "beta",
                username = "runner",
                firstName = "Бета",
                email = "team@example.test",
            ),
            user(
                uuid = "alpha",
                username = "alpha-login",
                firstName = "Альфа",
                email = "alpha@example.test",
            ),
        )

        assertEquals(
            listOf("alpha", "beta"),
            directChatCandidates(users, "").map(UserResponseData::uuid),
        )
        assertEquals(
            listOf("alpha"),
            directChatCandidates(users, "alpha-login").map(UserResponseData::uuid),
        )
        assertEquals(
            listOf("beta"),
            directChatCandidates(users, "TEAM@EXAMPLE.TEST").map(UserResponseData::uuid),
        )
    }

    @Test
    fun streamPickerExcludesCurrentUserAndSearchesEveryVisibleIdentityField() {
        val users = listOf(
            user(
                uuid = "current",
                username = "cassi",
                firstName = "Кассандра",
                email = "cassi@example.test",
            ),
            user(
                uuid = "beta",
                username = "runner",
                firstName = "Бета",
                email = "team@example.test",
            ),
            user(
                uuid = "alpha",
                username = "alpha-login",
                firstName = "Альфа",
                email = "alpha@example.test",
            ),
        )

        assertEquals(
            listOf("alpha", "beta"),
            streamCreationCandidates(users, "current", "").map(UserResponseData::uuid),
        )
        assertEquals(
            listOf("alpha"),
            streamCreationCandidates(users, "current", "alpha-login")
                .map(UserResponseData::uuid),
        )
        assertEquals(
            listOf("beta"),
            streamCreationCandidates(users, "current", "TEAM@EXAMPLE.TEST")
                .map(UserResponseData::uuid),
        )
    }

    @Test
    fun streamInputRequiresTrimmedNameAndKeepsOnlySelectableUniqueMembers() {
        val users = listOf(
            user("current", "cassi", "Кассандра", "cassi@example.test"),
            user("member", "member", "Участник", "member@example.test"),
        )

        assertEquals(
            null,
            buildCreateStreamInput(
                name = "   ",
                selectedUserUuids = listOf("member"),
                users = users,
                currentUserUuid = "current",
            ),
        )

        val input = buildCreateStreamInput(
            name = "  Команда CASSI  ",
            selectedUserUuids = listOf("member", "member", "current", "missing", " "),
            users = users,
            currentUserUuid = "current",
        )

        assertEquals("Команда CASSI", input?.name)
        assertEquals(setOf("member"), input?.memberUserUuids)
        assertEquals("", input?.description)
        assertEquals(false, input?.inviteOnly)
        assertEquals(false, input?.announce)
    }

    private fun stream(
        uuid: String,
        name: String,
        isPrivate: Boolean = false,
        directUserUuid: String? = null,
        archived: Boolean = false,
    ) = Stream(
        uuid = uuid,
        unreadCount = 0,
        updatedAt = "2026-08-01T00:00:00Z",
        name = name,
        isPrivate = isPrivate,
        isArchived = archived,
        directUserUuid = directUserUuid,
    )

    private fun user(
        uuid: String,
        username: String,
        firstName: String,
        email: String,
    ) = UserResponseData(
        uuid = uuid,
        username = username,
        firstName = firstName,
        email = email,
        status = "active",
        avatar = "",
    )
}
