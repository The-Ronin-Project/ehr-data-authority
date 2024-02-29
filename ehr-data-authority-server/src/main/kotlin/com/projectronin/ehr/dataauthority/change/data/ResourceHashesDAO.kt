package com.projectronin.ehr.dataauthority.change.data

import com.projectronin.ehr.dataauthority.change.data.binding.ResourceHashesDOs
import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import com.projectronin.ehr.dataauthority.change.data.services.ResourceHashDAOService
import com.projectronin.ehr.dataauthority.change.data.services.ResourceId
import mu.KotlinLogging
import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.inList
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.where
import org.ktorm.support.mysql.insertOrUpdate
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * DAO responsible for managing resource hashes.
 */
@Repository
@Profile("default")
class ResourceHashesDAO(private val database: Database) : ResourceHashDAOService {
    private val logger = KotlinLogging.logger { }

    override fun getHashes(
        tenantId: String,
        resourceIds: List<ResourceId>,
    ): Map<ResourceId, ResourceHashesDO> {
        // We can't do a tuple here, so grouping by the type to reduce calls
        val resourceIdsByType = resourceIds.groupBy { it.type }

        return resourceIdsByType.flatMap { (type, resourceIds) ->
            val ids = resourceIds.map { it.id }
            database.from(ResourceHashesDOs).select()
                .where(
                    (ResourceHashesDOs.tenantId eq tenantId) and
                        (ResourceHashesDOs.resourceType eq type) and
                        (ResourceHashesDOs.resourceId inList ids),
                ).map { ResourceHashesDOs.createEntity(it) }.associateBy { ResourceId(it.resourceType, it.resourceId) }
                .toList()
        }.toMap()
    }

    @Transactional
    override fun upsertHash(resourceHashesDO: ResourceHashesDO): ResourceHashesDO {
        logger.debug { "Upserting hash: $resourceHashesDO" }

        val uuid = resourceHashesDO.hashId ?: UUID.randomUUID()
        database.insertOrUpdate(ResourceHashesDOs) {
            set(it.hashId, uuid)
            set(it.resourceId, resourceHashesDO.resourceId)
            set(it.resourceType, resourceHashesDO.resourceType)
            set(it.tenantId, resourceHashesDO.tenantId)
            set(it.hash, resourceHashesDO.hash)
            set(it.updateDateTime, OffsetDateTime.now(ZoneOffset.UTC))

            onDuplicateKey {
                set(it.hash, resourceHashesDO.hash)
                set(it.updateDateTime, OffsetDateTime.now(ZoneOffset.UTC))
            }
        }

        return if (resourceHashesDO.hashId != null) {
            getById(uuid)
        } else {
            // Since we don't actually know the ID, we have to do the lookup, but it will exist.
            val resourceId = ResourceId(resourceHashesDO.resourceType, resourceHashesDO.resourceId)
            getHashes(resourceHashesDO.tenantId, listOf(resourceId))[resourceId]!!
        }
    }

    @Transactional
    override fun deleteHash(
        tenantId: String,
        resourceType: String,
        resourceId: String,
    ): Boolean {
        val recordsDeleted =
            database.delete(ResourceHashesDOs) {
                (ResourceHashesDOs.tenantId eq tenantId) and
                    (ResourceHashesDOs.resourceType eq resourceType) and
                    (ResourceHashesDOs.resourceId eq resourceId)
            }
        return recordsDeleted > 0
    }

    override fun deleteAllOfHash(): Boolean {
        return false
    }

    /**
     * Retrieves a [ResourceHashesDO] for the [uuid].
     */
    private fun getById(uuid: UUID): ResourceHashesDO =
        database.from(ResourceHashesDOs).select().where(ResourceHashesDOs.hashId eq uuid)
            .map { ResourceHashesDOs.createEntity(it) }.singleOrNull()
            ?: throw IllegalArgumentException("No hash found for $uuid")
}
