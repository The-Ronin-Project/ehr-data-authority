package com.projectronin.ehr.dataauthority.aidbox

import com.projectronin.ehr.dataauthority.aidbox.auth.AidboxAuthenticationBroker
import com.projectronin.interop.common.http.request
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Client for accessing an Aidbox server via its configured base URL for REST API calls.
 */
@Component
class AidboxClient(
    private val httpClient: HttpClient,
    @Value("\${aidbox.url}")
    private val aidboxURLRest: String,
    private val authenticationBroker: AidboxAuthenticationBroker
) {
    private val logger = KotlinLogging.logger { }

    /**
     * Publishes FHIR resources to Aidbox by POSTing a FHIR transaction bundle. Expects an id value in each resource.
     * For an existing resource id, updates that resource with the new data. For a new id, adds the resource to Aidbox.
     * Order of resources in the bundle is not important to resolve references within the bundle. The only requirement
     * on references is that all the referenced resources are either in the bundle or already in Aidbox.
     * The transaction bundle is all-or-nothing: Every resource in the bundle must succeed to return a 200 response.
     * @param resourceCollection List of FHIR resources to publish. May be a mixed List with different resourceTypes.
     * @return [HttpResponse] from the Aidbox FHIR transaction bundle REST API.
     */
    suspend fun batchUpsert(resourceCollection: List<Resource<*>>): HttpResponse {
        val arrayLength = resourceCollection.size
        val showArray = when (arrayLength) {
            1 -> "resource"
            else -> "resources"
        }
        logger.debug { "Aidbox batch upsert of $arrayLength $showArray" }
        val bundle = makeBundleForBatchUpsert(aidboxURLRest, resourceCollection)
        val authentication = authenticationBroker.getAuthentication()
        val response: HttpResponse = runBlocking {
            httpClient.request("Aidbox", "$aidboxURLRest/fhir") { url ->
                post(url) {
                    headers {
                        append(HttpHeaders.Authorization, "${authentication.tokenType} ${authentication.accessToken}")
                        append("aidbox-validation-skip", "reference")
                    }
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    setBody(bundle)
                }
            }
        }

        return response
    }

    /**
     * Fetches a full FHIR resource from Aidbox based on the Fhir ID.
     * @param resourceType [String] the type of FHIR resource, i.e. "Patient" (case sensitive)
     * @param resourceFHIRID [String] the FHIR ID of the resource ("id" json element)
     * @return [HttpResponse] containing the raw data from the server. Use HttpResponse.recieve<T>() to deserialize.
     */
    suspend fun getResource(resourceType: String, resourceFHIRID: String): HttpResponse {
        val authentication = authenticationBroker.getAuthentication()
        val response: HttpResponse =
            httpClient.request("Aidbox", "$aidboxURLRest/fhir/$resourceType/$resourceFHIRID") { url ->
                get(url) {
                    headers {
                        append(HttpHeaders.Authorization, "${authentication.tokenType} ${authentication.accessToken}")
                    }
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                }
            }

        return response
    }

    /**
     * Fetches a full FHIR resource from Aidbox based on the resource identifiers.
     * @param resourceType [String] the type of FHIR resource, i.e. "Patient" (case sensitive)
     * @param tenantId [String] the tenant mnemonic
     * @param identifierToken [String] system|value token to match system and value on any of the resource identifiers.
     * @return [HttpResponse] containing the raw data from the server. Use HttpResponse.recieve<T>() to deserialize.
     */
    suspend fun searchForResources(resourceType: String, tenantId: String, identifierToken: String): HttpResponse {
        val authentication = authenticationBroker.getAuthentication()
        val tenantIdentifier = "${CodeSystem.RONIN_TENANT.uri.value}|$tenantId".encodeURLPathPart()
        val encodedIdentifierToken = identifierToken.encodeURLPathPart()
        val response: HttpResponse =
            httpClient.request(
                "Aidbox",
                "$aidboxURLRest/fhir/$resourceType?identifier=$tenantIdentifier&identifier=$encodedIdentifierToken"
            ) { url ->
                get(url) {
                    headers {
                        append(HttpHeaders.Authorization, "${authentication.tokenType} ${authentication.accessToken}")
                    }
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                }
            }

        return response
    }

    /**
     * Deletes the [resourceType] with [udpId] from Aidbox.
     */
    suspend fun deleteResource(resourceType: String, udpId: String): HttpResponse {
        val authentication = authenticationBroker.getAuthentication()
        return httpClient.request("Aidbox", "$aidboxURLRest/fhir/$resourceType/$udpId") { url ->
            delete(url) {
                headers {
                    append(HttpHeaders.Authorization, "${authentication.tokenType} ${authentication.accessToken}")
                }
            }
        }
    }
}
