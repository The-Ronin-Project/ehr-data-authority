package com.projectronin.ehr.dataauthority.models

import io.swagger.v3.oas.annotations.media.Schema

data class FoundResourceIdentifiers(
    @Schema(name = "udpId", example = "tenant-12345")
    val udpId: String,
    @Schema(name = "identifiers")
    val identifiers: List<Identifier>,
)
