package com.projectronin.ehr.dataauthority.model

data class IdentifierSearchResponse(
    val searchedIdentifier: Identifier,
    val foundResources: List<FoundResourceIdentifiers>
)
