package com.projectronin.ehr.dataauthority.models

data class BatchResourceResponse(
    val succeeded: List<SucceededResource> = emptyList(),
    val failed: List<FailedResource> = emptyList(),
)
