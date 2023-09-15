package com.projectronin.ehr.dataauthority.local

import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import com.projectronin.ehr.dataauthority.change.data.services.ResourceHashDAOService
import org.springframework.context.annotation.Profile
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Map DAO responsible for managing resource hashes for the local storage.
 */
@Profile("local")
class LocalStorageMapHashDAO : ResourceHashDAOService {
    private var localStorageHash: MutableMap<Triple<String, String, String>, Int> = mutableMapOf()

    override fun getHash(tenantId: String, resourceType: String, resourceId: String): ResourceHashesDO? {
        val hashFound = localStorageHash[Triple(tenantId, resourceType, resourceId)]

        return hashFound?.let {
            val uuid = UUID.randomUUID()
            ResourceHashesDO {
                this.hashId = uuid
                this.resourceId = resourceId
                this.resourceType = resourceType
                this.tenantId = tenantId
                this.updateDateTime = OffsetDateTime.now(ZoneOffset.UTC)
                this.hash = hashFound
            }
        }
    }

    override fun upsertHash(resourceHashesDO: ResourceHashesDO): ResourceHashesDO {
        localStorageHash[Triple(resourceHashesDO.tenantId!!, resourceHashesDO.resourceType, resourceHashesDO.resourceId)] =
            resourceHashesDO.hash
        return resourceHashesDO
    }

    override fun deleteHash(tenantId: String, resourceType: String, resourceId: String): Boolean {
        return localStorageHash.keys.remove(Triple(tenantId, resourceType, resourceId))
    }

    override fun deleteAllOfHash(): Boolean {
        localStorageHash.clear()
        return localStorageHash.isEmpty()
    }
}
