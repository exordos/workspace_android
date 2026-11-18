package com.exordos.workspace.data.remote

/** HTTP decodes the wire DTO before exposing presentation data to the caller. */
interface UIORequest<RequestData, ResponseDTO, UIO, ResponseError> :
    ApiRequest<RequestData, ResponseDTO, ResponseError> {
    fun toUio(dto: ResponseDTO): UIO
}
