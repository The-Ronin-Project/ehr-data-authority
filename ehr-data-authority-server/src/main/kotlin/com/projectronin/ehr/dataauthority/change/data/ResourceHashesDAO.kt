package com.projectronin.ehr.dataauthority.change.data

import com.projectronin.ehr.dataauthority.change.data.binding.ResourceHashesDOs
import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import mu.KotlinLogging
import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.where
import org.ktorm.support.mysql.insertOrUpdate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * DAO responsible for managing resource hashes.
 */
@Repository
class ResourceHashesDAO(private val database: Database) {
    private val logger = KotlinLogging.logger { }

    /**
     * Retrieves the [ResourceHashesDO] associated with [resourceType], [resourceId] and [tenantId].
     */
    fun getHash(tenantId: String, resourceType: String, resourceId: String): ResourceHashesDO? {
        return database.from(ResourceHashesDOs).select()
            .where((ResourceHashesDOs.tenantId eq tenantId) and (ResourceHashesDOs.resourceType eq resourceType) and (ResourceHashesDOs.resourceId eq resourceId))
            .map { ResourceHashesDOs.createEntity(it) }.singleOrNull()
    }

    /**
     * Inserts or updates the [resourceHashesDO] and returns the current view from the data store.
     */
    @Transactional
    fun upsertHash(resourceHashesDO: ResourceHashesDO): ResourceHashesDO {
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
            getHash(
                resourceHashesDO.tenantId,
                resourceHashesDO.resourceType,
                resourceHashesDO.resourceId
            )!!
        }
    }

    /**
     * Deletes the hash associated to the [resourceType] and [resourceId] for [tenantId]
     */
    @Transactional
    fun deleteHash(tenantId: String, resourceType: String, resourceId: String): Boolean {
        val recordsDeleted = database.delete(ResourceHashesDOs) {
            (ResourceHashesDOs.tenantId eq tenantId) and (ResourceHashesDOs.resourceType eq resourceType) and (ResourceHashesDOs.resourceId eq resourceId)
        }
        return recordsDeleted > 0
    }

    /**
     * Retrieves a [ResourceHashesDO] for the [uuid].
     */
    private fun getById(uuid: UUID): ResourceHashesDO =
        database.from(ResourceHashesDOs).select().where(ResourceHashesDOs.hashId eq uuid)
            .map { ResourceHashesDOs.createEntity(it) }.singleOrNull()
            ?: throw IllegalArgumentException("No hash found for $uuid")
}
