package ru.genesiscorporation.workspace.beta.modules.encryptedchat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

/** Exercise the production annotated links and distinct device/correlation states. */
@RunWith(AndroidJUnit4::class)
class EncryptedChatServiceStatesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ownDeviceRequestWaitsWhileAnotherDeviceOfTheSameUserCanBeManaged() {
        var ownDevice by mutableStateOf(true)
        var outcome by mutableStateOf(EncryptedChatRequestOutcome.PENDING)
        var grants = 0
        var rejects = 0
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                Column(Modifier.padding(16.dp)) {
                    EncryptedChatServiceMessage(request(), "Касандра", outcome, true, false,
                        onGrant = { grants++; outcome = EncryptedChatRequestOutcome.GRANTED },
                        onReject = { rejects++; outcome = EncryptedChatRequestOutcome.REJECTED },
                        isOwnDeviceRequest = ownDevice)
                }
            }
        }
        compose.onNodeWithText(resource(R.string.encrypted_chat_requested)).assertIsDisplayed()
        assertEquals(0, compose.encryptedInlineLinkCount(STATUS_TAG))
        compose.onNodeWithTag(SERVICE_TAG).assertDoesNotExist()
        compose.runOnIdle { ownDevice = false }
        compose.onNodeWithTag(SERVICE_TAG).assertIsDisplayed()
        assertEquals(2, compose.encryptedInlineLinkCount(SERVICE_TAG))
        compose.clickEncryptedInlineLink(SERVICE_TAG, resource(R.string.encrypted_chat_confirm_request))
        compose.runOnIdle { assertEquals(1, grants); assertEquals(0, rejects) }
        compose.onNodeWithTag(SERVICE_TAG).assertDoesNotExist()
        compose.runOnIdle { outcome = EncryptedChatRequestOutcome.PENDING }
        compose.clickEncryptedInlineLink(SERVICE_TAG, resource(R.string.encrypted_chat_reject_inline))
        compose.runOnIdle { assertEquals(1, grants); assertEquals(1, rejects) }
        compose.onNodeWithTag(SERVICE_TAG).assertDoesNotExist()
    }

    @Test fun missingGenerationKeyDisablesOnlyGrantAndBusyStateDisablesBothLinks() {
        var pending by mutableStateOf(true)
        var grants = 0
        var rejects = 0
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                Column(Modifier.padding(16.dp)) {
                    EncryptedChatServiceMessage(request(), "Анна", EncryptedChatRequestOutcome.PENDING,
                        true, pending, { grants++ }, { rejects++ }, canGrant = false)
                }
            }
        }
        assertEquals(0, compose.encryptedInlineLinkCount(SERVICE_TAG))
        compose.clickEncryptedInlineLink(SERVICE_TAG, resource(R.string.encrypted_chat_confirm_request))
        compose.clickEncryptedInlineLink(SERVICE_TAG, resource(R.string.encrypted_chat_reject_inline))
        compose.runOnIdle { assertEquals(0, grants); assertEquals(0, rejects); pending = false }
        assertEquals(1, compose.encryptedInlineLinkCount(SERVICE_TAG))
        compose.clickEncryptedInlineLink(SERVICE_TAG, resource(R.string.encrypted_chat_confirm_request))
        compose.runOnIdle { assertEquals(0, grants); assertEquals(0, rejects) }
        compose.clickEncryptedInlineLink(SERVICE_TAG, resource(R.string.encrypted_chat_reject_inline))
        compose.runOnIdle { assertEquals(1, rejects) }
    }

    @Test fun ordinaryMemberSeesRequestNoticeWithoutManagementLinks() {
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                Column(Modifier.padding(16.dp)) {
                    EncryptedChatServiceMessage(request(), "Анна", EncryptedChatRequestOutcome.PENDING,
                        false, false, { error("Member cannot grant") }, { error("Member cannot reject") })
                }
            }
        }
        compose.onNodeWithTag(SERVICE_TAG).assertIsDisplayed()
        assertEquals(0, compose.encryptedInlineLinkCount(SERVICE_TAG))
        compose.onNodeWithText(resource(R.string.encrypted_chat_confirm_request), substring = true).assertDoesNotExist()
        compose.onNodeWithText(resource(R.string.encrypted_chat_reject_inline), substring = true).assertDoesNotExist()
    }

    @Test fun ownRejectedRequestRetriesItsHistoricalGenerationAndThenWaitsWithoutAnotherLink() {
        var outcome by mutableStateOf(EncryptedChatRequestOutcome.REJECTED)
        var replacementExists by mutableStateOf(false)
        val requested = mutableListOf<String>()
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                Column(Modifier.padding(16.dp)) {
                    EncryptedChatServiceMessage(request(), "Касандра", outcome, false, false, {}, {},
                        isOwnDeviceRequest = true, isLatestOwnGenerationRequest = !replacementExists,
                        onRequestKey = { requested += it; replacementExists = true })
                    if (replacementExists) EncryptedChatServiceMessage(request(), "Касандра", EncryptedChatRequestOutcome.PENDING,
                        false, false, {}, {}, isOwnDeviceRequest = true, isLatestOwnGenerationRequest = true)
                }
            }
        }
        assertEquals(1, compose.encryptedInlineLinkCount(REQUEST_TAG))
        compose.clickEncryptedInlineLink(REQUEST_TAG, resource(R.string.encrypted_chat_request_retry))
        compose.runOnIdle { assertEquals(listOf(KEY), requested) }
        compose.onNodeWithTag(REQUEST_TAG).assertDoesNotExist()
        compose.onAllNodesWithTag(STATUS_TAG).assertCountEquals(1)
        assertEquals(0, compose.encryptedInlineLinkCount(STATUS_TAG))
        // A history/realtime replay of the old rejected row must not restore its retry link.
        compose.runOnIdle { outcome = EncryptedChatRequestOutcome.GRANTED }
        compose.onAllNodesWithTag(STATUS_TAG).assertCountEquals(1)
        compose.onAllNodesWithText(resource(R.string.encrypted_chat_received), substring = true).assertCountEquals(0)
    }

    @Test fun correlatedGrantDoesNotDuplicateTheRecipientStatusAndUnrelatedDevicesSeeNoReceipt() {
        var ownRequestPresent by mutableStateOf(true)
        var recipientDevice by mutableStateOf(true)
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                Column(Modifier.padding(16.dp)) {
                    if (ownRequestPresent) EncryptedChatServiceMessage(request(), "Касандра", EncryptedChatRequestOutcome.GRANTED,
                        false, false, {}, {}, isOwnDeviceRequest = true)
                    EncryptedChatServiceMessage(EncryptedChatPayload("key_grant", KEY), "Владелец", null,
                        false, false, {}, {}, isRecipientDevice = recipientDevice, hasOwnGenerationRequest = ownRequestPresent,
                        hasGenerationKey = true, requestStatus = EncryptedChatRequestStatus.RECEIVED)
                    EncryptedChatServiceMessage(EncryptedChatPayload("key_reject", KEY), "Владелец", null,
                        false, false, {}, {}, isRecipientDevice = false)
                }
            }
        }
        compose.onAllNodesWithText(resource(R.string.encrypted_chat_received), substring = true).assertCountEquals(1)
        compose.onAllNodesWithTag(STATUS_TAG).assertCountEquals(1)
        compose.onNodeWithTag(REQUEST_TAG).assertDoesNotExist()
        compose.runOnIdle { ownRequestPresent = false }
        compose.onAllNodesWithText(resource(R.string.encrypted_chat_received), substring = true).assertCountEquals(1)
        compose.onAllNodesWithTag(STATUS_TAG).assertCountEquals(1)
        compose.runOnIdle { recipientDevice = false }
        compose.onAllNodesWithTag(STATUS_TAG).assertCountEquals(0)
    }

    @Test fun rawGrantAndRejectDoNotAssertUnverifiedRecipientState() {
        var kind by mutableStateOf("key_grant")
        var hasKey by mutableStateOf(false)
        var status by mutableStateOf(EncryptedChatRequestStatus.IDLE)
        val requested = mutableListOf<String>()
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                Column(Modifier.padding(16.dp)) {
                    EncryptedChatServiceMessage(EncryptedChatPayload(kind, KEY), "Владелец", null,
                        false, false, {}, {}, isRecipientDevice = true, hasGenerationKey = hasKey,
                        requestStatus = status, onRequestKey = { requested += it })
                }
            }
        }
        compose.onNodeWithTag(STATUS_TAG).assertDoesNotExist()
        compose.onNodeWithTag(REQUEST_TAG).assertDoesNotExist()
        compose.runOnIdle { hasKey = true }
        // A local key alone does not correlate this grant with a completed request.
        compose.onNodeWithTag(STATUS_TAG).assertDoesNotExist()
        compose.runOnIdle { hasKey = false; status = EncryptedChatRequestStatus.RECEIVED }
        // A stale completion without locally usable key material is not a receipt either.
        compose.onNodeWithTag(STATUS_TAG).assertDoesNotExist()
        compose.runOnIdle { hasKey = true }
        compose.onNodeWithTag(STATUS_TAG).assertIsDisplayed()
        compose.runOnIdle { kind = "key_reject"; status = EncryptedChatRequestStatus.IDLE }
        compose.onNodeWithTag(STATUS_TAG).assertDoesNotExist()
        compose.onNodeWithTag(REQUEST_TAG).assertDoesNotExist()
        compose.runOnIdle { status = EncryptedChatRequestStatus.REJECTED }
        compose.clickEncryptedInlineLink(REQUEST_TAG, resource(R.string.encrypted_chat_request_retry))
        compose.runOnIdle { assertEquals(listOf(KEY), requested) }
    }

    @Test fun rotationNoticeDistinguishesOwnerKeyFromParticipantRequestAndAvoidsDuplicateRequests() {
        var hasKey by mutableStateOf(true)
        var ownerAnnouncement by mutableStateOf(true)
        var ownRequest by mutableStateOf(false)
        var pending by mutableStateOf(false)
        val requested = mutableListOf<String>()
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                Column(Modifier.padding(16.dp)) {
                    EncryptedChatServiceMessage(EncryptedChatPayload("key_announced", KEY), "Владелец", null,
                        false, pending, {}, {}, hasGenerationKey = hasKey, isOwnerAnnouncement = ownerAnnouncement,
                        hasOwnGenerationRequest = ownRequest, onRequestKey = { requested += it })
                }
            }
        }
        compose.onNodeWithText(resource(R.string.encrypted_chat_new_owner_key)).assertIsDisplayed()
        compose.onNodeWithTag(REQUEST_TAG).assertDoesNotExist()
        compose.runOnIdle { hasKey = false; ownerAnnouncement = false }
        compose.onNodeWithText(resource(R.string.encrypted_chat_security_key_changed)).assertIsDisplayed()
        assertEquals(1, compose.encryptedInlineLinkCount(REQUEST_TAG))
        compose.clickEncryptedInlineLink(REQUEST_TAG, resource(R.string.encrypted_chat_request_inline))
        compose.runOnIdle { assertEquals(listOf(KEY), requested); ownRequest = true }
        compose.onNodeWithTag(REQUEST_TAG).assertDoesNotExist()
        compose.runOnIdle { ownRequest = false; pending = true }
        assertEquals(0, compose.encryptedInlineLinkCount(REQUEST_TAG))
        compose.clickEncryptedInlineLink(REQUEST_TAG, resource(R.string.encrypted_chat_request_inline))
        compose.runOnIdle { assertEquals(listOf(KEY), requested) }
    }

    private fun request() = EncryptedChatPayload("key_request", KEY, deviceId = "another-device", deviceName = "Android")
    private fun resource(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    companion object {
        private const val KEY = "11111111-1111-4111-8111-111111111111"
        private const val SERVICE_TAG = "encrypted-service-message"
        private const val STATUS_TAG = "encrypted-request-status-$KEY"
        private const val REQUEST_TAG = "encrypted-request-key-$KEY"
    }
}
