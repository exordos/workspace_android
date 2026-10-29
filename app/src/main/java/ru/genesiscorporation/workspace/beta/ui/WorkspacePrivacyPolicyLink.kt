package ru.genesiscorporation.workspace.beta.ui

import android.widget.Toast
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun WorkspacePrivacyPolicyLink(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    TextButton(
        onClick = {
            try {
                uriHandler.openUri("https://exordos.com/legal/workspace-privacy")
            } catch (_: IllegalArgumentException) {
                Toast.makeText(context, R.string.privacy_policy_browser_unavailable, Toast.LENGTH_LONG).show()
            }
        },
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = LocalWorkspaceColorsPalette.current.primary,
        ),
    ) {
        Text(stringResource(R.string.privacy_policy_title))
    }
}
