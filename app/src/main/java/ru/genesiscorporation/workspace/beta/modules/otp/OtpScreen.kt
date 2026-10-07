package ru.genesiscorporation.workspace.beta.modules.otp

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import ru.genesiscorporation.workspace.beta.LoginFlow
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.AuthColors
import ru.genesiscorporation.workspace.beta.ui.AuthLoadingOverlay
import ru.genesiscorporation.workspace.beta.ui.AuthScreen
import ru.genesiscorporation.workspace.beta.ui.authColors

@Composable
fun OtpScreen(viewModel: OtpViewModel, navController: NavHostController) {
    val otp by viewModel.otpText.collectAsState()
    val state by viewModel.queryState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = authColors()

    LaunchedEffect(state) {
        when (state) {
            is QueryState.Error -> Toast.makeText(context, "Код введён неверно", Toast.LENGTH_SHORT).show()
            QueryState.Success -> navController.navigate(LoginFlow.Projects(viewModel.isFirstOrganization))
            else -> Unit
        }
    }
    AuthScreen(colors = colors) {
        OtpContent(
            otp = otp,
            enabled = state !is QueryState.Loading,
            colors = colors,
            onOtpChange = viewModel::onOtpTextChange,
            onBack = { navController.navigateUp() },
        )
    }
    if (state is QueryState.Loading) AuthLoadingOverlay(colors)
}

@Composable
internal fun OtpContent(
    otp: String,
    enabled: Boolean,
    colors: AuthColors,
    onOtpChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(180)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Spacer(Modifier.height(76.dp))
    Text(
        text = "Введите код",
        color = colors.text,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "Введите 6-значный код из\nприложения-аутентификатора",
        color = colors.mutedText,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 10.dp),
    )
    Spacer(Modifier.height(64.dp))
    OtpCodeField(otp, enabled, colors, focusRequester, onOtpChange)
    Spacer(Modifier.height(40.dp))
    Text(
        text = "Вернуться к логину",
        color = colors.accent,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable(enabled = enabled, onClick = onBack).padding(vertical = 8.dp),
    )
}

@Composable
private fun OtpCodeField(
    value: String,
    enabled: Boolean,
    colors: AuthColors,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            .semantics { contentDescription = "6-значный код" },
        decorationBox = { innerTextField ->
            Box {
                Row(
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(6) { index ->
                        Box(
                            modifier = Modifier.weight(1f).height(64.dp)
                                .background(colors.field, RoundedCornerShape(10.dp))
                                .border(
                                    1.dp,
                                    if (focused && index == value.length) colors.accent else Color.Transparent,
                                    RoundedCornerShape(10.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(value.getOrNull(index)?.toString().orEmpty(), color = colors.text, fontSize = 24.sp, lineHeight = 28.sp)
                        }
                    }
                }
                Box(Modifier.size(1.dp).alpha(0.01f)) { innerTextField() }
            }
        },
    )
}
