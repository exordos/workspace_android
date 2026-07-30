package ru.genesiscorporation.workspace.beta.modules.otp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import androidx.compose.ui.unit.dp

@Composable
fun OtpSymbolView(
    symbol: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(59.dp)
            .border(
                width = 1.dp,
                color = if (isActive) LocalWorkspaceColorsPalette.current.primary else  LocalWorkspaceColorsPalette.current.searchBackground,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                color =LocalWorkspaceColorsPalette.current.searchBackground,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            color = LocalWorkspaceColorsPalette.current.textHeaders,
            fontSize = 20.sp,
        )
    }
}
