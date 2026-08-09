package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.ui.ReferenceMessage
import ru.genesiscorporation.workspace.beta.ui.ReferenceMessageBase
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SendMessageView(
    viewModel: ChatDialogViewModel
) {
    val scope = rememberCoroutineScope()
    val imageUri by viewModel.imageUri.collectAsState()
    val editingMessageBackupText by viewModel.editingMessageBackupText.collectAsState()
    val quotedMessage by viewModel.currentQuotedMessage.collectAsState()
    val quotedMessages by viewModel.quotedMessages.collectAsState()
    val mentionUsers by viewModel.users.collectAsState()
    val baseUrl by viewModel.userViewModel.baseUrl.collectAsState()
    val context = LocalContext.current
    val editorFocusRequester = remember { FocusRequester() }
    val mentionQuery = viewModel.mentions.activeAtQuery()?.second
    val mentionCandidates = remember(mentionUsers) {
        composerMentionCandidates(mentionUsers)
    }
    val mentionSuggestions = remember(mentionCandidates, mentionQuery) {
        mentionQuery?.let { query ->
            filterComposerMentionSuggestions(
                candidates = mentionCandidates,
                query = query,
            )
        }.orEmpty()
    }
    val launcher = rememberLauncherForActivityResult(
        contract =
            ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.onImageUriChange(uri)
    }
    Column(
        modifier = Modifier
            .background(LocalWorkspaceColorsPalette.current.surface)
    ) {
        if (imageUri != null) {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 0.dp)
                    .size(100.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_close_small),
                    contentDescription = "Close",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clickable { viewModel.onImageUriChange(null) },
                )
            }
        }
        val message = editingMessageBackupText
        val currentlyQuotedMessage = quotedMessage
        if (message != null) {
            ReferenceMessageBase(
                Modifier.weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
                shouldClose = true,
                onCloseTap = { viewModel.clearEditingMessage() }
            ) {
                ReferenceMessage("Редактирование", message)
            }
        } else if (currentlyQuotedMessage != null){
            if (quotedMessages.count() > 1) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quotedMessages.forEach { quotedMessage ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = LocalWorkspaceColorsPalette.current.divider,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (quotedMessage == currentlyQuotedMessage) LocalWorkspaceColorsPalette.current.cardBackgroundActive else LocalWorkspaceColorsPalette.current.surface
                                )
                                .padding(8.dp)
                                .clickable( onClick = { viewModel.onClickOnQuotedMessage(quotedMessage) })
                        ) {
                            Text(
                                quotedMessage.message.user?.displayableName() ?: "Пользователь",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (quotedMessage == currentlyQuotedMessage) LocalWorkspaceColorsPalette.current.textHeaders else LocalWorkspaceColorsPalette.current.textHeaders
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_close_small),
                                contentDescription = "Close",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { viewModel.clearQuotingMessage(quotedMessage) },
                            )
                        }
                    }
                }
            }
            ReferenceMessageBase(
                Modifier.weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shouldClose = true,
                onCloseTap = { viewModel.clearQuotingMessage(currentlyQuotedMessage) }
            ) {
                ReferenceMessage("Ответить", currentlyQuotedMessage.message.payload.content)
            }
        }
        ComposerMentionSuggestions(
            suggestions = mentionSuggestions,
            baseUrl = baseUrl.orEmpty(),
            authHeaders = viewModel.client.authHeaders(),
            onSelect = { suggestion ->
                viewModel.onUserSelected(
                    name = suggestion.displayName,
                    urn = "urn:user:${suggestion.userUuid}",
                )
                editorFocusRequester.requestFocus()
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp)
                .background(LocalWorkspaceColorsPalette.current.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .weight(1f)
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        LocalWorkspaceColorsPalette.current.background,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            launcher.launch("image/*")
                        },
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = LocalWorkspaceColorsPalette.current.iconBase
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.attach_file),
                            contentDescription = "Attach file"
                        )
                    }
                    BasicTextField(
                        value = viewModel.mentions.value,
                        onValueChange = viewModel::onMentionsChange,
                        textStyle = TextStyle(
                            color = LocalWorkspaceColorsPalette.current.textHeaders,
                            fontSize = 16.sp,
                            fontFamily = InterFontFamily,
                        ),
                        cursorBrush = SolidColor(LocalWorkspaceColorsPalette.current.textHeaders),
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .focusRequester(editorFocusRequester),
                        visualTransformation = MentionVisualTransformation(SpanStyle(color = Color(0xFF1565C0))),
                        decorationBox =
                    { innerTextField -> innerTextField() }
                    )
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        viewModel.onSendClicked(context)
                    }
                },
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalWorkspaceColorsPalette.current.primary,
                    contentColor = LocalWorkspaceColorsPalette.current.onPrimary
                )
            ) {
                if (viewModel.editingMessage == null) {
                    Icon(
                        painter = painterResource(id = R.drawable.send),
                        contentDescription = "Send"
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = "Send edit"
                    )
                }
            }
        }
    }
}
private val MentionPattern =
    Regex("""\[([^\[\]]+)]\((urn:user:[^)]+)\)""")

