package com.projectronin.ehr.dataauthority.model

data class SucceededResource(
    override val resourceType: String,
    override val resourceId: String,
    val modificationType: ModificationType
) : ResourceResponse
