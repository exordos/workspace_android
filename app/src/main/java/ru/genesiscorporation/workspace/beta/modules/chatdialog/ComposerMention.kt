package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.util.UUID
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

internal data class ComposerMentionQuery(
    val start: Int,
    val end: Int,
    val query: String,
)

internal data class ComposerMentionSuggestion(
    val userUuid: String,
    val displayName: String,
    val username: String,
    val email: String,
    val status: String,
)

internal fun detectComposerMentionQuery(
    value: TextFieldValue,
): ComposerMentionQuery? {
    if (!value.selection.collapsed) return null
    val cursor = value.selection.start.coerceIn(0, value.text.length)
    val match = COMPOSER_MENTION_TRIGGER.find(value.text.substring(0, cursor))
        ?: return null
    if (match.range.last + 1 != cursor) return null
    val query = match.groupValues[1]
    if (query.length > MAX_COMPOSER_MENTION_QUERY_CHARS) return null
    return ComposerMentionQuery(
        start = cursor - query.length - 1,
        end = cursor,
        query = query,
    )
}

internal fun composerMentionCandidates(
    users: List<UserResponseData>,
): List<ComposerMentionSuggestion> {
    val seenUserUuids = HashSet<String>()
    return buildList {
        users.forEach { user ->
            val candidate = toComposerMentionSuggestion(user)
                ?: return@forEach
            if (seenUserUuids.add(candidate.userUuid)) add(candidate)
        }
    }
}

internal fun filterComposerMentionSuggestions(
    candidates: List<ComposerMentionSuggestion>,
    query: String,
    maxResults: Int = MAX_COMPOSER_MENTION_RESULTS,
): List<ComposerMentionSuggestion> {
    if (maxResults <= 0) return emptyList()
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    if (normalizedQuery.isEmpty()) return candidates.take(maxResults)

    val uuidMatches = ArrayList<ComposerMentionSuggestion>(maxResults)
    val usernameMatches = ArrayList<ComposerMentionSuggestion>(maxResults)
    val displayNameMatches = ArrayList<ComposerMentionSuggestion>(maxResults)
    val emailMatches = ArrayList<ComposerMentionSuggestion>(maxResults)
    for (candidate in candidates) {
        when {
            normalizedQuery in candidate.userUuid.lowercase(Locale.ROOT) ->
                if (uuidMatches.size < maxResults) {
                    uuidMatches += candidate
                }

            normalizedQuery in candidate.username.lowercase(Locale.ROOT) ->
                if (usernameMatches.size < maxResults) {
                    usernameMatches += candidate
                }

            normalizedQuery in candidate.displayName.lowercase(Locale.ROOT) ->
                if (displayNameMatches.size < maxResults) {
                    displayNameMatches += candidate
                }

            normalizedQuery in candidate.email.lowercase(Locale.ROOT) ->
                if (emailMatches.size < maxResults) {
                    emailMatches += candidate
                }
        }
    }
    return (uuidMatches + usernameMatches + displayNameMatches + emailMatches)
        .take(maxResults)
}

internal fun insertComposerMention(
    value: TextFieldValue,
    query: ComposerMentionQuery,
    suggestion: ComposerMentionSuggestion,
): TextFieldValue {
    if (detectComposerMentionQuery(value) != query) return value
    val userUuid = canonicalComposerMentionUuid(suggestion.userUuid)
        ?: return value
    val displayName = suggestion.displayName.trim()
        .take(MAX_COMPOSER_MENTION_FIELD_CHARS)
    if (displayName.isEmpty()) return value
    val start = query.start.coerceIn(0, value.text.length)
    val end = query.end.coerceIn(start, value.text.length)
    val mention = "[${escapeWorkspaceMarkdownInline(displayName)}]" +
        "(urn:user:$userUuid) "
    val nextText = value.text.replaceRange(start, end, mention)
    val cursor = start + mention.length
    return TextFieldValue(
        text = nextText,
        selection = TextRange(cursor),
    )
}

@Composable
internal fun ComposerMentionSuggestions(
    suggestions: List<ComposerMentionSuggestion>,
    onSelect: (ComposerMentionSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return

    val colors = LocalWorkspaceColorsPalette.current
    val title = stringResource(R.string.message_composer_mentions_title)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics { paneTitle = title },
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        tonalElevation = 6.dp,
    ) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 240.dp),
        ) {
            items(
                items = suggestions,
                key = ComposerMentionSuggestion::userUuid,
            ) { suggestion ->
                val description = stringResource(
                    R.string.message_composer_mention_item_description,
                    suggestion.displayName,
                    suggestion.username,
                )
                TextButton(
                    onClick = { onSelect(suggestion) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = description
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    color = if (suggestion.status == "active") {
                                        colors.indicatorGreen
                                    } else {
                                        colors.textAdditional30
                                    },
                                    shape = RoundedCornerShape(50),
                                ),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = suggestion.displayName,
                                color = colors.textHeaders,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "@${suggestion.username}",
                                color = colors.textAdditional50,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun toComposerMentionSuggestion(
    user: UserResponseData,
): ComposerMentionSuggestion? {
    val userUuid = canonicalComposerMentionUuid(user.uuid) ?: return null
    if (userUuid == SYSTEM_WORKSPACE_USER_UUID) return null
    val username = user.username.trim()
        .take(MAX_COMPOSER_MENTION_FIELD_CHARS)
    val displayName = user.displayableName().trim()
        .ifEmpty { username }
        .take(MAX_COMPOSER_MENTION_FIELD_CHARS)
    if (username.isEmpty() || displayName.isEmpty()) return null
    return ComposerMentionSuggestion(
        userUuid = userUuid,
        displayName = displayName,
        username = username,
        email = user.email.orEmpty().trim()
            .take(MAX_COMPOSER_MENTION_FIELD_CHARS),
        status = user.status.trim()
            .take(MAX_COMPOSER_MENTION_STATUS_CHARS),
    )
}

private fun canonicalComposerMentionUuid(value: String): String? =
    runCatching {
        UUID.fromString(value.trim()).toString()
    }.getOrNull()

private val COMPOSER_MENTION_TRIGGER =
    Regex("""(?:^|[\s(\[{,.:;!?])@(\S*)$""")
private const val SYSTEM_WORKSPACE_USER_UUID =
    "00000000-0000-0000-0000-000000000000"
private const val MAX_COMPOSER_MENTION_QUERY_CHARS = 128
private const val MAX_COMPOSER_MENTION_RESULTS = 8
private const val MAX_COMPOSER_MENTION_FIELD_CHARS = 256
private const val MAX_COMPOSER_MENTION_STATUS_CHARS = 64
