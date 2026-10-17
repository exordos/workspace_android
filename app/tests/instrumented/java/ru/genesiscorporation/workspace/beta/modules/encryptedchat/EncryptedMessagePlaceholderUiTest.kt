/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.genesiscorporation.workspace.beta.modules.encryptedchat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.modules.chatdialog.LockedEncryptedMessageActions
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class EncryptedMessagePlaceholderUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lockedMessagesUseSoftGeometryInBothThemesAndNarrowWindows() {
        var dark by mutableStateOf(true)
        var width by mutableStateOf(320.dp)
        compose.setContent {
            WokspaceTheme(darkTheme = dark, dynamicColor = false) {
                Box(Modifier.width(width).background(LocalWorkspaceColorsPalette.current.background)) {
                    EncryptedChatLockedMessage("synthetic-key", EncryptedChatRequestStatus.IDLE,
                        false, false, showRequest = false, onRequestKey = {})
                }
            }
        }
        listOf(true to 320.dp, false to 180.dp).forEach { (theme, window) ->
            compose.runOnIdle { dark = theme; width = window }
            compose.onNodeWithText(resource(R.string.encrypted_chat_locked)).assertDoesNotExist()
            compose.onNodeWithContentDescription(resource(R.string.encrypted_chat_locked)).assertIsDisplayed()
            val image = compose.onNodeWithTag("encrypted-message-placeholder").captureToImage().toPixelMap()
            // A real rendered blur has many intermediate shades, unlike flat skeleton bars.
            val x = image.width / 3
            val shades = (0 until image.height).map { image[x, it].red }.distinct()
            assertTrue("The two placeholder rows must have soft edges", shades.size > 8)
            val background = image[image.width - 1, 0].red
            fun inkAt(y: Int) = (0 until image.width).count { kotlin.math.abs(image[it, y].red - background) > 0.035f }
            val upper = (0 until image.height / 2).maxOf(::inkAt)
            val lower = (image.height / 2 until image.height).maxOf(::inkAt)
            assertTrue("The placeholder must retain two rows with different lengths", upper > lower && lower > image.width / 3)
        }
    }

    @Test fun blurredMessageRetainsItsLongPressKeyRequestAndPendingGuard() {
        var pending by mutableStateOf(false)
        var requests = 0
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                LockedEncryptedMessageActions("synthetic-message", pending, { requests++; pending = true }) {
                    EncryptedChatLockedMessage("synthetic-key", EncryptedChatRequestStatus.IDLE,
                        false, pending, showRequest = false, onRequestKey = {})
                }
            }
        }
        compose.onNodeWithTag("encrypted-message-placeholder", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(resource(R.string.encrypted_chat_locked)).assertIsDisplayed()
        compose.onNodeWithTag("encrypted-message-placeholder", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithTag("request-message-key-action").performClick()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithTag("message-bubble-synthetic-message").performTouchInput { longClick() }
        compose.onNodeWithTag("request-message-key-action").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun invalidCiphertextKeepsItsReadableErrorInsteadOfTheBlur() {
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                EncryptedChatLockedMessage("synthetic-key", EncryptedChatRequestStatus.IDLE,
                    true, false, showRequest = false, onRequestKey = {})
            }
        }
        compose.onNodeWithText(resource(R.string.encrypted_chat_cannot_decrypt)).assertIsDisplayed()
        compose.onNodeWithTag("encrypted-message-placeholder").assertDoesNotExist()
    }

    private fun resource(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
