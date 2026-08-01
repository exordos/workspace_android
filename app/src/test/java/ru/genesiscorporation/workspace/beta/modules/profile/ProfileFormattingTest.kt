package ru.genesiscorporation.workspace.beta.modules.profile

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.ChatListDensity
import ru.genesiscorporation.workspace.beta.data.WorkspaceNotificationSound
import ru.genesiscorporation.workspace.beta.data.WorkspaceThemeMode
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind

class ProfileFormattingTest {
    @Test
    fun `cache size formatter is bounded and locale-independent`() {
        assertEquals("0 Б", formatCacheSize(-1))
        assertEquals("0 Б", formatCacheSize(0))
        assertEquals("1023 Б", formatCacheSize(1_023))
        assertEquals("1 КБ", formatCacheSize(1_024))
        assertEquals("1023 КБ", formatCacheSize(1_048_575))
        assertEquals("1,0 МБ", formatCacheSize(1_048_576))
        assertEquals("1,5 МБ", formatCacheSize(1_572_864))
    }

    @Test
    fun `profile status label combines custom text and presence`() {
        assertEquals("В сети", profileStatusLabel(null, "active"))
        assertEquals("Нет на месте", profileStatusLabel(" ", "idle"))
        assertEquals(
            "Reviewing · Не беспокоить",
            profileStatusLabel(" Reviewing ", "do_not_disturb"),
        )
        assertEquals("Статус неизвестен", profileStatusLabel(null, "future"))
    }

    @Test
    fun `figma profile presence presentation covers every known state`() {
        assertEquals(
            ProfilePresencePresentation("В сети", ProfilePresenceTone.ONLINE),
            profilePresencePresentation("ACTIVE"),
        )
        assertEquals(
            ProfilePresencePresentation("Нет на месте", ProfilePresenceTone.AWAY),
            profilePresencePresentation("idle"),
        )
        assertEquals(
            ProfilePresencePresentation("Не беспокоить", ProfilePresenceTone.BUSY),
            profilePresencePresentation("do_not_disturb"),
        )
        assertEquals(
            ProfilePresencePresentation("Не в сети", ProfilePresenceTone.NEUTRAL),
            profilePresencePresentation("offline"),
        )
        assertEquals(
            ProfilePresencePresentation("Статус неизвестен", ProfilePresenceTone.NEUTRAL),
            profilePresencePresentation(null),
        )
    }

    @Test
    fun `figma profile preference labels are localized`() {
        assertEquals(
            listOf("Обычный", "Мягкий", "Цифровой", "Стекло", "Импульс", "Без звука"),
            WorkspaceNotificationSound.entries.map { it.profileSoundLabel() },
        )
        assertEquals(
            listOf("Системная", "Светлая", "Тёмная"),
            WorkspaceThemeMode.entries.map { it.profileThemeLabel() },
        )
        assertEquals(
            listOf("Стандартная", "Компактная"),
            ChatListDensity.entries.map { it.profileDensityLabel() },
        )
    }

    @Test
    fun `avatar reset is offered only for replaceable avatar sources`() {
        assertEquals(true, isResettableAvatar("urn:image:11111111-2222-3333-4444-555555555555"))
        assertEquals(true, isResettableAvatar("urn:url:https://example.com/avatar.png"))
        assertEquals(false, isResettableAvatar("urn:gravatar:0123456789abcdef"))
        assertEquals(false, isResettableAvatar(""))
    }

    @Test
    fun `profile errors remain actionable without leaking server details`() {
        assertEquals(
            "Фото профиля должно быть не больше 25 МБ",
            profileErrorMessage(
                "Не удалось обновить фото профиля",
                ApiError(
                    errorMessage = "internal detail",
                    code = "AVATAR_TOO_LARGE",
                    kind = ApiErrorKind.VALIDATION,
                ),
            ),
        )
        assertEquals(
            "Не удалось обновить статус: нет подключения к сети",
            profileErrorMessage(
                "Не удалось обновить статус",
                ApiError(
                    errorMessage = "host",
                    code = "NETWORK",
                    kind = ApiErrorKind.NETWORK,
                ),
            ),
        )
    }

    @Test
    fun `profile snapshots must belong to the active user`() {
        val expected = "11111111-2222-3333-4444-555555555555"

        assertEquals(true, profileBelongsToUser(expected.uppercase(), expected))
        assertEquals(
            false,
            profileBelongsToUser(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                expected,
            ),
        )
        assertEquals(false, profileBelongsToUser("not-a-uuid", expected))
        assertEquals(false, profileBelongsToUser(expected, "not-a-uuid"))
    }
}
