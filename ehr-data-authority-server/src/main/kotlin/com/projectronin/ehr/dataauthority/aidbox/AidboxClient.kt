package com.projectronin.ehr.dataauthority.aidbox

import com.projectronin.ehr.dataauthority.aidbox.auth.AidboxAuthenticationBroker
import com.projectronin.ehr.dataauthority.change.data.services.DataStorageService
import com.projectronin.interop.common.http.request
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.resource.Bundle
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
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value

/**
 * Client for accessing an Aidbox server via its configured base URL for REST API calls.
 */
class AidboxClient(
    private val httpClient: HttpClient,
    @Value("\${aidbox.url}")
    private val aidboxURLRest: String,
    private val authenticationBroker: AidboxAuthenticationBroker
) : DataStorageService {
    private val logger = KotlinLogging.logger { }

    override suspend fun batchUpsert(resourceCollection: List<Resource<*>>): HttpStatusCode {
        val arrayLength = resourceCollection.size
        val showArray = when (arrayLength) {
            1 -> "resource"
            else -> "resources"
        }
        logger.debug { "Aidbox batch upsert of $arrayLength $showArray" }
        val bundle = makeBundleForBatchUpsert(aidboxURLRest, resourceCollection)
        val authentication = authenticationBroker.getAuthentication()
        return runBlocking {
            val response: HttpResponse =
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
            response.status
        }
    }

    override fun getResource(resourceType: String, resourceFHIRID: String): Resource<*> {
        val authentication = authenticationBroker.getAuthentication()
        return runBlocking {
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
            response.body()
        }
    }

    override suspend fun searchForResources(resourceType: String, tenantId: String, identifierToken: String): Bundle {
        val authentication = authenticationBroker.getAuthentication()
        val tenantIdentifier = "${CodeSystem.RONIN_TENANT.uri.value}|$tenantId".encodeURLPathPart()
        val encodedIdentifierToken = identifierToken.encodeURLPathPart()
        return runBlocking {
            val response: HttpResponse =
                httpClient.request(
                    "Aidbox",
                    "$aidboxURLRest/fhir/$resourceType?identifier=$tenantIdentifier&identifier=$encodedIdentifierToken"
                ) { url ->
                    get(url) {
                        headers {
                            append(
                                HttpHeaders.Authorization,
                                "${authentication.tokenType} ${authentication.accessToken}"
                            )
                        }
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                    }
                }
            response.body()
        }
    }

    override suspend fun deleteResource(resourceType: String, udpId: String): HttpStatusCode {
        val authentication = authenticationBroker.getAuthentication()
        return runBlocking {
            val response: HttpResponse =
                httpClient.request("Aidbox", "$aidboxURLRest/fhir/$resourceType/$udpId") { url ->
                    delete(url) {
                        headers {
                            append(HttpHeaders.Authorization, "${authentication.tokenType} ${authentication.accessToken}")
                        }
                    }
                }
            response.status
        }
    }

    override suspend fun deleteAllResources(): HttpStatusCode {
        return HttpStatusCode.BadRequest
    }
}
