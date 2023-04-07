package com.projectronin.ehr.dataauthority.testclients

import com.fasterxml.jackson.databind.JsonNode
import com.projectronin.ehr.dataauthority.controllers.BatchResourceResponse
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
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
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
    private const val RESOURCES_URL = "$BASE_URL/resources"
    private const val AUTH_URL = "http://localhost:8081/ehr/token"

    fun addResources(resources: List<Resource<*>>): BatchResourceResponse = runBlocking {
        val authentication = getAuthentication()
        httpClient.post(RESOURCES_URL) {
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
                append("client_id", "id")
                append("client_secret", "secret")
            }
        ).body()
        json.get("access_token").asText()
    }
}
