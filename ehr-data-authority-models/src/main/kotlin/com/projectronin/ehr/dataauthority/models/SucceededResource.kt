package com.projectronin.ehr.dataauthority.models

data class SucceededResource(
    override val resourceType: String,
    override val resourceId: String,
    val modificationType: ModificationType,
) : ResourceResponse
