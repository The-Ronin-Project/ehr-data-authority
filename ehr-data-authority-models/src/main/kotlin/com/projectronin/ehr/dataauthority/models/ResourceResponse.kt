package com.projectronin.ehr.dataauthority.models

sealed interface ResourceResponse {
    val resourceType: String
    val resourceId: String
}
