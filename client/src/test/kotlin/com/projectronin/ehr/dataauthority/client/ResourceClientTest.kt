package com.projectronin.ehr.dataauthority.client

import com.projectronin.ehr.dataauthority.client.auth.DataAuthorityAuthenticationService
import com.projectronin.ehr.dataauthority.client.models.BatchResourceResponse
import com.projectronin.ehr.dataauthority.client.models.FailedResource
import com.projectronin.ehr.dataauthority.client.models.ModificationType
import com.projectronin.ehr.dataauthority.client.models.SucceededResource
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.common.http.ktor.ContentLengthSupplier
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResourceClientTest {
    private val authenticationToken = "12345678"
    private val authenticationService = mockk<DataAuthorityAuthenticationService> {
        every { getAuthentication() } returns mockk {
            every { accessToken } returns authenticationToken
        }
    }
    private val client: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            jackson {
                JacksonManager.setUpMapper(this)
            }
        }
        install(ContentLengthSupplier)
    }

    @Test
    fun `addResources works with one resource being sent`() {
        val resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAKE"
        val resource = listOf<Resource<*>>(
            Patient(id = Id(value = resourceId))
        )
        val resourceReturn = BatchResourceResponse(
            succeeded = listOf(
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAKE",
                    modificationType = ModificationType.CREATED
                )
            )
        )

        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.OK.value)
                .setBody(JacksonManager.objectMapper.writeValueAsString(resourceReturn))
                .setHeader("Content-Type", "application/json")
        )
        val url = mockWebServer.url("/test")
        val response = runBlocking {
            val resourceToReturn = ResourceClient(url.toString(), client, authenticationService)
                .addResources(resource)
            resourceToReturn
        }
        assertEquals(resource[0].id?.value, response.succeeded[0].resourceId)
        assertEquals(resource[0].resourceType, response.succeeded[0].resourceType)
        assertEquals(response.succeeded[0].modificationType, ModificationType.CREATED)
        val request = mockWebServer.takeRequest()
        assertEquals(true, request.path?.endsWith("/resources"))
        assertEquals("Bearer $authenticationToken", request.getHeader("Authorization"))
    }

    @Test
    fun `addResources works with smaller batch size`() {
        val resourceId1 = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1"
        val resource1 = Patient(Id(value = resourceId1))
        val resourceId2 = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2"
        val resource2 = Patient(Id(value = resourceId2))
        val resourceId3 = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3"
        val resource3 = Patient(Id(value = resourceId3))
        val resourceId4 = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4"
        val resource4 = Patient(Id(value = resourceId4))

        val listOfResourceIds = listOf(resourceId1, resourceId2, resourceId3, resourceId4)
        val listOfResources = listOf<Resource<*>>(resource1, resource2, resource3, resource4)
        val returnedResources = BatchResourceResponse(
            succeeded = listOf(
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4", modificationType = ModificationType.CREATED)
            )
        )

        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.OK.value)
                .setBody(JacksonManager.objectMapper.writeValueAsString(returnedResources))
                .setHeader("Content-Type", "application/json")
        )
        val url = mockWebServer.url("/test")
        val response = runBlocking {
            val resourceToReturn = ResourceClient(url.toString(), client, authenticationService).addResources(listOfResources)
            resourceToReturn
        }

        val request = mockWebServer.takeRequest()
        val responseIds = mutableListOf<String>()
        response.succeeded.forEach { responseIds.add(it.resourceId) }
        assertTrue(responseIds == listOfResourceIds)
        assertEquals(true, request.path?.endsWith("/resources"))
        assertEquals(response.succeeded.size, 4)
        assertEquals("Bearer $authenticationToken", request.getHeader("Authorization"))
    }

    @Test
    fun `addResources works with smaller batch size of failed and succeeded resources`() {
        val resourceId1 = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1"
        val resource1 = Patient(Id(value = resourceId1))
        val resourceId2 = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2"
        val resource2 = Patient(Id(value = resourceId2))
        val resourceId3 = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3"
        val resource3 = Patient(Id(value = resourceId3))
        val resourceId4 = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4"
        val resource4 = Patient(Id(value = resourceId4))

        val listOfResources = listOf<Resource<*>>(resource1, resource2, resource3, resource4)
        val returnedResources = BatchResourceResponse(
            succeeded = listOf(
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2",
                    modificationType = ModificationType.CREATED
                )
            ),
            failed = listOf(
                FailedResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3",
                    error = "Error publishing to data store"
                ),
                FailedResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4",
                    error = "Error publishing to data store"
                )
            )
        )
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.OK.value)
                .setBody(JacksonManager.objectMapper.writeValueAsString(returnedResources))
                .setHeader("Content-Type", "application/json")
        )
        val url = mockWebServer.url("/test")
        val response = runBlocking {
            val resourceToReturn =
                ResourceClient(url.toString(), client, authenticationService).addResources(listOfResources)
            resourceToReturn
        }

        val request = mockWebServer.takeRequest()
        val failedResponseIds = mutableListOf<String>()
        val succeededResponseIds = mutableListOf<String>()
        response.failed.forEach { failedResponseIds.add(it.resourceId) }
        response.succeeded.forEach { succeededResponseIds.add(it.resourceId) }
        assertTrue(
            failedResponseIds == listOf("FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3", "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4")
        )
        assertTrue(
            succeededResponseIds == listOf("FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1", "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2")
        )
        assertEquals(true, request.path?.endsWith("/resources"))
        assertEquals(response.succeeded.size, 2)
        assertEquals(response.failed.size, 2)
        assertEquals("Bearer $authenticationToken", request.getHeader("Authorization"))
    }

    @Test
    fun `addResources works with larger batch size`() {
        val resourceId1 = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1"
        val resource1 = Patient(Id(value = resourceId1))
        val resourceId2 = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2"
        val resource2 = Patient(Id(value = resourceId2))
        val resourceId3 = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3"
        val resource3 = Patient(Id(value = resourceId3))
        val resourceId4 = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4"
        val resource4 = Patient(Id(value = resourceId4))
        val resourceId5 = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK5"
        val resource5 = Patient(Id(value = resourceId5))

        val listOfResources = listOf<Resource<*>>(
            resource1, resource2, resource3, resource4, resource5,
            resource1, resource2, resource3, resource4, resource5,
            resource1, resource2, resource3, resource4, resource5,
            resource1, resource2, resource3, resource4, resource5,
            resource1, resource2, resource3, resource4, resource5,
            resource1, resource2, resource3, resource4, resource5
        )

        val resourcesReturned1 = BatchResourceResponse(
            succeeded = listOf(
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK5", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK5", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK5", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK5", modificationType = ModificationType.CREATED)
            ),
            failed = listOf(
                FailedResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1", error = "Error publishing to data store"),
                FailedResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2", error = "Error publishing to data store"),
                FailedResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3", error = "Error publishing to data store"),
                FailedResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4", error = "Error publishing to data store"),
                FailedResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK5", error = "Error publishing to data store")
            )
        )
        val resourcesReturned2 = BatchResourceResponse(
            succeeded = listOf(
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1", modificationType = ModificationType.CREATED),
                SucceededResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2", modificationType = ModificationType.CREATED)
            ),
            failed = listOf(
                FailedResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4", error = "Error publishing to data store"),
                FailedResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK5", error = "Error publishing to data store"),
                FailedResource(resourceType = "Patient", resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3", error = "Error publishing to data store")
            )
        )
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue( // mock first response from chunk
            MockResponse()
                .setResponseCode(HttpStatusCode.OK.value)
                .setBody(JacksonManager.objectMapper.writeValueAsString(resourcesReturned1))
                .setHeader("Content-Type", "application/json")
        )
        mockWebServer.enqueue( // mock second response from chunk
            MockResponse()
                .setResponseCode(HttpStatusCode.OK.value)
                .setBody(JacksonManager.objectMapper.writeValueAsString(resourcesReturned2))
                .setHeader("Content-Type", "application/json")
        )

        val url = mockWebServer.url("/test")
        val response = runBlocking {
            val resourcesToReturn = ResourceClient(url.toString(), client, authenticationService).addResources(listOfResources)
            resourcesToReturn
        }

        val request = mockWebServer.takeRequest()
        assertEquals(true, request.path?.endsWith("/resources"))
        assertEquals(response.failed.size, 8)
        assertEquals(response.succeeded.size, 22)
        assertTrue(response.failed.all { it.error == "Error publishing to data store" })
        assertEquals("Bearer $authenticationToken", request.getHeader("Authorization"))
    }

    @Test
    fun `addResources fails`() {
        val resourceId1 = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1"
        val resource1 = Patient(Id(value = resourceId1))
        val resourceId2 = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2"
        val resource2 = Patient(Id(value = resourceId2))
        val listOfResources = listOf<Resource<*>>(resource1, resource2)
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.BadRequest.value)
                .setHeader("Content-Type", "application/json")
        )
        val url = mockWebServer.url("/test")
        val exception = assertThrows<ClientFailureException> {
            runBlocking {
                ResourceClient(url.toString(), client, authenticationService).addResources(listOfResources)
            }
        }

        assertNotNull(exception.message)
        exception.message?.let { assertTrue(it.contains("400")) }

        val request = mockWebServer.takeRequest()
        assertEquals(true, request.path?.endsWith("/resources"))
        assertEquals("Bearer $authenticationToken", request.getHeader("Authorization"))
    }
}
