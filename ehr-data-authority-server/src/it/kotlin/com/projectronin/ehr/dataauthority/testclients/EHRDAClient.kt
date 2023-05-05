package com.projectronin.ehr.dataauthority.testclients

import com.fasterxml.jackson.databind.JsonNode
import com.projectronin.ehr.dataauthority.model.BatchResourceResponse
import com.projectronin.ehr.dataauthority.model.Identifier
import com.projectronin.ehr.dataauthority.model.IdentifierSearchResponse
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking

object EHRDAClient {
    val httpClient = HttpClient(CIO) {
        // If not a successful response, Ktor will throw Exceptions
        expectSuccess = true

        // Setup JSON
        install(ContentNegotiation) {
            jackson {
                JacksonManager.setUpMapper(this)
            }
        }

        // Enable logging.
        install(Logging) {
            level = LogLevel.NONE
        }
    }

    private const val BASE_URL = "http://localhost:8080"
    private const val RESOURCES_URL_FMT = "$BASE_URL/tenants/%s/resources"
    private const val RESOURCE_RETRIEVAL_URL_FMT = "$RESOURCES_URL_FMT/%s/%s"
    private const val RESOURCE_IDENTIFIER_SEARCH_URL_FMT = "$RESOURCES_URL_FMT/%s/identifiers/%s"
    private const val AUTH_URL = "http://localhost:8081/ehr/token"

    fun addResources(tenantId: String, resources: List<Resource<*>>): BatchResourceResponse = runBlocking {
        val authentication = getAuthentication()
        httpClient.post(RESOURCES_URL_FMT.format(tenantId)) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $authentication")
            }
            setBody(resources)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body()
    }

    private fun getAuthentication(): String = runBlocking {
        val json: JsonNode = httpClient.submitForm(
            url = AUTH_URL,
            formParameters = Parameters.build {
                append("grant_type", "client_credentials")
                append("client_id", "ehr-client")
                append("client_secret", "secret")
            }
        ).body()
        json.get("access_token").asText()
    }

    fun getResource(tenantId: String, resourceType: String, udpID: String): Resource<*> = runBlocking {
        val authentication = getAuthentication()
        httpClient.get(RESOURCE_RETRIEVAL_URL_FMT.format(tenantId, resourceType, udpID)) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $authentication")
            }
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body()
    }

    fun searchResourceIdentifiers(tenantId: String, resourceType: String, identifiers: List<Identifier>): List<IdentifierSearchResponse> = runBlocking {
        val authentication = getAuthentication()
        val url = RESOURCE_IDENTIFIER_SEARCH_URL_FMT.format(tenantId, resourceType, identifiers.joinToString(",") { it.toToken().encodeURLPathPart() })
        httpClient.get(url) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $authentication")
            }
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body()
    }
}
