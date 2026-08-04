package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.AuthHeader
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.util.Locale
import java.util.UUID

private const val maxMentionResults = 12
private const val maxMentionFieldChars = 256
private const val maxMentionStatusChars = 64
private const val maxMentionAvatarUrnChars = 2_048
private const val maxVisibleMentionRows = 4
private const val activeUserStatus = "active"
private val mentionHorizontalInset = 12.dp
private val mentionListPadding = 8.dp
private val mentionRowPadding = 8.dp
private val mentionRowHeight = 64.dp
private val mentionShape = RoundedCornerShape(8.dp)
private val mentionAvatarSize = 32.dp
private val mentionStatusIndicatorSize = 8.dp
private val mentionAvatarTextGap = 12.dp
private val mentionTextBlockHeight = 40.dp
private val mentionTextLineHeight = 20.dp
private val mentionScrollbarWidth = 16.dp
private val mentionScrollbarInset = 10.dp
private val mentionScrollbarThumbInset = 4.dp
private val mentionScrollbarThumbWidth = 8.dp
private val mentionScrollbarThumbHeight = 80.5.dp
private val mentionScrollbarThumbRadius = 7.dp

internal data class ComposerMentionSuggestion(
    val userUuid: String,
    val displayName: String,
    val username: String,
    val email: String,
    val status: String,
    val avatarUrn: String,
)

internal fun composerMentionCandidates(
    users: List<UserResponseData>,
): List<ComposerMentionSuggestion> {
    val seenUserUuids = HashSet<String>()
    return buildList {
        users.forEach { user ->
            val candidate = user.toComposerMentionSuggestion()
                ?: return@forEach
            if (seenUserUuids.add(candidate.userUuid)) add(candidate)
        }
    }
}

internal fun filterComposerMentionSuggestions(
    candidates: List<ComposerMentionSuggestion>,
    query: String,
    maxResults: Int = maxMentionResults,
): List<ComposerMentionSuggestion> {
    if (maxResults <= 0) return emptyList()
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    if (normalizedQuery.isEmpty()) return candidates.take(maxResults)

    val uuidMatches = ArrayList<ComposerMentionSuggestion>(maxResults)
    val usernameMatches = ArrayList<ComposerMentionSuggestion>(maxResults)
    val displayNameMatches = ArrayList<ComposerMentionSuggestion>(maxResults)
    val emailMatches = ArrayList<ComposerMentionSuggestion>(maxResults)
    candidates.forEach { candidate ->
        when {
            normalizedQuery in candidate.userUuid.lowercase(Locale.ROOT) ->
                if (uuidMatches.size < maxResults) uuidMatches.add(candidate)
            normalizedQuery in candidate.username.lowercase(Locale.ROOT) ->
                if (usernameMatches.size < maxResults) usernameMatches.add(candidate)
            normalizedQuery in candidate.displayName.lowercase(Locale.ROOT) ->
                if (displayNameMatches.size < maxResults) {
                    displayNameMatches.add(candidate)
                }
            normalizedQuery in candidate.email.lowercase(Locale.ROOT) ->
                if (emailMatches.size < maxResults) emailMatches.add(candidate)
        }
    }
    return (uuidMatches + usernameMatches + displayNameMatches + emailMatches)
        .take(maxResults)
}

