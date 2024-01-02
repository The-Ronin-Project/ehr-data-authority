package com.projectronin.ehr.dataauthority.models

import io.swagger.v3.oas.annotations.media.Schema

data class FailedResource(
    @Schema(name = "resourceType", example = "Patient")
    override val resourceType: String,
    @Schema(name = "resourceId", example = "123e4567-e89b-12d3-a456-426614174000")
    override val resourceId: String,
    @Schema(name = "error", example = "failed to find resource")
    val error: String,
) : ResourceResponse
