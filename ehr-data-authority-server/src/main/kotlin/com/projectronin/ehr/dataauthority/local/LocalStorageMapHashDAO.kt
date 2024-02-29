package com.projectronin.ehr.dataauthority.local

import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import com.projectronin.ehr.dataauthority.change.data.services.ResourceHashDAOService
import com.projectronin.ehr.dataauthority.change.data.services.ResourceId
import com.projectronin.interop.common.collection.associateWithNonNull
import org.springframework.context.annotation.Profile
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Map DAO responsible for managing resource hashes for the local storage.
 */
@Profile("local")
class LocalStorageMapHashDAO : ResourceHashDAOService {
    private var localStorageHash: MutableMap<Pair<String, ResourceId>, Int> = mutableMapOf()

    override fun getHashes(
        tenantId: String,
        resourceIds: List<ResourceId>,
    ): Map<ResourceId, ResourceHashesDO> {
        return resourceIds.associateWithNonNull { resourceId ->
            val hash = localStorageHash[Pair(tenantId, resourceId)]
            hash?.let {
                val uuid = UUID.randomUUID()
                ResourceHashesDO {
                    this.hashId = uuid
                    this.resourceId = resourceId.id
                    this.resourceType = resourceId.type
                    this.tenantId = tenantId
                    this.updateDateTime = OffsetDateTime.now(ZoneOffset.UTC)
                    this.hash = it
                }
            }
        }
    }

    override fun upsertHash(resourceHashesDO: ResourceHashesDO): ResourceHashesDO {
        localStorageHash[
            Pair(
                resourceHashesDO.tenantId,
                ResourceId(
                    resourceHashesDO.resourceType,
                    resourceHashesDO.resourceId,
                ),
            ),
        ] =
            resourceHashesDO.hash
        return resourceHashesDO
    }

    override fun deleteHash(
        tenantId: String,
        resourceType: String,
        resourceId: String,
    ): Boolean {
        return localStorageHash.keys.remove(Pair(tenantId, ResourceId(resourceType, resourceId)))
    }

    override fun deleteAllOfHash(): Boolean {
        localStorageHash.clear()
        return localStorageHash.isEmpty()
    }
}
