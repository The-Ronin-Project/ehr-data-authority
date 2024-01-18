package com.projectronin.ehr.dataauthority.testclients

import com.projectronin.interop.common.http.auth.AuthMethod
import com.projectronin.interop.common.http.auth.AuthenticationConfig
import com.projectronin.interop.common.http.auth.Client
import com.projectronin.interop.common.http.auth.InteropAuthenticationService
import com.projectronin.interop.common.http.auth.Token
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.validation.client.ResourceClient
import com.projectronin.interop.validation.client.generated.models.Order
import com.projectronin.interop.validation.client.generated.models.Resource
import com.projectronin.interop.validation.client.generated.models.ResourceStatus
import com.projectronin.interop.validation.client.generated.models.UpdatableResourceStatus
import com.projectronin.interop.validation.client.generated.models.UpdateResource
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking

object ValidationClient {
    val httpClient =
        HttpClient(CIO) {
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

    private val authenticationConfig =
        AuthenticationConfig(
            token = Token(url = "http://localhost:8081/validation/token"),
            audience = "https://interop-validation.dev.projectronin.io",
            client = Client(id = "validation-client", secret = "client-secret"),
            method = AuthMethod.STANDARD,
        )
    private val authenticationService = InteropAuthenticationService(httpClient, authenticationConfig)
    private val resourcesClient = ResourceClient("http://localhost:8082", httpClient, authenticationService)

    fun getResources(): List<Resource> =
        runBlocking {
            resourcesClient.getResources(listOf(ResourceStatus.REPORTED), Order.DESC, 50)
        }

    fun clearResources(resources: List<Resource>) {
        runBlocking {
            resources.forEach { resource ->
                resourcesClient.updateResource(resource.id, UpdateResource(UpdatableResourceStatus.IGNORED))
            }
        }
    }

    fun clearAllResources() {
        runBlocking {
            do {
                val resources = getResources()
                resources.forEach { resource ->
                    resourcesClient.updateResource(resource.id, UpdateResource(UpdatableResourceStatus.IGNORED))
                }
            } while (resources.isNotEmpty())
        }
    }
}
