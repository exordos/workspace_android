package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.MontserratFontFamily

@Immutable
data class AuthColors(
    val background: Color,
    val field: Color,
    val logoBackground: Color,
    val text: Color,
    val mutedText: Color,
    val labelText: Color,
    val divider: Color,
    val accent: Color,
    val onAccent: Color,
    val disabled: Color,
    val onDisabled: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
)

@Composable
fun authColors(): AuthColors {
    val palette = LocalWorkspaceColorsPalette.current
    return AuthColors(
        background = palette.background,
        field = palette.searchBackground,
        logoBackground = palette.surface,
        text = palette.textHeaders,
        mutedText = palette.textAdditional50,
        labelText = palette.textAdditional50,
        divider = palette.divider,
        accent = palette.primary,
        onAccent = palette.onPrimary,
        disabled = palette.noticeDisable,
        onDisabled = palette.textHeaders,
        error = palette.indicatorRed,
        errorContainer = palette.indicatorRed.copy(alpha = 0.12f).compositeOver(palette.surface),
        onErrorContainer = palette.indicatorRed,
    )
}

@Composable
fun AuthScreen(
    colors: AuthColors,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val typography = authTypography()
    MaterialTheme(typography = typography) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(22.dp))
            Text(
                text = "Вход",
                color = colors.text,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                AuthErrorBanner(errorMessage, colors)
            }
            content()
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun authTypography() = MaterialTheme.typography.copy(
    bodyLarge = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
)

@Composable
fun AuthLazyScreen(
    colors: AuthColors,
    errorMessage: String? = null,
    content: LazyListScope.() -> Unit,
) {
    MaterialTheme(typography = authTypography()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(contentType = "auth-header") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = "Вход",
                        color = colors.text,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(16.dp))
                        AuthErrorBanner(errorMessage, colors)
                    }
                }
            }
            content()
            item(contentType = "auth-footer") { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
fun AuthErrorBanner(
    message: String,
    colors: AuthColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .background(colors.errorContainer, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "!",
            color = colors.onErrorContainer,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .border(1.dp, colors.onErrorContainer, RoundedCornerShape(20.dp))
                .padding(horizontal = 7.dp, vertical = 1.dp),
        )
        Text(
            text = message,
            color = colors.onErrorContainer,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
fun AuthLogo(
    colors: AuthColors,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    contentDescription: String? = "Exordos Workspace",
) {
    val fallback = painterResource(R.drawable.icon)
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .size(116.dp)
            .background(colors.logoBackground, shape)
            .clip(shape)
            .padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            Image(
                painter = fallback,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                placeholder = fallback,
                error = fallback,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    colors: AuthColors,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onImeAction: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val borderColor = when {
        error != null -> colors.error
        focused -> colors.accent
        else -> Color.Transparent
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = if (error != null) colors.error else colors.labelText,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = TextStyle(
                color = colors.text,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            ),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    if (onImeAction != null) {
                        onImeAction()
                    } else {
                        focusManager.moveFocus(FocusDirection.Down)
                    }
                },
                onDone = { onImeAction?.invoke() },
            ),
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    error?.let { message -> this.error(message) }
                }
                .background(colors.field, RoundedCornerShape(10.dp))
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 14.dp),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = colors.mutedText,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                            )
                        }
                        innerTextField()
                    }
                    trailingContent?.invoke()
                }
            },
        )
        if (error != null) {
            Text(
                text = error,
                color = colors.error,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun AuthPrimaryButton(
    text: String,
    enabled: Boolean,
    colors: AuthColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            disabledContainerColor = colors.disabled,
            disabledContentColor = colors.onDisabled,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun AuthLogoutButton(
    colors: AuthColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Выйти из организации",
    enabled: Boolean = true,
) {
    val contentColor = if (enabled) colors.error else colors.onDisabled
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, contentColor),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.error,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.onDisabled,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_logout),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(25.dp),
        )
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
fun AuthLoadingOverlay(
    colors: AuthColors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.75f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = colors.accent,
            trackColor = colors.field,
            modifier = Modifier.size(48.dp),
        )
    }
}
