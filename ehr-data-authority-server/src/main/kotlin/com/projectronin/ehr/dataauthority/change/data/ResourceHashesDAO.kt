package com.projectronin.ehr.dataauthority.change.data

import com.projectronin.ehr.dataauthority.change.data.binding.ResourceHashesDOs
import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import mu.KotlinLogging
import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.update
import org.ktorm.dsl.where
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * DAO responsible for managing resource hasehs.
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
     * Inserts the [resourceHashesDO] and returns the current view from the data store.
     */
    @Transactional
    fun insertHash(resourceHashesDO: ResourceHashesDO): ResourceHashesDO {
        val uuid = UUID.randomUUID()

        database.insert(ResourceHashesDOs) {
            set(it.hashId, uuid)
            set(it.resourceId, resourceHashesDO.resourceId)
            set(it.resourceType, resourceHashesDO.resourceType)
            set(it.tenantId, resourceHashesDO.tenantId)
            set(it.hash, resourceHashesDO.hash)
            set(it.updateDateTime, resourceHashesDO.updateDateTime)
        }
        return getById(uuid)
    }

    /**
     * Updates [hashId] to the [newHash] and returns the current view from the data store.
     */
    @Transactional
    fun updateHash(hashId: UUID, newHash: Int): ResourceHashesDO {
        logger.debug { "Updating hash value for $hashId to $newHash" }

        database.update(ResourceHashesDOs) {
            set(it.hash, newHash)
            set(it.updateDateTime, OffsetDateTime.now(ZoneOffset.UTC))

            where {
                it.hashId eq hashId
            }
        }

        return getById(hashId)
    }

    /**
     * Retrieves a [ResourceHashesDO] for the [uuid].
     */
    private fun getById(uuid: UUID): ResourceHashesDO =
        database.from(ResourceHashesDOs).select().where(ResourceHashesDOs.hashId eq uuid)
            .map { ResourceHashesDOs.createEntity(it) }.singleOrNull()
            ?: throw IllegalArgumentException("No hash found for $uuid")
}
