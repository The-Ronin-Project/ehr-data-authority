package com.projectronin.ehr.dataauthority.model

data class BatchResourceResponse(
    val succeeded: List<SucceededResource> = emptyList(),
    val failed: List<FailedResource> = emptyList()
)
