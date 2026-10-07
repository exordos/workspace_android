package ru.genesiscorporation.workspace.beta.modules.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.LoginFlow
import ru.genesiscorporation.workspace.beta.data.remote.dto.ProjectResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.AuthColors
import ru.genesiscorporation.workspace.beta.ui.AuthLoadingOverlay
import ru.genesiscorporation.workspace.beta.ui.AuthPrimaryButton
import ru.genesiscorporation.workspace.beta.ui.AuthLazyScreen
import ru.genesiscorporation.workspace.beta.ui.authColors

@Composable
fun ProjectsScreen(viewModel: ProjectsViewModel, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val projects by viewModel.projects.collectAsState()
    val selectedProject by viewModel.selectedProject.collectAsState()
    val state by viewModel.projectsQueryState.collectAsState()
    val loginState by viewModel.queryState.collectAsState()
    ProjectsContent(
        projects = projects,
        selectedProject = selectedProject,
        state = state,
        loginState = loginState,
        onProjectSelected = viewModel::setSelectedProject,
        onRetry = { scope.launch { viewModel.getProjects() } },
        onOpenProject = { scope.launch { viewModel.onLoginClick() } },
        onReturnToLogin = { navController.returnToCredentials() },
    )
}

internal fun NavHostController.returnToCredentials() {
    popBackStack<LoginFlow.Login>(inclusive = false)
}

@Composable
internal fun ProjectsContent(
    projects: List<ProjectResponseData>,
    selectedProject: ProjectResponseData?,
    state: QueryState,
    loginState: QueryState,
    onProjectSelected: (ProjectResponseData) -> Unit,
    onRetry: () -> Unit,
    onOpenProject: () -> Unit,
    onReturnToLogin: () -> Unit,
) {
    val colors = authColors()
    val loading = state is QueryState.Loading || loginState is QueryState.Loading

    AuthLazyScreen(colors = colors, errorMessage = (state as? QueryState.Error)?.message) {
        item(contentType = "projects-header") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(48.dp))
                Text("Выберите проект", color = colors.text, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Сообщения и настройки каждого проекта изолированы",
                    color = colors.mutedText,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp, bottom = 28.dp),
                )
            }
        }
        items(projects, key = { it.uuid }, contentType = { "project" }) { project ->
            ProjectCell(project, project.uuid == selectedProject?.uuid, colors, !loading, onProjectSelected)
        }
        item(contentType = "projects-actions") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state is QueryState.Error) {
                    AuthPrimaryButton(
                        text = "Повторить",
                        enabled = !loading,
                        colors = colors,
                        onClick = onRetry,
                    )
                } else if (state is QueryState.Success) {
                    Spacer(Modifier.height(16.dp))
                    AuthPrimaryButton(
                        text = "Открыть проект",
                        enabled = !loading && selectedProject != null,
                        colors = colors,
                        onClick = onOpenProject,
                    )
                }
                Text(
                    "Вернуться к логину",
                    color = colors.accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 14.dp)
                        .clickable(enabled = !loading, onClick = onReturnToLogin)
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
    if (loading) AuthLoadingOverlay(colors)
}

@Composable
fun ProjectCell(
    project: ProjectResponseData,
    isSelected: Boolean,
    colors: AuthColors = authColors(),
    enabled: Boolean = true,
    onProjectSelected: (ProjectResponseData) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) colors.logoBackground else colors.field, RoundedCornerShape(10.dp))
            .border(1.dp, if (isSelected) colors.accent else Color.Transparent, RoundedCornerShape(10.dp))
            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onProjectSelected(project) })
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            enabled = enabled,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.accent,
                unselectedColor = colors.mutedText,
                disabledSelectedColor = colors.disabled,
                disabledUnselectedColor = colors.mutedText,
            ),
        )
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(project.name, color = colors.text, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
            Text(
                text = "ID …${project.uuid.takeLast(8)}",
                color = colors.mutedText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (project.description.isNotBlank()) {
                Text(project.description, color = colors.mutedText, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