data class MentionSpan(
    val range: IntRange,
    val displayName: String,
    val urn: String,
) {
    val start: Int get() = range.first
    val endExclusive: Int get() = range.last + 1
}

fun findMentions(text: String): List<MentionSpan> =
    MentionPattern.findAll(text).map { match ->
        MentionSpan(
            range = match.range,
            displayName = match.groupValues[1],
            urn = match.groupValues[2],
        )
    }.toList()

fun formatMention(displayName: String, urn: String): String =
    "[$displayName]($urn)"

@Stable
class MentionTextFieldState(initialText: String = "") {
    var value by mutableStateOf(TextFieldValue(initialText))
        private set

    val text: String get() = value.text

    fun onValueChange(incoming: TextFieldValue) {
        value = applyMentionAwareChange(value, incoming)
    }

    fun insertText(text: String) {
        if (text.isEmpty()) return
        val s = value.selection.min
        val e = value.selection.max
        val newText = value.text.replaceRange(s, e, text)
        value = TextFieldValue(newText, TextRange(s + text.length))
    }

    fun appendText(text: String) {
        if (text.isEmpty()) return
        val newText = value.text + text
        value = TextFieldValue(newText, TextRange(newText.length))
    }

    fun setText(text: String) {
        value = TextFieldValue(text, TextRange(text.length))
    }

    fun insertMention(displayName: String, urn: String, trailingSpace: Boolean = true) {
        val token = formatMention(displayName, urn)
        insertText(if (trailingSpace) "$token " else token)
    }

    fun insertMentionFromAtQuery(
        displayName: String,
        urn: String,
        trailingSpace: Boolean = true,
    ): Boolean {
        val caret = value.selection.max
        val at = findActiveAtQueryStart(value.text, caret) ?: return false
        value = TextFieldValue(
            text = value.text.removeRange(at, caret),
            selection = TextRange(at),
        )
        insertMention(displayName, urn, trailingSpace)
        return true
    }

    fun activeAtQuery(): Pair<Int, String>? {
        val caret = value.selection.max
        val at = findActiveAtQueryStart(value.text, caret) ?: return null
        return at to value.text.substring(at + 1, caret)
    }
}

@Composable
fun rememberMentionTextFieldState(initialText: String = ""): MentionTextFieldState =
    remember { MentionTextFieldState(initialText) }

private class MentionVisualTransformation(
    private val mentionStyle: SpanStyle,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        buildMentionTransformedText(text.text, mentionStyle)
}

