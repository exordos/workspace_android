package com.exordos.workspace.modules.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.exordos.workspace.UserViewModel
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.FolderResponseData
import com.exordos.workspace.data.remote.dto.FoldersRequest
import com.exordos.workspace.data.remote.dto.LoginRequest
import com.exordos.workspace.data.remote.dto.ProjectResponseData
import com.exordos.workspace.data.remote.dto.ProjectsRequest
import com.exordos.workspace.data.remote.dto.TokenRefreshRequest
import com.exordos.workspace.modules.chooseserver.QueryState

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
        val serverId = userViewModel.selectedServer.value?.id ?: return
        val projectName = _projects.value.firstOrNull { it.uuid == projectUuid }?.name
        _queryState.value = QueryState.Loading
        val response = client.performRequest(TokenRefreshRequest(userViewModel.getTokens(serverId)?.refreshToken ?: "", "openid email profile project:$projectUuid"), serverId)
        when(response) {
            is ApiResult.Success -> {
                val userResponse = response.value
                val saved = userViewModel.setTokensAndWait(
                    accessToken = userResponse.accessToken,
                    refreshToken = userResponse.refreshToken,
                    serverId = serverId,
                )
                if (saved) userViewModel.updateSelectedServerProjectUuid(projectUuid, projectName, serverId)
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
