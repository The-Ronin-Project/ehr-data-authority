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

enum class IdentifierSearchableResourceTypes {
    Patient,
    Location,
    Practitioner
}

data class IdentifierSearchResponse(
    val searchedIdentifier: Identifier,
    val foundResources: List<FoundResource>
)

data class FoundResource(
    val udpId: String,
    val identifier: List<Identifier>
)

data class Identifier(
    val system: String,
    val value: String
) {
    override fun toString(): String {
        return "$system|$value"
    }
}
