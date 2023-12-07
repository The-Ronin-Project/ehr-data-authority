package com.projectronin.ehr.dataauthority.models

data class ChangeStatusResource(
    override val resourceType: String,
    override val resourceId: String,
    val changeType: ChangeType,
) : ResourceResponse
