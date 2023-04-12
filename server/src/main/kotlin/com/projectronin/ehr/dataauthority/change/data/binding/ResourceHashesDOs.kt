package com.projectronin.ehr.dataauthority.change.data.binding

import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import com.projectronin.interop.common.ktorm.binding.binaryUuid
import com.projectronin.interop.common.ktorm.binding.utcDateTime
import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.varchar

object ResourceHashesDOs : Table<ResourceHashesDO>("resource_hashes") {
    var hashId = binaryUuid("hash_id").bindTo { it.hashId }
    var resourceId = varchar("resource_id").bindTo { it.resourceId }
    var resourceType = varchar("resource_type").bindTo { it.resourceType }
    var tenantId = varchar("tenant_id").bindTo { it.tenantId }
    var hash = int("hash").bindTo { it.hash }
    var updateDateTime = utcDateTime("update_dt_tm").bindTo { it.updateDateTime }
}
