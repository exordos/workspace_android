package ru.genesiscorporation.workspace.beta.modules.profile

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderItem
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class FolderDisplayScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun folderListMatchesTheFigmaHierarchyAndOpensRealRows() {
        var selected: String? = null
        var createClicks = 0
        var backClicks = 0
        var closeClicks = 0

        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                FolderDisplayList(
                    folders = listOf(
                        folder(
                            "00000000-0000-0000-0000-000000000000",
                            "All chats",
                            "all",
                            0,
                        ),
                        folder("custom", "Песочница", "created", 2),
                    ),
                    streamCount = 22,
                    loading = false,
                    message = null,
                    onDismissMessage = {},
                    onBack = { backClicks += 1 },
                    onClose = { closeClicks += 1 },
                    onCreateFolder = { createClicks += 1 },
                    onFolderSelected = { selected = it.uuid },
                )
            }
        }

        composeRule.onNodeWithTag(FOLDER_DISPLAY_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Отображение папок").assertIsDisplayed()
        composeRule.onNodeWithText("МОИ ПАПКИ").assertIsDisplayed()
        composeRule.onNodeWithText("Все чаты (22)").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Назад к профилю").performClick()
        composeRule.onNodeWithContentDescription("Закрыть отображение папок").performClick()
        composeRule.onNodeWithText("Песочница (2)").performClick()
        composeRule.onNodeWithTag(FOLDER_CREATE_OPEN_TAG).performClick()
        composeRule.runOnIdle {
            assertEquals("custom", selected)
            assertEquals(1, createClicks)
            assertEquals(1, backClicks)
            assertEquals(1, closeClicks)
        }
    }

    @Test
    fun createFormSelectsChatsAndSubmitsOnlyAValidServerTitle() {
        var submission: Pair<String, Set<String>>? = null

        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                FolderCreateForm(
                    streams = listOf(
                        stream("sandbox", "песочница", 4),
                        stream("team", "Команда", 0),
                    ),
                    activeAccount = null,
                    operationInProgress = false,
                    error = null,
                    onBack = {},
                    onClose = {},
                    onDismissError = {},
                    onCreate = { name, selected ->
                        submission = name to selected
                    },
                    streamAvatar = { Spacer(Modifier.size(40.dp)) },
                )
            }
        }

        composeRule.onNodeWithTag(FOLDER_CREATE_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Добавить чаты").assertIsDisplayed()
        composeRule.onNodeWithTag(FOLDER_CREATE_SUBMIT_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(FOLDER_NAME_FIELD_TAG)
            .performTextInput("cassi-check")
        composeRule.onNodeWithTag("${FOLDER_CHAT_ROW_TAG_PREFIX}sandbox")
            .assertTextContains("песочница")
            .performClick()
        composeRule.onNodeWithTag(FOLDER_CREATE_SUBMIT_TAG)
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals("cassi-check", submission?.first)
            assertEquals(setOf("sandbox"), submission?.second)
        }
    }

    @Test
    fun createFormSearchAndHeaderActionsRemainUsable() {
        var backClicks = 0
        var closeClicks = 0

        composeRule.setContent {
            WokspaceTheme(darkTheme = false) {
                FolderCreateForm(
                    streams = listOf(
                        stream("sandbox", "песочница", 0),
                        stream("team", "Команда", 0),
                    ),
                    activeAccount = null,
                    operationInProgress = false,
                    error = null,
                    onBack = { backClicks += 1 },
                    onClose = { closeClicks += 1 },
                    onDismissError = {},
                    onCreate = { _, _ -> },
                    streamAvatar = { Spacer(Modifier.size(40.dp)) },
                )
            }
        }

        composeRule.onNodeWithTag(FOLDER_CHAT_SEARCH_TAG)
            .performTextInput("пес")
        composeRule.onNodeWithText("песочница").assertIsDisplayed()
        composeRule.onNodeWithText("Команда").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Назад к папкам").performClick()
        composeRule.onNodeWithContentDescription("Закрыть создание папки").performClick()
        composeRule.runOnIdle {
            assertEquals(1, backClicks)
            assertEquals(1, closeClicks)
        }
    }

    private fun folder(
        uuid: String,
        title: String,
        systemType: String?,
        itemCount: Int,
    ) = FolderResponseData(
        uuid = uuid,
        title = title,
        unreadCount = 0,
        systemType = systemType,
        creationDate = "2026-08-01T00:00:00Z",
        items = (0 until itemCount).map { index ->
            FolderItem(
                uuid = "$uuid-$index",
                streamUuid = "stream-$index",
                chatType = "stream",
                unreadCount = 0,
            )
        },
    )

    private fun stream(
        uuid: String,
        name: String,
        unreadCount: Int,
    ) = Stream(
        uuid = uuid,
        unreadCount = unreadCount,
        updatedAt = "2026-08-01T00:00:00Z",
        name = name,
        description = "Описание $name",
        isPrivate = false,
    )
}
