package com.projectronin.ehr.dataauthority.aidbox

import com.projectronin.interop.common.logmarkers.getLogMarker
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Service allowing access to push data updates to the Ronin clinical data store.
 */
@Service
class AidboxPublishService(
    private val aidboxClient: AidboxClient,
    @Value("\${aidbox.publishBatchSize:25}") private val batchSize: Int = 25
) {
    private val logger = KotlinLogging.logger { }

    /**
     * Publishes FHIR resources to the Ronin clinical data store via HTTP. Expects an id value in each resource.
     * For an existing resource id, publish updates that resource with the new data. For a new id, it adds the resource.
     * Expects that the caller will not input an empty List.
     * @param resourceCollection List of FHIR resources to publish. May be a mixed List with different resourceTypes.
     * @return true for success: an HTTP 2xx response, or publish was skipped for an empty list; otherwise false.
     */
    fun publish(resourceCollection: List<Resource<*>>): Boolean {
        logger.info { "Publishing Ronin clinical data" }
        if (resourceCollection.isEmpty()) {
            return true
        }

        val processedResults = runBlocking {
            resourceCollection.chunked(batchSize).map {
                try {
                    aidboxClient.batchUpsert(it).status.isSuccess()
                } catch (e: Exception) {
                    logger.warn(e.getLogMarker(), e) { "Failed to publish Ronin clinical data" }
                    false
                }
            }
        }
        return processedResults.all { it }
    }
}
