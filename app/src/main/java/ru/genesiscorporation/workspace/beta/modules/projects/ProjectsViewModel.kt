package ru.genesiscorporation.workspace.beta.modules.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.FoldersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.LoginRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ProjectResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.ProjectsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TokenRefreshRequest
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import java.time.LocalDateTime

class ProjectsViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    val isFirstOrganization: Boolean
): ViewModel() {


    private val _projectsQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val projectsQueryState: StateFlow<QueryState> = _projectsQueryState
    private val _queryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val queryState: StateFlow<QueryState> = _queryState

    private val _projects = MutableStateFlow<List<ProjectResponseData>>(emptyList())
    val projects: StateFlow<List<ProjectResponseData>> = _projects.asStateFlow()

    private val _selectedProject = MutableStateFlow<ProjectResponseData?>(null)
    val selectedProject: StateFlow<ProjectResponseData?> = _selectedProject.asStateFlow()

    init {
        viewModelScope.launch {
            getProjects()
        }
    }
    fun setSelectedProject(project: ProjectResponseData?) {
        _selectedProject.update { project }
    }

    suspend fun getProjects() {
        _projectsQueryState.value = QueryState.Loading
        val response = client.performRequest(ProjectsRequest())
        when(response) {
            is ApiResult.Success -> {
                _projects.update { response.value }
                _selectedProject.update { response.value.firstOrNull() }
                _projectsQueryState.value = QueryState.Success
            }

            is ApiResult.Error -> {
                _projectsQueryState.value = QueryState.Error("")
            }
        }
    }

    suspend fun onLoginClick() {
        val selectedProject = _selectedProject.value ?: return
        refreshToken(selectedProject.uuid)
    }

    suspend fun refreshToken(projectUuid: String) {
        _queryState.value = QueryState.Loading
        val response = client.performRequest(TokenRefreshRequest(userViewModel.refreshToken.value ?: "", "openid email profile project:$projectUuid"))
        when(response) {
            is ApiResult.Success -> {
                val userResponse = response.value
                val saved = userViewModel.setTokensAndWait(
                    accessToken = userResponse.accessToken,
                    refreshToken = userResponse.refreshToken,
                )
                userViewModel.updateSelectedServerProjectUuid(_selectedProject.value?.uuid ?: "")
                _queryState.value = if (saved) {
                    QueryState.Success
                } else {
                    QueryState.Error("Error")
                }
            }
            is ApiResult.Error -> {
                _queryState.value = QueryState.Error(response.error.message ?: "Произошла ошибка, повторите позднее")
            }
        }
    }
}