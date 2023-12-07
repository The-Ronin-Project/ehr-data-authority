package com.projectronin.ehr.dataauthority.change

import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import com.projectronin.ehr.dataauthority.change.data.services.DataStorageService
import com.projectronin.ehr.dataauthority.change.data.services.ResourceHashDAOService
import com.projectronin.ehr.dataauthority.change.model.ChangeStatus
import com.projectronin.ehr.dataauthority.models.ChangeType
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
    ): Map<T, ChangeStatus> = resources.mapValues { determineChangeStatus(tenantId, it.value) }

    /**
     * Determines the [ChangeStatus] for the given resource within this tenant.
     */
    private fun determineChangeStatus(
        tenantId: String,
        resource: Resource<*>,
    ): ChangeStatus {
        val resourceType = resource.resourceType
        val resourceId = resource.id!!.value!!

        val normalizedResource = normalizeResource(resource)
        val resourceHash = normalizedResource.consistentHashCode()
        val currentHashDO = getStoredHash(tenantId, resourceType, resourceId)

        logger.debug { "resourceHash = $resourceHash" }
        logger.debug { "currentHashDO = $currentHashDO" }

        val changeType =
            if (currentHashDO == null) {
                // If we have no record, we treat it as new
                ChangeType.NEW
            } else if (resourceHash != currentHashDO.hash) {
                // If our current record and the new hash do not match, then it has changed.
                ChangeType.CHANGED
            } else {
                // Since they do match, we'll do a deeper comparison.
                val storedResource = getStoredResource(resourceType, resourceId)

                // The normalized form will strip out the meta, so we are intentionally checking the profile
                // If the profile has changed, then the resource has changed.
                if (resource.meta?.profile != storedResource.meta?.profile) {
                    ChangeType.CHANGED
                } else {
                    val normalizedStored = normalizeResource(storedResource)

                    logger.debug { "Comparing new resource $normalizedResource to stored resource $normalizedStored" }

                    if (normalizedResource == normalizedStored) {
                        // If the normalized forms are equal, then there has been no change.
                        ChangeType.UNCHANGED
                    } else {
                        ChangeType.CHANGED
                    }
                }
            }

        return ChangeStatus(resourceType, resourceId, changeType, currentHashDO?.hashId, resourceHash)
    }

    /**
     * Retrieves the stored hash for the [resourceType] with [resourceId] for [tenantId].
     */
    private fun getStoredHash(
        tenantId: String,
        resourceType: String,
        resourceId: String,
    ): ResourceHashesDO? = resourceHashDao.getHash(tenantId, resourceType, resourceId)

    /**
     * Gets the stored resource for the [resourceType] with [resourceId]
     */
    private fun getStoredResource(
        resourceType: String,
        resourceId: String,
    ): Resource<*> {
        return dataStorageService.getResource(resourceType, resourceId)
    }

    /**
     * Normalizes the [resource] to allow for a proper comparison regardless of changes made by the backing system.
     */
    private fun normalizeResource(resource: Resource<*>): Resource<*> = copy(resource, mapOf("meta" to null))
}