internal fun buildMentionTransformedText(
    original: String,
    mentionStyle: SpanStyle,
): TransformedText {
    val mentions = findMentions(original)
    if (mentions.isEmpty()) {
        return TransformedText(AnnotatedString(original), OffsetMapping.Identity)
    }

    val originalToTransformed = IntArray(original.length + 1)
    val transformed = buildAnnotatedString {
        var o = 0
        var t = 0
        var mentionIndex = 0

        while (o < original.length) {
            val mention = mentions.getOrNull(mentionIndex)
            if (mention != null && o == mention.start) {
                val rawLen = mention.endExclusive - mention.start
                val chip = "@${mention.displayName}"
                val chipLen = chip.length

                originalToTransformed[o] = t
                for (i in 1..rawLen) {
                    originalToTransformed[o + i] = t + chipLen
                }

                withStyle(mentionStyle) { append(chip) }

                o += rawLen
                t += chipLen
                mentionIndex++
            } else {
                originalToTransformed[o] = t
                append(original[o])
                o++
                t++
            }
        }
        originalToTransformed[original.length] = t
    }

    val transformedToOriginal = IntArray(transformed.length + 1)
    run {
        var o = 0
        var t = 0
        var mentionIndex = 0
        while (o < original.length) {
            val mention = mentions.getOrNull(mentionIndex)
            if (mention != null && o == mention.start) {
                val rawLen = mention.endExclusive - mention.start
                val chipLen = 1 + mention.displayName.length

                transformedToOriginal[t] = o
                for (j in 1..chipLen) {
                    transformedToOriginal[t + j] = o + rawLen
                }

                o += rawLen
                t += chipLen
                mentionIndex++
            } else {
                transformedToOriginal[t] = o
                o++
                t++
            }
        }
        transformedToOriginal[transformed.length] = original.length
    }

    return TransformedText(
        text = transformed,
        offsetMapping = ArrayOffsetMapping(originalToTransformed, transformedToOriginal),
    )
}

private class ArrayOffsetMapping(
    private val originalToTransformed: IntArray,
    private val transformedToOriginal: IntArray,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int =
        originalToTransformed[offset.coerceIn(0, originalToTransformed.lastIndex)]

    override fun transformedToOriginal(offset: Int): Int =
        transformedToOriginal[offset.coerceIn(0, transformedToOriginal.lastIndex)]
}

internal fun applyMentionAwareChange(
    old: TextFieldValue,
    incoming: TextFieldValue,
): TextFieldValue {
    if (old.text == incoming.text) {
        return snapSelectionAwayFromMentionInterior(incoming)
    }

    val oldText = old.text
    val newText = incoming.text

    var start = 0
    val minLen = min(oldText.length, newText.length)
    while (start < minLen && oldText[start] == newText[start]) start++

    var oldEnd = oldText.length
    var newEnd = newText.length
    while (oldEnd > start && newEnd > start && oldText[oldEnd - 1] == newText[newEnd - 1]) {
        oldEnd--
        newEnd--
    }

    if (oldEnd > start) {
        val mentions = findMentions(oldText)
        var delStart = start
        var delEnd = oldEnd

        for (mention in mentions) {
            val ms = mention.start
            val me = mention.endExclusive
            val overlaps = delStart < me && delEnd > ms
            val fullyContained = delStart <= ms && delEnd >= me
            if (overlaps && !fullyContained) {
                delStart = min(delStart, ms)
                delEnd = max(delEnd, me)
            }
        }

        if (delStart != start || delEnd != oldEnd) {
            val replacement = newText.substring(start, newEnd)
            val result = buildString(oldText.length - (delEnd - delStart) + replacement.length) {
                append(oldText, 0, delStart)
                append(replacement)
                append(oldText, delEnd, oldText.length)
            }
            return TextFieldValue(result, TextRange(delStart + replacement.length))
        }
    }

    return snapSelectionAwayFromMentionInterior(incoming)
}

private fun snapSelectionAwayFromMentionInterior(value: TextFieldValue): TextFieldValue {
    val mentions = findMentions(value.text)
    if (mentions.isEmpty()) return value

    fun snap(index: Int): Int {
        val hit = mentions.firstOrNull { index > it.start && index < it.endExclusive }
            ?: return index
        val mid = (hit.start + hit.endExclusive) / 2
        return if (index < mid) hit.start else hit.endExclusive
    }

    val sel = value.selection
    val start = snap(sel.start)
    val end = snap(sel.end)
    return if (start == sel.start && end == sel.end) value
    else value.copy(selection = TextRange(start, end))
}

private fun findActiveAtQueryStart(text: String, caret: Int): Int? {
    if (caret <= 0 || caret > text.length) return null
    val before = text.substring(0, caret)
    val at = before.lastIndexOf('@')
    if (at < 0) return null
    if (at > 0 && before[at - 1] !in mentionTriggerBoundaries) return null
    val query = before.substring(at + 1)
    if (query.length > maxMentionQueryChars || query.any { it.isWhitespace() }) return null
    return at
}

private val mentionTriggerBoundaries = setOf(' ', '\n', '\t', '(', '[', '{', ',', '.', ':', ';', '!', '?')
private const val maxMentionQueryChars = 128
