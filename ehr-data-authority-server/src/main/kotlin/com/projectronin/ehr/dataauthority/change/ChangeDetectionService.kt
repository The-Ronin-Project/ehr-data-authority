package com.projectronin.ehr.dataauthority.change

import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import com.projectronin.ehr.dataauthority.change.data.services.DataStorageService
import com.projectronin.ehr.dataauthority.change.data.services.ResourceHashDAOService
import com.projectronin.ehr.dataauthority.change.data.services.ResourceId
import com.projectronin.ehr.dataauthority.change.model.ChangeStatus
import com.projectronin.ehr.dataauthority.models.ChangeType
import com.projectronin.interop.common.collection.associateWithNonNull
import com.projectronin.interop.common.reflect.copy
import com.projectronin.interop.fhir.r4.resource.Resource
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * The ChangeDetectionService is used to determine if resources should be considered changed since their last update into Aidbox.
 */
@Component
class ChangeDetectionService(
    private val dataStorageService: DataStorageService,
    private val resourceHashDao: ResourceHashDAOService,
) {
    private val logger = KotlinLogging.logger { }

    /**
     * Determines the [ChangeStatus] for each of the supplied [resources]. The keys used for the input will be the keys
     * for the responses, allowing consumers to correlate the two.
     */
    fun <T> determineChangeStatuses(
        tenantId: String,
        resources: Map<T, Resource<*>>,
    ): Map<T, ChangeStatus> {
        val normalizedResources = resources.mapValues { it.value.normalizeResource() }
        val currentHashes = normalizedResources.mapValues { it.value.consistentHashCode() }
        val storedHashes = getStoredHashes(tenantId, resources)

        val changeTypeByKey = mutableMapOf<T, ChangeType>()
        val toCompare = mutableListOf<T>()
        resources.keys.forEach { key ->
            val storedHash = storedHashes[key]
            val resourceHash = currentHashes[key]

            if (storedHash == null) {
                changeTypeByKey[key] = ChangeType.NEW
            } else if (resourceHash != storedHash.hash) {
                changeTypeByKey[key] = ChangeType.CHANGED
            } else {
                toCompare.add(key)
            }
        }

        val resourcesToCompare = toCompare.associateWithNonNull { resources[it] }
        val storedResources = getStoredResources(tenantId, resourcesToCompare.values)
        resourcesToCompare.forEach { (key, resource) ->
            val storedResource = storedResources[resource.id!!.value!!]!!

            // The normalized form will strip out the meta, so we are intentionally checking the profile
            // If the profile has changed, then the resource has changed.
            val changeType =
                if (resource.meta?.profile != storedResource.meta?.profile) {
                    ChangeType.CHANGED
                } else {
                    val normalizedResource = normalizedResources[key]
                    val normalizedStored = storedResource.normalizeResource()

                    logger.debug { "Comparing new resource $normalizedResource to stored resource $normalizedStored" }

                    if (normalizedResource == normalizedStored) {
                        // If the normalized forms are equal, then there has been no change.
                        ChangeType.UNCHANGED
                    } else {
                        ChangeType.CHANGED
                    }
                }
            changeTypeByKey[key] = changeType
        }

        return resources.mapValues { (key, resource) ->
            val changeType = changeTypeByKey[key]!!
            val storedHash = storedHashes[key]
            val newHash = currentHashes[key]!!
            ChangeStatus(resource.resourceType, resource.id!!.value!!, changeType, storedHash?.hashId, newHash)
        }
    }

    private fun <T> getStoredHashes(
        tenantId: String,
        resources: Map<T, Resource<*>>,
    ): Map<T, ResourceHashesDO> {
        val resourceIdsToKey =
            resources.map { (key, resource) ->
                ResourceId(resource.resourceType, resource.id!!.value!!) to key
            }.toMap()
        val hashesByResourceId = resourceHashDao.getHashes(tenantId, resourceIdsToKey.keys.toList())
        return hashesByResourceId.mapKeys { resourceIdsToKey[it.key]!! }
    }

    private fun getStoredResources(
        tenantId: String,
        resources: Collection<Resource<*>>,
    ): Map<String, Resource<*>> {
        val resourcesByType = resources.groupBy { it.resourceType }
        return resourcesByType.flatMap { (type, currentResources) ->
            val resourceIds = currentResources.map { it.id!!.value!! }
            dataStorageService.getResources(type, resourceIds).toList()
        }.toMap()
    }

    /**
     * Normalizes this resource to allow for a proper comparison regardless of changes made by the backing system.
     */
    private fun Resource<*>.normalizeResource(): Resource<*> = copy(this, mapOf("meta" to null))
}
