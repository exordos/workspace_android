package ru.genesiscorporation.workspace.beta.modules.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.modules.chatchannels.FOLDER_TITLE_MAX_LENGTH
import ru.genesiscorporation.workspace.beta.modules.chatchannels.folderDraftError
import ru.genesiscorporation.workspace.beta.modules.chatchannels.validateFolderDraft

class FolderDisplayFormattingTest {
    @Test
    fun `folder draft trims title and removes duplicate streams`() {
        val first = stream("first", "Песочница")
        val draft = validateFolderDraft(
            name = "  Проверка  ",
            selectedStreams = listOf(first, first, stream("second", "Команда")),
        )

        assertEquals("Проверка", draft?.name)
        assertEquals(listOf("first", "second"), draft?.streams?.map(Stream::uuid))
    }

    @Test
    fun `folder draft enforces the server title contract`() {
        assertNull(validateFolderDraft("   ", emptyList()))
        assertNull(
            validateFolderDraft(
                "x".repeat(FOLDER_TITLE_MAX_LENGTH + 1),
                emptyList(),
            ),
        )
        assertEquals(
            "Введите название папки",
            folderDraftError(" "),
        )
        assertEquals(
            "Название папки должно быть не длиннее 64 символов",
            folderDraftError("x".repeat(65)),
        )
    }

    @Test
    fun `folder chat fallback copy is stable`() {
        assertEquals("Описание", folderStreamPreview(stream("one", "Чат", "Описание")))
        assertEquals(
            "Сообщений пока нет",
            folderStreamPreview(stream("two", "Пустой")),
        )
    }

    @Test
    fun `system folders use the same Russian labels as the desktop UI`() {
        val all = folder(
            title = "All chats",
            systemType = "all",
            uuid = "00000000-0000-0000-0000-000000000000",
        )
        val personal = folder(
            title = "Personal",
            systemType = "all",
            uuid = "00000000-0000-0000-0000-000000000001",
        )
        val channels = folder(
            title = "Channels",
            systemType = "all",
            uuid = "00000000-0000-0000-0000-000000000002",
        )
        val namedLikeSystemFolder = folder("Personal", "created")
        val user = folder("Папка", "created")

        assertEquals("Все чаты", folderDisplayTitle(all))
        assertEquals("Личные", folderDisplayTitle(personal))
        assertEquals("Каналы", folderDisplayTitle(channels))
        assertEquals("Personal", folderDisplayTitle(namedLikeSystemFolder))
        assertEquals("Папка", folderDisplayTitle(user))
    }

    private fun stream(
        uuid: String,
        name: String,
        description: String = "",
    ) = Stream(
        uuid = uuid,
        unreadCount = 0,
        updatedAt = "2026-08-01T00:00:00Z",
        name = name,
        description = description,
        isPrivate = false,
    )

    private fun folder(
        title: String,
        systemType: String?,
        uuid: String = title,
    ) = FolderResponseData(
        uuid = uuid,
        title = title,
        unreadCount = 0,
        systemType = systemType,
        creationDate = "2026-08-01T00:00:00Z",
    )
}
