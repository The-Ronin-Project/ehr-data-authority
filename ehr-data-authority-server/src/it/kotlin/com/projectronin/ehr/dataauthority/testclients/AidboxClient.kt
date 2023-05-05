package com.projectronin.ehr.dataauthority.testclients

import com.projectronin.interop.aidbox.auth.AidboxAuthenticationBroker
import com.projectronin.interop.aidbox.auth.AidboxAuthenticationService
import com.projectronin.interop.aidbox.auth.AidboxCredentials
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking

object AidboxClient {
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

    private const val BASE_URL = "http://localhost:8888"

    private val aidboxCredentials = AidboxCredentials("client", "secret")
    private val authenticationService =
        AidboxAuthenticationService(httpClient, BASE_URL, aidboxCredentials)
    private val authenticationBroker = AidboxAuthenticationBroker(authenticationService)

    private const val FHIR_URL = "$BASE_URL/fhir"
    const val RESOURCES_FORMAT = "$FHIR_URL/%s"
    private const val RESOURCE_FORMAT = "$RESOURCES_FORMAT/%s"

    fun getAuthorizationHeader(): String {
        val authentication = authenticationBroker.getAuthentication()
        return "${authentication.tokenType} ${authentication.accessToken}"
    }

    fun addResource(resource: Resource<*>): Resource<*> = runBlocking {
        val url = RESOURCES_FORMAT.format(resource.resourceType)
        val response = httpClient.post(url) {
            headers {
                append(HttpHeaders.Authorization, getAuthorizationHeader())
            }
            contentType(ContentType.Application.Json)
            setBody(resource)
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("None success returned from adding resource: ${response.bodyAsText()}")
        }

        getResource(resource.resourceType, resource.id!!.value!!)
    }

    fun getResource(resourceType: String, id: String): Resource<*> = runBlocking {
        val url = RESOURCE_FORMAT.format(resourceType, id)
        httpClient.get(url) {
            headers {
                append(HttpHeaders.Authorization, getAuthorizationHeader())
            }
        }.body()
    }

    fun deleteResource(resourceType: String, id: String) = runBlocking {
        val url = RESOURCE_FORMAT.format(resourceType, id)
        httpClient.delete(url) {
            headers {
                append(HttpHeaders.Authorization, getAuthorizationHeader())
            }
        }
    }
}
