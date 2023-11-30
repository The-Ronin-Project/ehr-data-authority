package com.projectronin.ehr.dataauthority.aidbox

import com.projectronin.ehr.dataauthority.aidbox.auth.AidboxAuthenticationBroker
import com.projectronin.ehr.dataauthority.change.data.services.DataStorageService
import com.projectronin.interop.common.http.exceptions.ClientAuthenticationException
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
        return runBlocking {
            val call: suspend () -> HttpResponse = {
                val authentication = authenticationBroker.getAuthentication()
                httpClient.request("Aidbox", "$aidboxURLRest/fhir") { url ->
                    post(url) {
                        headers {
                            append(
                                HttpHeaders.Authorization,
                                "${authentication.tokenType} ${authentication.accessToken}"
                            )
                            append("aidbox-validation-skip", "reference")
                        }
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        setBody(bundle)
                    }
                }
            }
            val response: HttpResponse = performWithAuthRetry(call)
            response.status
        }
    }

    override fun getResource(resourceType: String, resourceFHIRID: String): Resource<*> {
        return runBlocking {
            val call: suspend () -> HttpResponse = {
                val authentication = authenticationBroker.getAuthentication()
                httpClient.request("Aidbox", "$aidboxURLRest/fhir/$resourceType/$resourceFHIRID") { url ->
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
            }
            val response: HttpResponse = performWithAuthRetry(call)
            response.body()
        }
    }

    override suspend fun searchForResources(resourceType: String, tenantId: String, identifierToken: String): Bundle {
        val tenantIdentifier = "${CodeSystem.RONIN_TENANT.uri.value}|$tenantId".encodeURLPathPart()
        val encodedIdentifierToken = identifierToken.encodeURLPathPart()
        return runBlocking {
            val call: suspend () -> HttpResponse = {
                val authentication = authenticationBroker.getAuthentication()
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
            }
            val response = performWithAuthRetry(call)
            response.body()
        }
    }

    override suspend fun deleteResource(resourceType: String, udpId: String): HttpStatusCode {
        return runBlocking {
            val call: suspend () -> HttpResponse = {
                val authentication = authenticationBroker.getAuthentication()
                httpClient.request("Aidbox", "$aidboxURLRest/fhir/$resourceType/$udpId") { url ->
                    delete(url) {
                        headers {
                            append(
                                HttpHeaders.Authorization,
                                "${authentication.tokenType} ${authentication.accessToken}"
                            )
                        }
                    }
                }
            }
            val response: HttpResponse = performWithAuthRetry(call)
            response.status
        }
    }

    private suspend fun performWithAuthRetry(supplier: suspend () -> HttpResponse, retries: Int = 1): HttpResponse {
        return try {
            supplier.invoke()
        } catch (e: ClientAuthenticationException) {
            // if it's a 401, then we retry
            if (e.status.value == HttpStatusCode.Unauthorized.value) {
                if (retries > 0) {
                    logger.warn { "Received a 401 from Aidbox, reauthenticating and retrying with $retries tries remaining" }
                    authenticationBroker.reauthenticate()
                    performWithAuthRetry(supplier, retries - 1)
                } else {
                    logger.error { "Recieved a 401 from Aidbox, exhausted reauthentication retires" }
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    override suspend fun deleteAllResources(): HttpStatusCode {
        return HttpStatusCode.BadRequest
    }
}
