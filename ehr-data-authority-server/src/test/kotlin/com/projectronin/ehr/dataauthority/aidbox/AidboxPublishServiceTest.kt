package com.projectronin.ehr.dataauthority.aidbox

import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.HumanName
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AidboxPublishServiceTest {
    private val collection = listOf(
        Practitioner(
            id = Id("cmjones"),
            identifier = listOf(
                Identifier(system = CodeSystem.NPI.uri, value = "third".asFHIR())
            ),
            name = listOf(
                HumanName(family = "Jones".asFHIR(), given = listOf("Cordelia", "May").asFHIR())
            )
        ),
        Practitioner(
            id = Id("rallyr"),
            identifier = listOf(
                Identifier(system = CodeSystem.NPI.uri, value = "second".asFHIR())
            ),
            name = listOf(
                HumanName(
                    family = "Llyr".asFHIR(),
                    given = listOf("Regan", "Anne").asFHIR()
                )
            )
        )
    )
    private val aidboxClient = mockk<AidboxClient>()
    private val aidboxPublishService = AidboxPublishService(aidboxClient, 2)

    @Test
    fun `publish list of 2 Practitioner resources to aidbox, response 200 true`() {
        val httpResponse = mockk<HttpResponse>()
        coEvery { httpResponse.status } returns HttpStatusCode.OK
        coEvery { aidboxClient.batchUpsert(collection) } returns httpResponse
        val actualSuccess: Boolean = runBlocking {
            aidboxPublishService.publish(collection)
        }
        assertTrue(actualSuccess)
    }

    @Test
    fun `empty list, return true`() {
        val httpResponse = mockk<HttpResponse>()
        val collection = listOf<Resource<*>>()
        coEvery { httpResponse.status } returns HttpStatusCode.OK
        coEvery { aidboxClient.batchUpsert(collection) } returns httpResponse
        val actualSuccess: Boolean = runBlocking {
            aidboxPublishService.publish(collection)
        }
        assertTrue(actualSuccess)
    }

    @Test
    fun `publish list of 2 Practitioner resources to aidbox, response 1xx (or 2xx) false`() {
        val httpResponse = mockk<HttpResponse>()
        coEvery { httpResponse.status } returns HttpStatusCode.Continue
        coEvery { aidboxClient.batchUpsert(collection) } returns httpResponse
        val actualSuccess: Boolean = runBlocking {
            aidboxPublishService.publish(collection)
        }
        assertFalse(actualSuccess)
    }

    @Test
    fun `publish list of 2 Practitioner resources to aidbox, exception 5xx (or 3xx or 4xx) false`() {
        val httpResponse = mockk<HttpResponse>()
        coEvery { httpResponse.status } returns HttpStatusCode.ServiceUnavailable
        coEvery { aidboxClient.batchUpsert(collection) } returns httpResponse
        val actualSuccess: Boolean = runBlocking {
            aidboxPublishService.publish(collection)
        }
        assertFalse(actualSuccess)
    }

    @Test
    fun `uses batches for larger collections`() {
        val httpResponse = mockk<HttpResponse>()
        coEvery { httpResponse.status } returns HttpStatusCode.OK

        val resource1 = mockk<Patient>()
        val resource2 = mockk<Appointment>()
        val resource3 = mockk<Practitioner>()
        val resource4 = mockk<Condition>()
        val resource5 = mockk<Location>()

        coEvery { aidboxClient.batchUpsert(listOf(resource1, resource2)) } returns httpResponse
        coEvery { aidboxClient.batchUpsert(listOf(resource3, resource4)) } returns httpResponse
        coEvery { aidboxClient.batchUpsert(listOf(resource5)) } returns httpResponse

        val actualSuccess: Boolean = runBlocking {
            aidboxPublishService.publish(listOf(resource1, resource2, resource3, resource4, resource5))
        }
        assertTrue(actualSuccess)
    }

    @Test
    fun `continues processing batches even after a failure`() {
        val okHttpResponse = mockk<HttpResponse>()
        coEvery { okHttpResponse.status } returns HttpStatusCode.OK

        val failureHttpResponse = mockk<HttpResponse>()
        coEvery { failureHttpResponse.status } returns HttpStatusCode.ServiceUnavailable

        val resource1 = mockk<Patient>()
        val resource2 = mockk<Appointment>()
        val resource3 = mockk<Practitioner>()
        val resource4 = mockk<Condition>()
        val resource5 = mockk<Location>()

        coEvery { aidboxClient.batchUpsert(listOf(resource1, resource2)) } returns failureHttpResponse
        coEvery { aidboxClient.batchUpsert(listOf(resource3, resource4)) } returns okHttpResponse
        coEvery { aidboxClient.batchUpsert(listOf(resource5)) } returns okHttpResponse

        val actualSuccess: Boolean = runBlocking {
            aidboxPublishService.publish(listOf(resource1, resource2, resource3, resource4, resource5))
        }
        assertFalse(actualSuccess)

        coVerify(exactly = 1) { aidboxClient.batchUpsert(listOf(resource1, resource2)) }
        coVerify(exactly = 1) { aidboxClient.batchUpsert(listOf(resource3, resource4)) }
        coVerify(exactly = 1) { aidboxClient.batchUpsert(listOf(resource5)) }
    }
}
