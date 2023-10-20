package com.projectronin.ehr.dataauthority.client

import com.projectronin.ehr.dataauthority.client.auth.EHRDataAuthorityAuthenticationService
import com.projectronin.ehr.dataauthority.models.BatchResourceChangeResponse
import com.projectronin.ehr.dataauthority.models.BatchResourceResponse
import com.projectronin.ehr.dataauthority.models.FailedResource
import com.projectronin.ehr.dataauthority.models.Identifier
import com.projectronin.ehr.dataauthority.models.IdentifierSearchResponse
import com.projectronin.ehr.dataauthority.models.IdentifierSearchableResourceTypes
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.common.http.request
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Adds supplied resource
 */
@Component
class EHRDataAuthorityClient(
    @Value("\${ehrda.url}")
    private val hostUrl: String,
    private val client: HttpClient,
    private val authenticationService: EHRDataAuthorityAuthenticationService,
    @Value("\${ehrda.batch.identifiers:50}")
    private val identifiersBatchSize: Int = 50,
    @Value("\${ehrda.batch.add:20}")
    private val addBatchSize: Int = 20
) {
    private val serverName = "EHR Data Authority"
    val notFoundStatuses = listOf(HttpStatusCode.NotFound, HttpStatusCode.Gone)

    /**
     * takes in a list of resources cuts the list into chunks of no more than 25,
     * then sends to [addResourcesByBatch] to post the resources
     * returns list of [BatchResourceResponse]
     */
    suspend fun addResources(tenantId: String, resources: List<Resource<*>>): BatchResourceResponse {
        val batchResources = addResourcesInBatches(tenantId, resources, addBatchSize)
        val succeeded = batchResources.flatMap { it.succeeded }
        val failed = batchResources.flatMap { it.failed }
        return BatchResourceResponse(succeeded, failed)
    }

    /**
     * Adds the resources in batches of the provided size. If the size is too large, smaller batches may be attempted until all resources have been added.
     */
    private suspend fun addResourcesInBatches(
        tenantId: String,
        resources: List<Resource<*>>,
        batchSize: Int
    ): List<BatchResourceResponse> {
        return resources.chunked(batchSize).flatMap { batch ->
            runCatching { addResourcesByBatch(tenantId, batch) }.fold(
                onSuccess = { listOf(it) },
                onFailure = { e ->
                    if (e is ClientFailureException && e.status == HttpStatusCode.PayloadTooLarge) {
                        // The actual batch size doesn't really matter since we just care about the actual size of the batch being processed.
                        if (batch.size == 1) {
                            val resource = batch.first()
                            listOf(
                                BatchResourceResponse(
                                    failed = listOf(
                                        FailedResource(
                                            resourceType = resource.resourceType,
                                            resourceId = resource.id!!.value!!,
                                            error = "Payload too large"
                                        )
                                    )
                                )
                            )
                        } else {
                            // Attempt the failed batch at half the current size
                            addResourcesInBatches(tenantId, batch, batchSize / 2)
                        }
                    } else {
                        throw e
                    }
                }
            )
        }
    }

    /**
     * Posts a batch/chunk of resources.
     */
    private suspend fun addResourcesByBatch(tenantId: String, resources: List<Resource<*>>): BatchResourceResponse {
        val resourceUrl = "$hostUrl/tenants/$tenantId/resources"
        val authentication = authenticationService.getAuthentication()
        val response: HttpResponse = client.request(serverName, resourceUrl) { url ->
            post(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${authentication.accessToken}")
                }
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(resources)
            }
        }
        return response.body()
    }

    /**
     * Retrieves the resource with [resourceType] and [udpId] for [tenantId].
     */
    suspend fun getResource(tenantId: String, resourceType: String, udpId: String): Resource<*>? {
        return runCatching<Resource<*>?> {
            val resourceUrl = "$hostUrl/tenants/$tenantId/resources/$resourceType/$udpId"
            val authentication = authenticationService.getAuthentication()

            val response: HttpResponse = client.request(serverName, resourceUrl) { url ->
                get(url) {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${authentication.accessToken}")
                    }
                    accept(ContentType.Application.Json)
                    contentType(ContentType.Application.Json)
                }
            }
            response.body()
        }.fold(
            onSuccess = { it },
            onFailure = {
                if (it is ClientFailureException && it.status in notFoundStatuses) {
                    null
                } else {
                    throw it
                }
            }
        )
    }

    /**
     * Reified version of [getResource] allowing for specifying the type.
     */
    final inline fun <reified T : Resource<T>> getResourceAs(
        tenantId: String,
        resourceType: String,
        udpId: String
    ): T? =
        runBlocking {
            getResource(tenantId, resourceType, udpId) as? T
        }

    /**
     * Retrieves the identifiers associated to the [resourceType] with [identifiers] for [tenantId]
     */
    suspend fun getResourceIdentifiers(
        tenantId: String,
        resourceType: IdentifierSearchableResourceTypes,
        identifiers: List<Identifier>
    ): List<IdentifierSearchResponse> {
        val resourceUrl = "$hostUrl/tenants/$tenantId/resources/${resourceType.name}/identifiers"
        return identifiers.chunked(identifiersBatchSize).flatMap { batch ->
            val authentication = authenticationService.getAuthentication()
            val response: HttpResponse = client.request(serverName, resourceUrl) { requestUrl ->
                post(requestUrl) {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${authentication.accessToken}")
                    }
                    accept(ContentType.Application.Json)
                    contentType(ContentType.Application.Json)
                    setBody(batch)
                }
            }
            response.body<List<IdentifierSearchResponse>>()
        }
    }

    /**
     * Deletes the resource for [resourceType] and [udpId] for [tenantId]. This API only supports deleting from testing
     * tenants. Any non-testing tenant that is attempted to be deleted from will result in a 400. If this request was
     * unsuccessful, any exception returned by the EHR Data Authority will be thrown.
     */
    suspend fun deleteResource(tenantId: String, resourceType: String, udpId: String) {
        val resourceUrl = "$hostUrl/tenants/$tenantId/resources/$resourceType/$udpId"
        val authentication = authenticationService.getAuthentication()
        client.request(serverName, resourceUrl) { url ->
            delete(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${authentication.accessToken}")
                }
            }
        }
    }

    /**
     * Returns the change status of given [resources] compared against the existing stored resources.
     */
    suspend fun getResourcesChangeStatus(tenantId: String, resources: List<Resource<*>>): BatchResourceChangeResponse {
        val batchResources = resources.chunked(25).map { getResourcesChangeByBatch(tenantId, it) }
        val succeeded = batchResources.flatMap { it.succeeded }
        val failed = batchResources.flatMap { it.failed }
        return BatchResourceChangeResponse(succeeded, failed)
    }

    /**
     * Posts a batch/chunk of resources.
     */
    private suspend fun getResourcesChangeByBatch(
        tenantId: String,
        resources: List<Resource<*>>
    ): BatchResourceChangeResponse {
        val resourceUrl = "$hostUrl/tenants/$tenantId/resources/changeStatus"
        val authentication = authenticationService.getAuthentication()
        val response: HttpResponse = client.request(serverName, resourceUrl) { url ->
            post(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${authentication.accessToken}")
                }
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(resources)
            }
        }
        return response.body()
    }
}
