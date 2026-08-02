package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
internal fun MessageReactionChip(
    displayEmoji: String,
    count: Int,
    reactionUsers: List<UserResponseData>?,
    selected: Boolean,
    enabled: Boolean,
    avatarBaseUrl: String,
    avatarOwnerAccountId: String?,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val contentDescription = if (reactionUsers != null) {
        stringResource(
            R.string.message_reaction_users_description,
            displayEmoji,
            reactionUsers.joinToString { it.displayableName() },
        )
    } else {
        stringResource(
            R.string.message_reaction_count_description,
            displayEmoji,
            count,
        )
    }
    Row(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (selected) {
                    colors.primary
                } else {
                    colors.messageReactionBackground
                },
                shape = CircleShape,
            )
            .background(
                color = if (selected) {
                    colors.primary.copy(alpha = 0.16f)
                } else {
                    colors.messageReactionBackground
                },
                shape = CircleShape,
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .height(28.dp)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                this.contentDescription = contentDescription
            }
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = displayEmoji,
            color = colors.messageReactionForeground,
            fontSize = 14.sp,
            lineHeight = 16.sp,
        )
        if (reactionUsers == null) {
            Text(
                text = count.toString(),
                color = colors.messageReactionForeground,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                reactionUsers.forEach { user ->
                    Avatar(
                        avatarUrn = user.avatar,
                        baseUrl = avatarBaseUrl,
                        color = null,
                        name = user.displayableName(),
                        size = 18,
                        hasPadding = false,
                        ownerAccountId = avatarOwnerAccountId,
                    )
                }
            }
        }
    }
}
