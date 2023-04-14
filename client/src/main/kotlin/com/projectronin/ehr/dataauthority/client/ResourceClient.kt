package com.projectronin.ehr.dataauthority.client

import com.projectronin.ehr.dataauthority.client.auth.DataAuthorityAuthenticationService
import com.projectronin.ehr.dataauthority.client.models.BatchResourceResponse
import com.projectronin.ehr.dataauthority.client.models.Identifier
import com.projectronin.ehr.dataauthority.client.models.IdentifierSearchResponse
import com.projectronin.ehr.dataauthority.client.models.IdentifierSearchableResourceTypes
import com.projectronin.interop.common.http.request
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Adds supplied resource
 */
@Component
class ResourceClient(
    @Value("\${dataauthority.url:}")
    private val hostUrl: String,
    private val client: HttpClient,
    private val authenticationService: DataAuthorityAuthenticationService
) {

    /**
     * takes in a list of resources cuts the list into chunks of no more than 25,
     * then sends to [addResourcesByBatch] to post the resources
     * returns list of [BatchResourceResponse]
     */
    suspend fun addResources(tenantId: String, resources: List<Resource<*>>): BatchResourceResponse {
        val batchResources = resources.chunked(25).map { addResourcesByBatch(tenantId, it) }
        val succeeded = batchResources.flatMap { it.succeeded }
        val failed = batchResources.flatMap { it.failed }
        return BatchResourceResponse(succeeded, failed)
    }

    /**
     * Posts a batch/chunk of resources.
     */
    private suspend fun addResourcesByBatch(tenantId: String, resources: List<Resource<*>>): BatchResourceResponse {
        val resourceUrl = "$hostUrl/tenants/$tenantId/resources"
        val authentication = authenticationService.getAuthentication()
        val response: HttpResponse = client.request("DataAuthority", resourceUrl) { url ->
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

    suspend fun getResource(tenantId: String, resourceType: ResourceType, udpId: String): Resource<*> {
        val resourceTypeString = resourceType.name.lowercase().replaceFirstChar { it.uppercase() }
        val resourceUrl = "$hostUrl/tenants/$tenantId/resources/$resourceTypeString/$udpId"
        val authentication = authenticationService.getAuthentication()

        val response: HttpResponse = client.request("DataAuthority", resourceUrl) { url ->
            get(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${authentication.accessToken}")
                }
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
            }
        }
        return response.body()
    }

    suspend fun getResourceIdentifiers(tenantId: String, resourceType: IdentifierSearchableResourceTypes, identifiers: List<Identifier>): List<IdentifierSearchResponse> {
        val identifierString = identifiers.joinToString(",")
        val resourceUrl = "$hostUrl/tenants/$tenantId/resources/${resourceType.name}/identifiers/$identifierString"
        val authentication = authenticationService.getAuthentication()
        val response: HttpResponse = client.request("DataAuthority", resourceUrl) { url ->
            get(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${authentication.accessToken}")
                }
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
            }
        }
        return response.body()
    }
}
