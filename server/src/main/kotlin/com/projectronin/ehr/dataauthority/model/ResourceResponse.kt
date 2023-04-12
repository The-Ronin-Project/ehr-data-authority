package com.projectronin.ehr.dataauthority.model

sealed interface ResourceResponse {
    val resourceType: String
    val resourceId: String
}
