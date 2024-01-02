package com.projectronin.ehr.dataauthority.models

import io.swagger.v3.oas.annotations.media.Schema

data class ChangeStatusResource(
    @Schema(name = "resourceType", example = "Patient")
    override val resourceType: String,
    @Schema(name = "resourceId", example = "123e4567-e89b-12d3-a456-426614174000")
    override val resourceId: String,
    val changeType: ChangeType,
) : ResourceResponse
