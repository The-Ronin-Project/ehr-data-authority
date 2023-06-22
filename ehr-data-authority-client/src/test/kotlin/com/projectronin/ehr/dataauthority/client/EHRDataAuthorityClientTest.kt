package com.projectronin.ehr.dataauthority.client

import com.projectronin.ehr.dataauthority.client.auth.EHRDataAuthorityAuthenticationService
import com.projectronin.ehr.dataauthority.models.BatchResourceResponse
import com.projectronin.ehr.dataauthority.models.FailedResource
import com.projectronin.ehr.dataauthority.models.FoundResourceIdentifiers
import com.projectronin.ehr.dataauthority.models.Identifier
import com.projectronin.ehr.dataauthority.models.IdentifierSearchResponse
import com.projectronin.ehr.dataauthority.models.IdentifierSearchableResourceTypes
import com.projectronin.ehr.dataauthority.models.ModificationType
import com.projectronin.ehr.dataauthority.models.SucceededResource
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.common.http.exceptions.ServerFailureException
import com.projectronin.interop.common.http.ktor.ContentLengthSupplier
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.r4.datatype.HumanName
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URLEncoder

class EHRDataAuthorityClientTest {
    private val authenticationToken = "12345678"
    private val authenticationService = mockk<EHRDataAuthorityAuthenticationService> {
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
            val resourceToReturn = EHRDataAuthorityClient(url.toString(), client, authenticationService)
                .addResources("tenant", resource)
            resourceToReturn
        }
        assertEquals(resource[0].id?.value, response.succeeded[0].resourceId)
        assertEquals(resource[0].resourceType, response.succeeded[0].resourceType)
        assertEquals(response.succeeded[0].modificationType, ModificationType.CREATED)
        val request = mockWebServer.takeRequest()
        assertEquals(true, request.path?.endsWith("/tenants/tenant/resources"))
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
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4",
                    modificationType = ModificationType.CREATED
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
                EHRDataAuthorityClient(url.toString(), client, authenticationService).addResources(
                    "tenant",
                    listOfResources
                )
            resourceToReturn
        }

        val request = mockWebServer.takeRequest()
        val responseIds = mutableListOf<String>()
        response.succeeded.forEach { responseIds.add(it.resourceId) }
        assertTrue(responseIds == listOfResourceIds)
        assertEquals(true, request.path?.endsWith("/tenants/tenant/resources"))
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
                EHRDataAuthorityClient(url.toString(), client, authenticationService).addResources(
                    "tenant",
                    listOfResources
                )
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
            succeededResponseIds == listOf(
                "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1",
                "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2"
            )
        )
        assertEquals(true, request.path?.endsWith("/tenants/tenant/resources"))
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
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK5",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK5",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK5",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4",
                    modificationType = ModificationType.CREATED
                ),
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK5",
                    modificationType = ModificationType.CREATED
                )
            ),
            failed = listOf(
                FailedResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK1",
                    error = "Error publishing to data store"
                ),
                FailedResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK2",
                    error = "Error publishing to data store"
                ),
                FailedResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3",
                    error = "Error publishing to data store"
                ),
                FailedResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4",
                    error = "Error publishing to data store"
                ),
                FailedResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK5",
                    error = "Error publishing to data store"
                )
            )
        )
        val resourcesReturned2 = BatchResourceResponse(
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
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK4",
                    error = "Error publishing to data store"
                ),
                FailedResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK5",
                    error = "Error publishing to data store"
                ),
                FailedResource(
                    resourceType = "Patient",
                    resourceId = "FAKEFAKE-FAKE-FAKE-FAKE-FAKEFAKEFAK3",
                    error = "Error publishing to data store"
                )
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
            val resourcesToReturn =
                EHRDataAuthorityClient(url.toString(), client, authenticationService).addResources(
                    "tenant",
                    listOfResources
                )
            resourcesToReturn
        }

        val request = mockWebServer.takeRequest()
        assertEquals(true, request.path?.endsWith("/tenants/tenant/resources"))
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
                EHRDataAuthorityClient(url.toString(), client, authenticationService).addResources(
                    "tenant",
                    listOfResources
                )
            }
        }

        assertNotNull(exception.message)
        exception.message?.let { assertTrue(it.contains("400")) }

        val request = mockWebServer.takeRequest()
        assertEquals(true, request.path?.endsWith("/tenants/tenant/resources"))
        assertEquals("Bearer $authenticationToken", request.getHeader("Authorization"))
    }

    @Test
    fun `getResource works when found`() {
        val resourceId = "123"
        val resource: Resource<*> = Patient(
            id = Id(value = resourceId),
            name = listOf(HumanName(family = "Test".asFHIR()))
        )
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.OK.value)
                .setBody(JacksonManager.objectMapper.writeValueAsString(resource))
                .setHeader("Content-Type", "application/json")
        )
        val url = mockWebServer.url("/test")
        val response = runBlocking {
            EHRDataAuthorityClient(url.toString(), client, authenticationService)
                .getResource("tenant", "Patient", "123")
        }
        val returnedResource = response as Patient
        val request = mockWebServer.takeRequest()

        assertEquals("Test", returnedResource.name.first().family?.value)
        assertEquals("/test/tenants/tenant/resources/Patient/123", request.path)
    }

    @Test
    fun `getResource returns null for 404`() {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.NotFound.value)
                .setBody("{\"errorMessage\": \"No resources ever\"}")
                .setHeader("Content-Type", "application/json")
        )
        val url = mockWebServer.url("/test")

        val response = runBlocking {
            EHRDataAuthorityClient(url.toString(), client, authenticationService)
                .getResource("tenant", "Patient", "123")
        }
        val request = mockWebServer.takeRequest()

        assertNull(response)
        assertEquals("/test/tenants/tenant/resources/Patient/123", request.path)
    }

    @Test
    fun `getResource returns null for 410`() {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.Gone.value)
                .setBody("{\"errorMessage\": \"No resources ever\"}")
                .setHeader("Content-Type", "application/json")
        )
        val url = mockWebServer.url("/test")

        val response = runBlocking {
            EHRDataAuthorityClient(url.toString(), client, authenticationService)
                .getResource("tenant", "Patient", "123")
        }
        val request = mockWebServer.takeRequest()

        assertNull(response)
        assertEquals("/test/tenants/tenant/resources/Patient/123", request.path)
    }

    @Test
    fun `getResource throws exception for 500`() {
        val resourceId = "123"
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.InternalServerError.value)
                .setBody("{\"errorMessage\": \"No resources ever\"}")
                .setHeader("Content-Type", "application/json")
        )
        val url = mockWebServer.url("/test")

        assertThrows<ServerFailureException> {
            runBlocking {
                EHRDataAuthorityClient(url.toString(), client, authenticationService).getResource(
                    "tenant",
                    "Practitioner",
                    resourceId
                )
            }
        }
    }

    @Test
    fun `getResourceAs works when found`() {
        val resourceId = "123"
        val resource: Resource<*> = Patient(
            id = Id(value = resourceId),
            name = listOf(HumanName(family = "Test".asFHIR()))
        )
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.OK.value)
                .setBody(JacksonManager.objectMapper.writeValueAsString(resource))
                .setHeader("Content-Type", "application/json")
        )
        val url = mockWebServer.url("/test")
        val returnedResource = runBlocking {
            EHRDataAuthorityClient(url.toString(), client, authenticationService)
                .getResourceAs<Patient>("tenant", "Patient", "123")
        }
        val request = mockWebServer.takeRequest()

        assertEquals("Test", returnedResource!!.name.first().family?.value)
        assertEquals("/test/tenants/tenant/resources/Patient/123", request.path)
    }

    @Test
    fun `getResourceAs returns null for 404`() {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.NotFound.value)
                .setBody("{\"errorMessage\": \"No resources ever\"}")
                .setHeader("Content-Type", "application/json")
        )
        val url = mockWebServer.url("/test")

        val returnedResource = runBlocking {
            EHRDataAuthorityClient(url.toString(), client, authenticationService)
                .getResourceAs<Patient>("tenant", "Patient", "123")
        }
        val request = mockWebServer.takeRequest()

        assertNull(returnedResource)
        assertEquals("/test/tenants/tenant/resources/Patient/123", request.path)
    }

    @Test
    fun `getResourceAs returns null for 410`() {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.Gone.value)
                .setBody("{\"errorMessage\": \"No resources ever\"}")
                .setHeader("Content-Type", "application/json")
        )
        val url = mockWebServer.url("/test")

        val returnedResource = runBlocking {
            EHRDataAuthorityClient(url.toString(), client, authenticationService)
                .getResourceAs<Patient>("tenant", "Patient", "123")
        }
        val request = mockWebServer.takeRequest()

        assertNull(returnedResource)
        assertEquals("/test/tenants/tenant/resources/Patient/123", request.path)
    }

    @Test
    fun `getResourceAs throws exception for 500`() {
        val resourceId = "123"
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.InternalServerError.value)
                .setBody("{\"errorMessage\": \"No resources ever\"}")
                .setHeader("Content-Type", "application/json")
        )
        val url = mockWebServer.url("/test")

        assertThrows<ServerFailureException> {
            runBlocking {
                EHRDataAuthorityClient(url.toString(), client, authenticationService).getResourceAs<Patient>(
                    "tenant",
                    "Practitioner",
                    resourceId
                )
            }
        }
    }

    @Test
    fun `getResourceIdentifiers works`() {
        val ident1 = Identifier("http://projectronin.com/id/mrn", "value1")
        val ident2 = Identifier("system2", "value2")

        val searchResult = listOf(
            IdentifierSearchResponse(
                searchedIdentifier = ident1,
                foundResources = listOf(
                    FoundResourceIdentifiers("udpId1", listOf(ident1, Identifier("notSearched", "notSearched"))),
                    FoundResourceIdentifiers("udpId2", listOf(ident1, Identifier("notSearched2", "notSearched2")))
                )
            ),
            IdentifierSearchResponse(
                searchedIdentifier = ident2,
                foundResources = listOf(
                    FoundResourceIdentifiers("udpId3", listOf(ident2))
                )
            )
        )
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.OK.value)
                .setBody(JacksonManager.objectMapper.writeValueAsString(searchResult))
                .setHeader("Content-Type", "application/json")
        )
        val url = mockWebServer.url("/test")
        val identifers = listOf(
            ident1,
            ident2
        )
        val response = runBlocking {
            EHRDataAuthorityClient(url.toString(), client, authenticationService)
                .getResourceIdentifiers("tenant", IdentifierSearchableResourceTypes.Location, identifers)
        }
        val request = mockWebServer.takeRequest()

        assertEquals(2, response.size)
        assertEquals(2, response[0].foundResources.size)
        assertEquals(ident1, response[0].searchedIdentifier)
        assertEquals(1, response[1].foundResources.size)
        assertEquals(
            "/test/tenants/tenant/resources/Location/identifiers?" +
                "identifier=${encode("http://projectronin.com/id/mrn|value1")}&identifier=${encode("system2|value2")}",
            request.path
        )
    }

    @Test
    fun `deleteResource returns success when OK is returned from request`() {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse().setResponseCode(HttpStatusCode.OK.value))
        val url = mockWebServer.url("/test")

        runBlocking {
            EHRDataAuthorityClient(url.toString(), client, authenticationService)
                .deleteResource("tenant", "Location", "tenant-1234")
        }

        val request = mockWebServer.takeRequest()
        assertEquals(
            "/test/tenants/tenant/resources/Location/tenant-1234",
            request.path
        )
        assertEquals("DELETE", request.method)
    }

    @Test
    fun `deleteResource returns failure when OK is not returned from request`() {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse().setResponseCode(HttpStatusCode.Gone.value))
        val url = mockWebServer.url("/test")

        assertThrows<ClientFailureException> {
            runBlocking {
                EHRDataAuthorityClient(url.toString(), client, authenticationService)
                    .deleteResource("tenant", "Location", "tenant-1234")
            }
        }

        val request = mockWebServer.takeRequest()
        assertEquals(
            "/test/tenants/tenant/resources/Location/tenant-1234",
            request.path
        )
        assertEquals("DELETE", request.method)
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
