package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderItem
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
class ForwardMessagesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun abandoningAnUncertainForwardRequiresExplicitConfirmation() {
        var abandoned = 0
        var verified = 0
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                Column { ForwardUncertainActions(onVerify = { verified++ }, onAbandon = { abandoned++ }) }
            }
        }
        compose.onNodeWithTag("forward-abandon").performClick()
        compose.onNodeWithTag("forward-abandon-cancel").performClick()
        compose.runOnIdle { assertEquals(0, abandoned); assertEquals(0, verified) }
        compose.onNodeWithTag("forward-abandon").performClick()
        compose.onNodeWithTag("forward-abandon-confirm").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("forward-abandon-confirm").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, abandoned) }
        compose.onNodeWithTag("forward-verify").performClick()
        compose.runOnIdle { assertEquals(1, verified) }
        compose.onNodeWithTag("forward-abandon").performClick()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.forward_stop_verifying_body)).assertIsDisplayed()
        compose.onNodeWithTag("forward-abandon-confirm").performClick()
        compose.onNodeWithTag("forward-abandon-confirm").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, abandoned); assertEquals(1, verified) }
    }

    @Test fun forwardedCopiesShowFigmaSourceAndTimeMetadata() {
        var snapshot by mutableStateOf(true)
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                if (snapshot) SnapshotQuoteCard(
                    "Alice",
                    false,
                    sourceLabel = "Engineering · General",
                    sourceCreatedAt = "2020-09-11T16:47:00Z",
                ) { Text("Captured body") }
                else MessageQuoteCard(MessageQuoteState.Loading, "Alice", false, onRetry = {}) {}
            }
        }
        compose.onNodeWithText("Alice").assertIsDisplayed()
        compose.onNodeWithText("# Engineering · General").assertIsDisplayed()
        compose.onNodeWithTag("snapshot-time", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { snapshot = false }
        compose.onNodeWithTag("snapshot-source").assertDoesNotExist()
        compose.onNodeWithTag("snapshot-time").assertDoesNotExist()
        compose.onNodeWithText("Alice").assertIsDisplayed()
    }

    @Test fun directSourcesDoNotShowAChannelMarker() {
        compose.setContent {
            WokspaceTheme(darkTheme = false, dynamicColor = false) {
                SnapshotQuoteCard(
                    "Alice",
                    false,
                    sourceLabel = "Bob Reed",
                    sourceIsDirect = true,
                ) { Text("Direct message") }
            }
        }
        compose.onNodeWithText("Bob Reed").assertIsDisplayed()
        compose.onNodeWithText("# Bob Reed").assertDoesNotExist()
    }

    @Test fun recipientSearchFoldersAndTopicChoiceUseActualSelections() {
        var chosen: String? = null
        compose.setContent {
            var query by remember { mutableStateOf("") }
            var folder by remember { mutableStateOf<String?>(null) }
            var selected by remember { mutableStateOf<Stream?>(null) }
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                ForwardRecipientPicker(listOf(stream(FIRST, "CASSI Design"), stream(SECOND, "CASSI General")),
                    listOf(FolderResponseData("folder", "Design", 0, "custom", "2026-09-07T12:00:00Z", listOf(FolderItem("item", FIRST, "stream", 0)))),
                    emptyList(), selected, listOf(topic()), query, folder, false, true, null, "", null,
                    onCancel = {}, onBack = { selected = null }, onSearch = { query = it }, onFolder = { folder = it },
                    onStream = { selected = it; query = "" }, onTopic = { chosen = it.uuid }, onRetry = {})
            }
        }
        compose.onNodeWithText("Design", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("forward-stream-$SECOND").assertDoesNotExist()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.forward_all_chats), useUnmergedTree = true).performClick()
        compose.onNodeWithTag("forward-search").performTextInput("General")
        compose.onNodeWithTag("forward-stream-$FIRST").assertDoesNotExist()
        compose.onNodeWithTag("forward-stream-$SECOND").assertIsDisplayed().performClick()
        compose.onNodeWithTag("forward-topic-$TOPIC").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(TOPIC, chosen) }
    }

    @Test fun choosingWorkspaceFromNativeChooserReturnsToTheOriginatingSession() {
        val session = ForwardShareSessions.create()
        lateinit var context: Context
        compose.setContent { context = LocalContext.current }
        val internal = Intent(context, ForwardToWorkspaceActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(ForwardShareSessions.EXTRA_SESSION, session.first)
        }
        val external = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "CASSI native chooser fixture") }
        val chooser = Intent.createChooser(external, "Переслать").apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(internal))
        }
        try {
            compose.runOnIdle { context.startActivity(chooser) }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val label = context.packageManager.getActivityInfo(ComponentName(context, ForwardToWorkspaceActivity::class.java), 0)
                .loadLabel(context.packageManager).toString()
            val deadline = System.currentTimeMillis() + 10_000
            var clicked = false
            while (!clicked && System.currentTimeMillis() < deadline) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                val matching = root?.findAccessibilityNodeInfosByText(label).orEmpty()
                for (node in matching) {
                    if (node.text?.toString() == label) {
                        var target: AccessibilityNodeInfo? = node
                        while (target != null && !target.isClickable) target = target.parent
                        if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { clicked = true; break }
                    }
                }
                if (!clicked) Thread.sleep(100)
            }
            assertTrue("Workspace target must be present in the native chooser", clicked)
            compose.waitUntil(5_000) { session.second.value }
            assertTrue(session.second.value)
        } finally { ForwardShareSessions.remove(session.first) }
    }

    @Test fun externalAttachmentsUseReadableContentUrisAndReadOnlyGrants() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "message-shares/cassi-contract").apply { mkdirs() }
        try {
            val file = File(folder, "report.txt").apply { writeText("CASSI attachment fixture") }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.message-share-files", file)
            val share = buildExternalShareIntent("Author: shared report", arrayListOf(uri), "text/plain")
            assertEquals(Intent.ACTION_SEND, share.action)
            assertEquals("content", uri.scheme)
            assertEquals(uri, share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertEquals(uri, share.clipData?.getItemAt(0)?.uri)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, share.flags)
            assertEquals("CASSI attachment fixture", context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() })
        } finally { folder.deleteRecursively() }
    }

    companion object {
        private const val FIRST = "00000000-0000-0000-0000-000000000001"
        private const val SECOND = "00000000-0000-0000-0000-000000000002"
        private const val TOPIC = "00000000-0000-0000-0000-000000000003"
        private fun stream(uuid: String, name: String) = Stream(uuid, 0, 0, 0, "2026-09-07T12:00:00Z", name, false, 0x7087FF, notificationMode = "all")
        private fun topic() = TopicsResponseData(TOPIC, "General topic", 0x7087FF, SECOND, "2026-09-07T12:00:00Z", 0, false, true, notificationMode = "all")
    }
}
