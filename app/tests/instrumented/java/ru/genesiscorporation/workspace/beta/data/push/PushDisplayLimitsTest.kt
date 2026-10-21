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

package ru.genesiscorporation.workspace.beta.data.push

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.UserViewModelFactory
import ru.genesiscorporation.workspace.beta.WorkspaceApplication
import ru.genesiscorporation.workspace.beta.modules.visualsettings.VisualSettingsScreen
import ru.genesiscorporation.workspace.beta.modules.visualsettings.VisualSettingsViewModel
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

/** Exercise the real settings screen in a short viewport, as in landscape/multi-window. */
class PushDisplayLimitsTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()

    @After fun releaseViewModels() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
    }

    @Test fun displayLimitsKeepExistingSettingsReachableInShortWindows() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WorkspaceApplication
        val user = ViewModelProvider(store, UserViewModelFactory(app.serverRepository))[UserViewModel::class.java]
        val visual = VisualSettingsViewModel(user)
        compose.setContent {
            WokspaceTheme {
                Box(Modifier.fillMaxWidth().height(260.dp).clipToBounds()) {
                    VisualSettingsScreen(visual, rememberNavController())
                }
            }
        }
        compose.onNodeWithText("Сначала непрочитанные").performScrollTo().assertIsDisplayed()
    }
}
