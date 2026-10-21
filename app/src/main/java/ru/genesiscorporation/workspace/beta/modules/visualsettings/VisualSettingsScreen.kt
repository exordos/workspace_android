package ru.genesiscorporation.workspace.beta.modules.visualsettings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import ru.genesiscorporation.workspace.beta.data.push.WorkspacePushNotifications
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ProfileFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.modules.ownusersettings.ActionSheetExample
import ru.genesiscorporation.workspace.beta.modules.ownusersettings.OwnUserSettingsViewModel
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

class VisualSettingsScreen {
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualSettingsScreen(
    viewModel: VisualSettingsViewModel,
    navController: NavHostController
) {
    val streamsOrderIsUnreadFirst = viewModel.userViewModel.streamsOrderIsUnreadFirst.collectAsState()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalWorkspaceColorsPalette.current.background,
                    titleContentColor = LocalWorkspaceColorsPalette.current.textHeaders,
                ),
                title = {
                    Text("Внешний вид")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back),
                            contentDescription = "Back"
                        )
                    }
                },
                expandedHeight = 48.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { innerPadding ->
        Column (
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(LocalWorkspaceColorsPalette.current.background)
                .verticalScroll(rememberScrollState())
        ) {
            PushNotificationLimits()
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_elements_order),
                        contentDescription = null,
                        modifier = Modifier.padding(0.dp, 6.dp, 8.dp, 6.dp)
                    )
                    Text(
                        text = "Порядок стримов и топиков",
                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                        fontSize = 14.sp,
                        fontFamily = InterFontFamily,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .border(
                        width = 1.dp,
                        color = LocalWorkspaceColorsPalette.current.divider,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding( horizontal = 12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(
                            onClick = {
                                viewModel.userViewModel.setStreamsOrderIsUnreadFirst(false)
                            }
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding( vertical = 12.dp)
                    ) {
                        Text(
                            text = "По последнему сообщению",
                            color = LocalWorkspaceColorsPalette.current.textAdditional50,
                            fontSize = 14.sp,
                            fontFamily = InterFontFamily,
                            modifier = Modifier.weight(1f),
                        )
                        if (!streamsOrderIsUnreadFirst.value) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_settings_check),
                                contentDescription = null,
                            )
                        }
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = LocalWorkspaceColorsPalette.current.divider
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(
                            onClick = {
                                viewModel.userViewModel.setStreamsOrderIsUnreadFirst(true)
                            }
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding( vertical = 12.dp)
                    ) {
                        Text(
                            text = "Сначала непрочитанные",
                            color = LocalWorkspaceColorsPalette.current.textAdditional50,
                            fontSize = 14.sp,
                            fontFamily = InterFontFamily,
                            modifier = Modifier.weight(1f),
                        )
                        if (streamsOrderIsUnreadFirst.value) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_settings_check),
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PushNotificationLimits() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var limits by remember { mutableStateOf(WorkspacePushNotifications(context).limits()) }
    fun save() { scope.launch(Dispatchers.IO) { WorkspacePushNotifications(context).setLimits(limits.conversations, limits.messages) } }
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text(stringResource(R.string.push_display_limits), color = LocalWorkspaceColorsPalette.current.textHeaders)
        Text(stringResource(R.string.push_group_limit, limits.conversations))
        Slider(value = limits.conversations.toFloat(), onValueChange = { limits = limits.copy(conversations = it.toInt()) },
            valueRange = 1f..5f, steps = 3, onValueChangeFinished = { save() })
        Text(stringResource(R.string.push_message_limit, limits.messages))
        Slider(value = limits.messages.toFloat(), onValueChange = { limits = limits.copy(messages = it.toInt()) },
            valueRange = 1f..10f, steps = 8, onValueChangeFinished = { save() })
        Text(stringResource(R.string.push_limit_explanation), fontSize = 12.sp)
    }
}
