package com.projectronin.ehr.dataauthority.change.model

import com.projectronin.ehr.dataauthority.models.ChangeType
import java.util.UUID

data class ChangeStatus(
    val resourceType: String,
    val resourceId: String,
    val type: ChangeType,
    val hashId: UUID?,
    val hash: Int
)
