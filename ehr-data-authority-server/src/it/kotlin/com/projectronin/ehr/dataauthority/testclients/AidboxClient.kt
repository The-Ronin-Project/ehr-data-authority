package com.projectronin.ehr.dataauthority.testclients

import com.projectronin.ehr.dataauthority.aidbox.AidboxClient
import com.projectronin.ehr.dataauthority.aidbox.auth.AidboxAuthenticationBroker
import com.projectronin.ehr.dataauthority.aidbox.auth.AidboxAuthenticationService
import com.projectronin.ehr.dataauthority.aidbox.auth.AidboxCredentials
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.statement.bodyAsText
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
    private val aidboxClient = AidboxClient(httpClient, BASE_URL, authenticationBroker)

    fun addResource(resource: Resource<*>): Resource<*> = runBlocking {
        val response = aidboxClient.batchUpsert(listOf(resource))
        if (!response.status.isSuccess()) {
            throw IllegalStateException("None success returned from adding resource: ${response.bodyAsText()}")
        }

        getResource(resource.resourceType, resource.id!!.value!!)
    }

    fun getResource(resourceType: String, id: String): Resource<*> = runBlocking {
        aidboxClient.getResource(resourceType, id).body()
    }

    fun deleteResource(resourceType: String, id: String) = runBlocking {
        aidboxClient.deleteResource(resourceType, id)
    }
}
