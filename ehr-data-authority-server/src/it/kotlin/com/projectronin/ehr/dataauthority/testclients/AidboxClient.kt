package com.projectronin.ehr.dataauthority.testclients

import com.projectronin.ehr.dataauthority.aidbox.AidboxClient
import com.projectronin.ehr.dataauthority.aidbox.auth.AidboxAuthenticationBroker
import com.projectronin.ehr.dataauthority.aidbox.auth.AidboxAuthenticationService
import com.projectronin.ehr.dataauthority.aidbox.auth.AidboxCredentials
import com.projectronin.interop.common.http.spring.HttpSpringConfig
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking

object AidboxClient {
    private val httpClient = HttpSpringConfig().getHttpClient()

    private const val BASE_URL = "http://localhost:8888"

    private val aidboxCredentials = AidboxCredentials("client", "secret")
    private val authenticationService =
        AidboxAuthenticationService(httpClient, BASE_URL, aidboxCredentials)
    private val authenticationBroker = AidboxAuthenticationBroker(authenticationService)
    private val aidboxClient = AidboxClient(httpClient, BASE_URL, authenticationBroker)

    fun addResource(resource: Resource<*>): Resource<*> = runBlocking {
        val response = aidboxClient.batchUpsert(listOf(resource))
        if (!response.isSuccess()) {
            throw IllegalStateException("None success returned from adding resource: ${response.description}")
        }

        getResource(resource.resourceType, resource.id!!.value!!)
    }

    fun getResource(resourceType: String, id: String): Resource<*> = runBlocking {
        aidboxClient.getResource(resourceType, id)
    }

    fun deleteResource(resourceType: String, id: String) = runBlocking {
        aidboxClient.deleteResource(resourceType, id)
    }
}
