package com.projectronin.ehr.dataauthority.models

data class BatchResourceChangeResponse(
    val succeeded: List<ChangeStatusResource> = emptyList(),
    val failed: List<FailedResource> = emptyList(),
)
