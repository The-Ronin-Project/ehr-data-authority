package com.projectronin.ehr.dataauthority.testclients

import com.projectronin.ehr.dataauthority.change.data.ResourceHashesDAO
import com.projectronin.ehr.dataauthority.change.data.binding.ResourceHashesDOs
import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import org.ktorm.database.Database
import org.ktorm.dsl.deleteAll
import java.time.OffsetDateTime

object DBClient {
    private val database = Database.connect("jdbc:mysql://springuser:ThePassword@localhost:3306/dataauthority-db")
    private val resourceHashesDAO = ResourceHashesDAO(database)

    fun purgeHashes() = database.deleteAll(ResourceHashesDOs)

    fun getStoredHashValue(tenantId: String, resourceType: String, resourceId: String): Int? =
        resourceHashesDAO.getHash(tenantId, resourceType, resourceId)?.hash

    fun setHashValue(tenantId: String, resourceType: String, resourceId: String, hash: Int) =
        resourceHashesDAO.insertHash(
            ResourceHashesDO {
                this.tenantId = tenantId
                this.resourceType = resourceType
                this.resourceId = resourceId
                this.hash = hash
                updateDateTime = OffsetDateTime.now()
            }
        )
}
