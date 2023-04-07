package com.projectronin.ehr.dataauthority.client.models

data class BatchResourceResponse(
    val succeeded: List<SucceededResource> = emptyList(),
    val failed: List<FailedResource> = emptyList()
)

data class SucceededResource(
    override val resourceType: String,
    override val resourceId: String,
    val modificationType: ModificationType
) : ResourceResponse

data class FailedResource(
    override val resourceType: String,
    override val resourceId: String,
    val error: String
) : ResourceResponse

sealed interface ResourceResponse {
    val resourceType: String
    val resourceId: String
}