@Composable
internal fun ComposerMentionSuggestions(
    suggestions: List<ComposerMentionSuggestion>,
    baseUrl: String,
    authHeaders: List<AuthHeader>,
    onSelect: (ComposerMentionSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return

    val colors = LocalWorkspaceColorsPalette.current
    val title = stringResource(R.string.message_composer_mentions_title)
    val listState = rememberLazyListState()
    val visibleRows = minOf(suggestions.size, maxVisibleMentionRows)
    val listHeight = mentionListPadding * 2 + mentionRowHeight * visibleRows
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = mentionHorizontalInset)
            .height(listHeight)
            .clip(mentionShape)
            .background(colors.mentionSuggestionsBackground)
            .semantics { paneTitle = title },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(mentionListPadding),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            itemsIndexed(
                items = suggestions,
                key = { _, suggestion -> suggestion.userUuid },
            ) { index, suggestion ->
                val itemDescription = stringResource(
                    R.string.message_composer_mention_item_description,
                    suggestion.displayName,
                    suggestion.username,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mentionRowHeight),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(mentionShape)
                            .clickable { onSelect(suggestion) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = itemDescription
                            }
                            .padding(mentionRowPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(mentionAvatarSize)) {
                            Avatar(
                                avatarUrn = suggestion.avatarUrn,
                                baseUrl = baseUrl,
                                authHeaders = authHeaders,
                                color = null,
                                name = suggestion.displayName,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (suggestion.status == activeUserStatus) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(mentionStatusIndicatorSize)
                                        .background(
                                            colors.mentionSuggestionsBackground,
                                            CircleShape,
                                        )
                                        .padding(1.dp)
                                        .background(colors.indicatorGreen, CircleShape),
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .padding(start = mentionAvatarTextGap)
                                .weight(1f)
                                .height(mentionTextBlockHeight),
                        ) {
                            Text(
                                text = suggestion.displayName,
                                color = colors.textHeaders,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontFamily = InterFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.height(mentionTextLineHeight),
                            )
                            Text(
                                text = suggestion.email.ifEmpty { "@${suggestion.username}" },
                                color = colors.textAdditional50,
                                fontSize = 12.sp,
                                lineHeight = 20.sp,
                                fontFamily = InterFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(mentionTextLineHeight),
                            )
                        }
                    }
                    if (index < suggestions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                            thickness = 1.dp,
                            color = colors.divider,
                        )
                    }
                }
            }
        }
        if (suggestions.size > maxVisibleMentionRows) {
            ComposerMentionScrollbar(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                itemCount = suggestions.size,
                backgroundColor = colors.mentionSuggestionsBackground,
                dividerColor = colors.divider,
                modifier = Modifier
                    .width(mentionScrollbarWidth)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun ComposerMentionScrollbar(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    itemCount: Int,
    backgroundColor: Color,
    dividerColor: Color,
    modifier: Modifier = Modifier,
) {
    val rowHeightPx = with(LocalDensity.current) { mentionRowHeight.toPx() }
    val scrollableRows = (itemCount - maxVisibleMentionRows).coerceAtLeast(1)
    val scrollProgress = (
        firstVisibleItemIndex + firstVisibleItemScrollOffset / rowHeightPx
        ) / scrollableRows

    BoxWithConstraints(
        modifier = modifier.background(backgroundColor),
    ) {
        val availableTravel = maxHeight - mentionScrollbarInset * 2 - mentionScrollbarThumbHeight
        val thumbOffset = mentionScrollbarInset +
            availableTravel * scrollProgress.coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(dividerColor),
        )
        Box(
            modifier = Modifier
                .offset(x = mentionScrollbarThumbInset, y = thumbOffset)
                .size(
                    width = mentionScrollbarThumbWidth,
                    height = mentionScrollbarThumbHeight,
                )
                .background(
                    color = Color.White.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(mentionScrollbarThumbRadius),
                ),
        )
    }
}

private fun UserResponseData.toComposerMentionSuggestion(): ComposerMentionSuggestion? {
    val userUuid = uuid.toCanonicalUuid() ?: return null
    val normalizedUsername = username.trim().take(maxMentionFieldChars)
    val displayName = displayableName().trim()
        .ifEmpty { normalizedUsername }
        .take(maxMentionFieldChars)
    if (normalizedUsername.isEmpty() || displayName.isEmpty()) return null
    return ComposerMentionSuggestion(
        userUuid = userUuid,
        displayName = displayName,
        username = normalizedUsername,
        email = email.orEmpty().trim().take(maxMentionFieldChars),
        status = status.trim().take(maxMentionStatusChars),
        avatarUrn = avatar.trim().take(maxMentionAvatarUrnChars),
    )
}

private fun String.toCanonicalUuid(): String? = runCatching {
    UUID.fromString(trim()).toString()
}.getOrNull()
