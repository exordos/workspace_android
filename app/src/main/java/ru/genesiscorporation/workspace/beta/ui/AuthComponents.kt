package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R

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
    val disabled: Color,
    val onDisabled: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
)

@Composable
fun authColors(): AuthColors =
    if (isSystemInDarkTheme()) {
        AuthColors(
            background = Color(0xFF1B1B1D),
            field = Color(0xFF28282B),
            logoBackground = Color(0xFF2D2D30),
            text = Color(0xFFF8F8F9),
            mutedText = Color(0xFF9A9A9F),
            labelText = Color(0xFF737378),
            divider = Color(0xFF343438),
            accent = Color(0xFFFF8138),
            disabled = Color(0xFF555558),
            onDisabled = Color(0xFF8A8A8E),
            error = Color(0xFFFF4248),
            errorContainer = Color(0xFFFFE7E8),
            onErrorContainer = Color(0xFFDE2B32),
        )
    } else {
        AuthColors(
            background = Color(0xFFF8F8FA),
            field = Color(0xFFFFFFFF),
            logoBackground = Color(0xFFEFEFF2),
            text = Color(0xFF1B1B1D),
            mutedText = Color(0xFF68686E),
            labelText = Color(0xFF606066),
            divider = Color(0xFFDEDEE3),
            accent = Color(0xFFE96520),
            disabled = Color(0xFFE1E1E5),
            onDisabled = Color(0xFFA0A0A6),
            error = Color(0xFFD92D35),
            errorContainer = Color(0xFFFFECEE),
            onErrorContainer = Color(0xFFB4232A),
        )
    }

@Composable
fun AuthScreen(
    colors: AuthColors,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
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

@Composable
fun AuthErrorBanner(
    message: String,
    colors: AuthColors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
) {
    Box(
        modifier = modifier
            .size(116.dp)
            .background(colors.logoBackground, RoundedCornerShape(14.dp))
            .padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.icon),
            contentDescription = "Exordos Workspace",
            modifier = Modifier.fillMaxSize(),
        )
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
                onDone = {
                    onImeAction?.invoke()
                },
            ),
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
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
            contentColor = Color(0xFF171719),
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
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.error),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.error,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_logout),
            contentDescription = null,
            tint = colors.error,
            modifier = Modifier.size(25.dp),
        )
        Text(
            text = "Выйти из организации",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
