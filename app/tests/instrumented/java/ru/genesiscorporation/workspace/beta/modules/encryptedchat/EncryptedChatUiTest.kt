package ru.genesiscorporation.workspace.beta.modules.encryptedchat

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.crypto.ChatMessageContext
import ru.genesiscorporation.workspace.beta.data.crypto.WorkspaceChatCrypto
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme
import java.io.File

/** Production components with synthetic names, generation identifiers and a local crypto round trip. */
@RunWith(AndroidJUnit4::class)
class EncryptedChatUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun keyGenerationAndOwnerActionsRenderWithoutSecretMaterial() {
        var rotations = 0
        var grants = 0
        var rejections = 0
        setKeys(onRotate = { rotations++ }, onGrant = { grants++ }, onReject = { rejections++ })
        compose.onNodeWithTag("encrypted-current-key-identifier").assertIsDisplayed()
        compose.onNodeWithTag("encrypted-grant-$REQUEST").assertDoesNotExist()
        capture("01-encryption-keys-owner")
        compose.onNodeWithTag("encrypted-rotate-key").performClick()
        compose.onNodeWithTag("encrypted-confirm-rotation").assertIsDisplayed()
        capture("02-reissue-key-confirmation")
        compose.onNodeWithTag("encrypted-cancel-confirmation").performClick()
        compose.runOnIdle { assertEquals(0, rotations) }
        compose.onNodeWithTag("encrypted-rotate-key").performClick()
        compose.onNodeWithTag("encrypted-confirm-rotation").performClick()
        compose.onNodeWithTag("encrypted-requests-PENDING").performScrollTo().performClick()
        compose.onNodeWithTag("encrypted-grant-$REQUEST").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("encrypted-reject-$REQUEST").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, rotations)
            assertEquals(1, grants)
            assertEquals(1, rejections)
        }
    }

    @Test fun selectingOneLocalKeyDeletesExactlyThatGenerationAfterConfirmation() {
        val deleted = mutableListOf<Set<String>>()
        setKeys(onDelete = { deleted += it }, showLocalActionsEntry = true)
        compose.onNodeWithTag("encrypted-local-keys").performScrollTo().performClick()
        compose.onNodeWithTag("encrypted-select-keys").performScrollTo().performClick()
        compose.onNodeWithTag("encrypted-select-key-$PREVIOUS").performScrollTo().performClick()
        compose.onNodeWithTag("encrypted-delete-keys").performScrollTo().performClick()
        compose.onNodeWithTag("encrypted-confirm-delete-keys").assertIsDisplayed()
        capture("03-remove-local-key-confirmation")
        compose.onNodeWithTag("encrypted-cancel-confirmation").performClick()
        compose.runOnIdle { assertEquals(emptyList<Set<String>>(), deleted) }
        compose.onNodeWithTag("encrypted-delete-keys").performClick()
        compose.onNodeWithTag("encrypted-confirm-delete-keys").performClick()
        compose.runOnIdle { assertEquals(listOf(setOf(PREVIOUS)), deleted) }
    }

    @Test fun deleteAllRequiresConfirmationAndContainsOnlyLocalGenerations() {
        var deleted = emptySet<String>()
        setKeys(onDelete = { deleted = it }, showLocalActionsEntry = true)
        compose.onNodeWithTag("encrypted-local-keys").performScrollTo().performClick()
        compose.onNodeWithTag("encrypted-delete-keys").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(emptySet<String>(), deleted) }
        compose.onNodeWithTag("encrypted-confirm-delete-keys").performClick()
        compose.runOnIdle { assertEquals(setOf(CURRENT, PREVIOUS), deleted) }
    }

    @Test fun memberAndBusyStatesDoNotOfferForbiddenOrRepeatedManagementActions() {
        var state by mutableStateOf(ownerState().copy(canManageKeys = false, canRotate = false))
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                EncryptedChatKeysScreen(state, users(), {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("encrypted-rotate-key").assertDoesNotExist()
        compose.onNodeWithTag("encrypted-grant-$REQUEST").assertDoesNotExist()
        compose.runOnIdle { state = ownerState().copy(pending = true) }
        compose.onNodeWithTag("encrypted-rotate-key").assertIsNotEnabled()
        compose.onNodeWithTag("encrypted-requests-PENDING").performScrollTo().performClick()
        compose.onNodeWithTag("encrypted-grant-$REQUEST").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("encrypted-reject-$REQUEST").assertIsNotEnabled()
    }

    @Test fun missingAndRejectedKeyAllowRequestWhileOutstandingRequestCannotRepeat() {
        var status by mutableStateOf(EncryptedChatRequestStatus.IDLE)
        var requests = 0
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                Column(Modifier.background(LocalWorkspaceColorsPalette.current.background).padding(16.dp)) {
                    EncryptedChatLockedMessage(CURRENT, status, false, false) { requests++; status = EncryptedChatRequestStatus.REQUESTED }
                }
            }
        }
        compose.onNodeWithTag("encrypted-request-key-$CURRENT").assertIsDisplayed()
        capture("04-encrypted-message-missing-key")
        assertEquals(1, compose.encryptedInlineLinkCount("encrypted-request-key-$CURRENT"))
        compose.clickEncryptedInlineLink("encrypted-request-key-$CURRENT", resource(R.string.encrypted_chat_request_inline))
        compose.onNodeWithTag("encrypted-request-key-$CURRENT").assertDoesNotExist()
        compose.onNodeWithTag("encrypted-request-status-$CURRENT").assertIsDisplayed()
        assertEquals(0, compose.encryptedInlineLinkCount("encrypted-request-status-$CURRENT"))
        compose.runOnIdle { assertEquals(1, requests); status = EncryptedChatRequestStatus.REJECTED }
        capture("05-access-request-rejected")
        assertEquals(1, compose.encryptedInlineLinkCount("encrypted-request-key-$CURRENT"))
        compose.clickEncryptedInlineLink("encrypted-request-key-$CURRENT", resource(R.string.encrypted_chat_request_retry))
        compose.runOnIdle { assertEquals(2, requests) }
    }

    @Test fun protectedChatTypeAndDecryptedSyntheticContentRenderWithServiceStates() {
        val crypto = WorkspaceChatCrypto()
        val key = crypto.createGroupKey()
        val plaintext = "Макеты готовы. Обсудим следующий шаг?"
        val context = ChatMessageContext(STREAM, CURRENT)
        val envelope = crypto.encryptMessage(key, plaintext.toByteArray(), context)
        val decoded = crypto.decryptMessage(key, envelope, context).toString(Charsets.UTF_8)
        assertNotEquals(plaintext, WorkspaceChatCrypto.toBase64(envelope))
        assertEquals(plaintext, decoded)
        key.fill(0)
        compose.setContent {
            var encrypted by remember { mutableStateOf(true) }
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                Column(Modifier.fillMaxSize().background(LocalWorkspaceColorsPalette.current.background).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    EncryptedChatTypeSelector(encrypted) { encrypted = it }
                    Text(stringResource(R.string.encrypted_chat_introduction), color = LocalWorkspaceColorsPalette.current.textAdditional50)
                    EncryptedChatServiceMessage(
                        EncryptedChatPayload("key_request", CURRENT, deviceName = "Android"), "Анна", EncryptedChatRequestOutcome.PENDING,
                        true, false, {}, {},
                    )
                    // Only the locally decrypted presentation enters the text node.
                    Text(decoded, color = LocalWorkspaceColorsPalette.current.textHeaders)
                    EncryptedChatLockedMessage(PREVIOUS, EncryptedChatRequestStatus.IDLE, true, false, onRequestKey = {})
                }
            }
        }
        compose.onNodeWithText(plaintext).assertIsDisplayed()
        compose.onNodeWithText(WorkspaceChatCrypto.toBase64(envelope)).assertDoesNotExist()
        capture("06-protected-chat-states")
        compose.onNodeWithTag("chat-type-regular").performClick()
        compose.onNodeWithTag("chat-type-protected").performClick()
    }

    private fun resource(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun setKeys(
        onRotate: () -> Unit = {}, onGrant: (String) -> Unit = {}, onReject: (String) -> Unit = {},
        onDelete: (Set<String>) -> Unit = {}, showLocalActionsEntry: Boolean = false,
    ) {
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                EncryptedChatKeysScreen(ownerState(), users(), {}, {}, onGrant, onReject, {}, onRotate, onDelete,
                    showLocalActionsEntry = showLocalActionsEntry)
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "encrypted-chat-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    private fun ownerState() = EncryptedChatSessionState(
        currentKeyUuid = CURRENT, sendingKeyUuid = CURRENT, hasCurrentKey = true, canManageKeys = true, canRotate = true,
        localKeys = listOf(EncryptedChatLocalKey(CURRENT, 1792058400000L, true), EncryptedChatLocalKey(PREVIOUS, 1791972000000L, true)),
        requests = listOf(EncryptedChatKeyRequest(
            EncryptedChatWireMessage(REQUEST, PROJECT, STREAM, USER, EncryptedChatPayload("key_request", CURRENT, deviceName = "Android")),
            EncryptedChatRequestOutcome.PENDING,
        )),
    )

    private fun users() = listOf(UserResponseData(firstName = "Анна", lastName = "Соколова", username = "anna", uuid = USER, status = "active", avatar = ""))

    companion object {
        private const val CURRENT = "11111111-1111-4111-8111-111111111111"
        private const val PREVIOUS = "22222222-2222-4222-8222-222222222222"
        private const val REQUEST = "33333333-3333-4333-8333-333333333333"
        private const val STREAM = "44444444-4444-4444-8444-444444444444"
        private const val USER = "55555555-5555-4555-8555-555555555555"
        private const val PROJECT = "66666666-6666-4666-8666-666666666666"
    }
}
