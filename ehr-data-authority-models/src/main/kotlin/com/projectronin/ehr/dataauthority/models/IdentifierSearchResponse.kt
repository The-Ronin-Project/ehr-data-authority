package com.projectronin.ehr.dataauthority.models

import io.swagger.v3.oas.annotations.media.Schema

data class IdentifierSearchResponse(
    @Schema(name = "identifier")
    val searchedIdentifier: Identifier,
    val foundResources: List<FoundResourceIdentifiers>,
)
