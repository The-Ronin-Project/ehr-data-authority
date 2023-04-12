package com.projectronin.ehr.dataauthority.model

data class FailedResource(
    override val resourceType: String,
    override val resourceId: String,
    val error: String
) : ResourceResponse
