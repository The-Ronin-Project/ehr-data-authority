package com.projectronin.ehr.dataauthority.change.data.model

import org.ktorm.entity.Entity
import java.time.OffsetDateTime
import java.util.UUID

interface ResourceHashesDO : Entity<ResourceHashesDO> {
    companion object : Entity.Factory<ResourceHashesDO>()

    var hashId: UUID?
    var resourceId: String
    var resourceType: String
    var tenantId: String
    var hash: Int
    var updateDateTime: OffsetDateTime
}
