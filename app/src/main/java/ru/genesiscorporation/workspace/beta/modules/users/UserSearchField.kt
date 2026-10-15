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

package ru.genesiscorporation.workspace.beta.modules.users

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
internal fun UserSearchField(query: String, onQueryChange: (String) -> Unit) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.fillMaxWidth().height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 4.dp, horizontal = 16.dp)
            .background(LocalWorkspaceColorsPalette.current.searchBackground, RoundedCornerShape(8.dp))
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(
                color = LocalWorkspaceColorsPalette.current.textAdditional30,
                fontSize = 14.sp,
                fontFamily = InterFontFamily,
            ),
            cursorBrush = SolidColor(LocalWorkspaceColorsPalette.current.textAdditional30),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) Text("Поиск...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                innerTextField()
            }
        )
    }
}
