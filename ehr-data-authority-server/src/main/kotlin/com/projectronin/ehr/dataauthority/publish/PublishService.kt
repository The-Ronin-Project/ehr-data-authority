package com.projectronin.ehr.dataauthority.publish

import com.projectronin.ehr.dataauthority.change.data.services.DataStorageService
import com.projectronin.interop.common.logmarkers.getLogMarker
import com.projectronin.interop.fhir.r4.resource.Resource
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Service allowing access to push data updates to the Ronin clinical data store.
 */
@Service
class PublishService(
    private val dataStorageService: DataStorageService,
    @Value("\${aidbox.publishBatchSize:25}") private val batchSize: Int = 25,
) {
    private val logger = KotlinLogging.logger { }

    /**
     * Publishes FHIR resources to the Ronin clinical data store via HTTP. Expects an id value in each resource.
     * For an existing resource id, publish updates that resource with the new data. For a new id, it adds the resource.
     * Expects that the caller will not input an empty List.
     * @param resourceCollection List of FHIR resources to publish. May be a mixed List with different resourceTypes.
     * @return The List of resources as they were stored in the making data system. This may match the inputs, but may also
     * include additional information such as updated meta information.
     */
    fun publish(resourceCollection: List<Resource<*>>): List<Resource<*>> {
        logger.info { "Publishing Ronin clinical data" }
        if (resourceCollection.isEmpty()) {
            return emptyList()
        }

        return runBlocking {
            resourceCollection.chunked(batchSize).flatMap {
                try {
                    dataStorageService.batchUpsert(it)
                } catch (e: Exception) {
                    logger.warn(e.getLogMarker(), e) { "Failed to publish Ronin clinical data" }
                    emptyList()
                }
            }
        }
    }
}
