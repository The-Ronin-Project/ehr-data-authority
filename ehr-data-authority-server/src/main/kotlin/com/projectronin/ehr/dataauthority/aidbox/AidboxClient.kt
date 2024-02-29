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
import io.ktor.http.encodeURLParameter
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
    private val authenticationBroker: AidboxAuthenticationBroker,
    @Value("\${aidbox.batch.size:10")
    private val aidboxBatchSize: Int = 10,
) : DataStorageService {
    private val logger = KotlinLogging.logger { }
    private val numAuthRetries = 1

    override suspend fun batchUpsert(resourceCollection: List<Resource<*>>): List<Resource<*>> {
        val arrayLength = resourceCollection.size
        val showArray =
            when (arrayLength) {
                1 -> "resource"
                else -> "resources"
            }
        logger.debug { "Aidbox batch upsert of $arrayLength $showArray" }
        val bundle = makeBundleForBatchUpsert(aidboxURLRest, resourceCollection)
        return runBlocking {
            httpClient.request(
                "Aidbox",
                "$aidboxURLRest/fhir",
                authenticationBroker,
                numAuthRetries,
                logger,
            ) { url, authentication ->
                post(url) {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "${authentication?.tokenType} ${authentication?.accessToken}",
                        )
                        append("aidbox-validation-skip", "reference")
                    }
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    setBody(bundle)
                }
            }.getResources()
        }
    }

    override fun getResource(
        resourceType: String,
        resourceFHIRID: String,
    ): Resource<*>? {
        return getResources(resourceType, listOf(resourceFHIRID))[resourceFHIRID]
    }

    override fun getResources(
        resourceType: String,
        resourceIds: List<String>,
    ): Map<String, Resource<*>> {
        val chunkedIds = resourceIds.chunked(aidboxBatchSize)
        return runBlocking {
            chunkedIds.flatMap { currentIds ->
                httpClient.request(
                    "Aidbox",
                    "$aidboxURLRest/fhir/$resourceType",
                    authenticationBroker,
                    numAuthRetries,
                    logger,
                ) { url, authentication ->
                    get(url) {
                        headers {
                            append(
                                HttpHeaders.Authorization,
                                "${authentication?.tokenType} ${authentication?.accessToken}",
                            )
                        }
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        url {
                            encodedParameters.append(
                                "_id",
                                currentIds.joinToString(separator = ",") {
                                    it.encodeURLParameter(spaceToPlus = true)
                                },
                            )
                        }
                    }
                }.getResources()
            }.associateBy { it.id!!.value!! }
        }
    }

    override suspend fun searchForResources(
        resourceType: String,
        tenantId: String,
        identifierToken: String,
    ): Bundle {
        val tenantIdentifier = "${CodeSystem.RONIN_TENANT.uri.value}|$tenantId".encodeURLPathPart()
        val encodedIdentifierToken = identifierToken.encodeURLPathPart()
        return runBlocking {
            val response: HttpResponse =
                runBlocking {
                    httpClient.request(
                        "Aidbox",
                        "$aidboxURLRest/fhir/$resourceType?identifier=$tenantIdentifier&identifier=$encodedIdentifierToken",
                        authenticationBroker,
                        numAuthRetries,
                        logger,
                    ) { url, authentication ->
                        get(url) {
                            headers {
                                append(
                                    HttpHeaders.Authorization,
                                    "${authentication?.tokenType} ${authentication?.accessToken}",
                                )
                            }
                            contentType(ContentType.Application.Json)
                            accept(ContentType.Application.Json)
                        }
                    }
                }
            response.body()
        }
    }

    override suspend fun deleteResource(
        resourceType: String,
        udpId: String,
    ): HttpStatusCode {
        return runBlocking {
            val response: HttpResponse =
                runBlocking {
                    httpClient.request(
                        "Aidbox",
                        "$aidboxURLRest/fhir/$resourceType/$udpId",
                        authenticationBroker,
                        numAuthRetries,
                        logger,
                    ) { url, authentication ->
                        delete(url) {
                            headers {
                                append(
                                    HttpHeaders.Authorization,
                                    "${authentication?.tokenType} ${authentication?.accessToken}",
                                )
                            }
                        }
                    }
                }
            response.status
        }
    }

    override suspend fun deleteAllResources(): HttpStatusCode {
        return HttpStatusCode.BadRequest
    }

    private suspend fun HttpResponse.getResources(): List<Resource<*>> = this.body<Bundle>().entry.mapNotNull { it.resource }
}
