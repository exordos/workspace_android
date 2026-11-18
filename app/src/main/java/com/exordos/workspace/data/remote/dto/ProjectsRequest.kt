package com.exordos.workspace.data.remote.dto

import com.exordos.workspace.data.remote.UIORequest
import com.exordos.workspace.data.uio.toUio
import com.exordos.workspace.data.uio.ProjectUIO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

class ProjectsRequest(): UIORequest<EmptyRequestData, List<ProjectResponseData>, List<ProjectUIO>, ApiError> {
    override fun toUio(dto: List<ProjectResponseData>): List<ProjectUIO> = dto.map { it.toUio() }
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/core/v1/iam/projects/"
    override val data = EmptyRequestData()
}

@Serializable
data class ProjectResponseData(
    val uuid: String,
    val name: String,
    val description: String

)
