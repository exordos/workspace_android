package ru.genesiscorporation.workspace.beta.modules.activity

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.modules.chatchannels.FolderList
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

enum class MyActivityDestination(
    val title: String,
    val icon: Int,
    val indicator: ActivityIndicator,
) {
    INBOX("Входящие", R.drawable.ic_mail, ActivityIndicator.PURPLE),
    STARRED("Избранное", R.drawable.ic_star, ActivityIndicator.BLUE),
    PINNED(
        "Отмеченные сообщения",
        R.drawable.ic_activity_bookmark,
        ActivityIndicator.RED,
    ),
    MENTIONS("Упоминания", R.drawable.ic_activity_mention, ActivityIndicator.YELLOW),
    REACTIONS("Реакции", R.drawable.ic_activity_reaction, ActivityIndicator.GREEN),
    DRAFTS("Черновики", R.drawable.ic_draft, ActivityIndicator.PINK),
    FEED("Лента", R.drawable.ic_feed, ActivityIndicator.ORANGE),
}

enum class ActivityIndicator {
    YELLOW,
    PINK,
    PURPLE,
    ORANGE,
    GREEN,
    RED,
    BLUE,
}

internal fun filteredActivityDestinations(query: String): List<MyActivityDestination> {
    val normalizedQuery = query.trim().lowercase()
    return MyActivityDestination.entries.filter { destination ->
        normalizedQuery.isEmpty() ||
            destination.title.lowercase().contains(normalizedQuery)
    }
}

@Composable
fun MyActivityScreen(
    chatViewModel: ChatViewModel,
    navController: NavHostController,
    onOpenChats: () -> Unit,
) {
    val context = LocalContext.current
    val unreadMentions by
        chatViewModel.unreadMentionCount.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val destinations = remember(query) { filteredActivityDestinations(query) }
    val colors = LocalWorkspaceColorsPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = "Моя активность",
                color = colors.textHeaders,
                fontFamily = InterFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .semantics { heading() },
            )
            IconButton(
                onClick = onOpenChats,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_new_stream),
                    contentDescription = "Открыть мессенджер",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .height(36.dp)
                .background(colors.searchBackground, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = colors.iconBase,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(24.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = colors.textHeaders,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                ),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Поиск по моей активности" },
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Найти",
                                color = colors.textAdditional30,
                                fontFamily = InterFontFamily,
                                fontSize = 14.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        FolderList(chatViewModel) { }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 6.dp,
                end = 12.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(destinations, key = MyActivityDestination::name) { destination ->
                ActivityRow(
                    destination = destination,
                    unreadCount = if (destination == MyActivityDestination.MENTIONS) {
                        unreadMentions
                    } else {
                        0
                    },
                    onClick = {
                        if (destination == MyActivityDestination.MENTIONS) {
                            navController.navigate(ChatFlow.Mentions)
                        } else {
                            Toast.makeText(
                                context,
                                "Раздел «${destination.title}» пока недоступен",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ActivityRow(
    destination: MyActivityDestination,
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val indicatorColor = when (destination.indicator) {
        ActivityIndicator.YELLOW -> colors.indicatorYellow
        ActivityIndicator.PINK -> colors.indicatorPink
        ActivityIndicator.PURPLE -> colors.indicatorPurple
        ActivityIndicator.ORANGE -> colors.indicatorOrange
        ActivityIndicator.GREEN -> colors.indicatorGreen
        ActivityIndicator.RED -> colors.indicatorRed
        ActivityIndicator.BLUE -> colors.indicatorBlue
    }
    val count = unreadCount.coerceAtLeast(0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.cardBackgroundActive, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (count > 0) {
                    "${destination.title}, непрочитанных: $count"
                } else {
                    destination.title
                }
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(indicatorColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(destination.icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = destination.title,
            color = colors.textHeaders,
            fontFamily = InterFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .widthIn(min = 20.dp)
                    .background(colors.noticeCounterBadge, CircleShape)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.coerceAtMost(999).toString(),
                    color = colors.noticeOnBadge,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
