package com.projectronin.ehr.dataauthority.models

data class IdentifierSearchResponse(
    val searchedIdentifier: Identifier,
    val foundResources: List<FoundResourceIdentifiers>,
)
