package com.projectronin.ehr.dataauthority.aidbox

import com.projectronin.ehr.dataauthority.publish.PublishService
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
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PublishServiceTest {
    private val collection =
        listOf(
            Practitioner(
                id = Id("cmjones"),
                identifier =
                    listOf(
                        Identifier(system = CodeSystem.NPI.uri, value = "third".asFHIR()),
                    ),
                name =
                    listOf(
                        HumanName(family = "Jones".asFHIR(), given = listOf("Cordelia", "May").asFHIR()),
                    ),
            ),
            Practitioner(
                id = Id("rallyr"),
                identifier =
                    listOf(
                        Identifier(system = CodeSystem.NPI.uri, value = "second".asFHIR()),
                    ),
                name =
                    listOf(
                        HumanName(
                            family = "Llyr".asFHIR(),
                            given = listOf("Regan", "Anne").asFHIR(),
                        ),
                    ),
            ),
        )
    private val dataStorageService = mockk<AidboxClient>()
    private val publishService = PublishService(dataStorageService, 2)

    @Test
    fun `publish list of 2 Practitioner resources to aidbox, response 200 true`() {
        coEvery {
            runBlocking { dataStorageService.batchUpsert(collection) }
        } returns (HttpStatusCode.OK)

        val actualSuccess = runBlocking { publishService.publish(collection) }
        assertTrue(actualSuccess)
    }

    @Test
    fun `empty list, return true`() {
        val collection = listOf<Resource<*>>()
        coEvery { dataStorageService.batchUpsert(collection) } returns mockk { HttpStatusCode.OK }
        val actualSuccess: Boolean =
            runBlocking {
                publishService.publish(collection)
            }
        assertTrue(actualSuccess)
    }

    @Test
    fun `publish list of 2 Practitioner resources to aidbox, response 1xx (or 2xx) false`() {
        coEvery { dataStorageService.batchUpsert(collection) } returns mockk { HttpStatusCode.Continue }
        val actualSuccess: Boolean =
            runBlocking {
                publishService.publish(collection)
            }
        assertFalse(actualSuccess)
    }

    @Test
    fun `publish list of 2 Practitioner resources to aidbox, exception 5xx (or 3xx or 4xx) false`() {
        coEvery { dataStorageService.batchUpsert(collection) } returns mockk { HttpStatusCode.ServiceUnavailable }
        val actualSuccess: Boolean =
            runBlocking {
                publishService.publish(collection)
            }
        assertFalse(actualSuccess)
    }

    @Test
    fun `uses batches for larger collections`() {
        val resource1 = mockk<Patient>()
        val resource2 = mockk<Appointment>()
        val resource3 = mockk<Practitioner>()
        val resource4 = mockk<Condition>()
        val resource5 = mockk<Location>()

        coEvery { dataStorageService.batchUpsert(listOf(resource1, resource2)) } returns HttpStatusCode.OK andThen HttpStatusCode.OK
        coEvery { dataStorageService.batchUpsert(listOf(resource3, resource4)) } returns HttpStatusCode.OK andThen HttpStatusCode.OK
        coEvery { dataStorageService.batchUpsert(listOf(resource5)) } returns HttpStatusCode.OK

        val actualSuccess: Boolean =
            runBlocking {
                publishService.publish(listOf(resource1, resource2, resource3, resource4, resource5))
            }
        assertTrue(actualSuccess)
    }

    @Test
    fun `continues processing batches even after a failure`() {
        val resource1 = mockk<Patient>()
        val resource2 = mockk<Appointment>()
        val resource3 = mockk<Practitioner>()
        val resource4 = mockk<Condition>()
        val resource5 = mockk<Location>()

        coEvery { dataStorageService.batchUpsert(listOf(resource1, resource2)) } returns mockk { HttpStatusCode.ServiceUnavailable }
        coEvery { dataStorageService.batchUpsert(listOf(resource3, resource4)) } returns mockk { HttpStatusCode.OK }
        coEvery { dataStorageService.batchUpsert(listOf(resource5)) } returns mockk { HttpStatusCode.OK }

        val actualSuccess: Boolean =
            runBlocking {
                publishService.publish(listOf(resource1, resource2, resource3, resource4, resource5))
            }
        assertFalse(actualSuccess)

        coVerify(exactly = 1) { dataStorageService.batchUpsert(listOf(resource1, resource2)) }
        coVerify(exactly = 1) { dataStorageService.batchUpsert(listOf(resource3, resource4)) }
        coVerify(exactly = 1) { dataStorageService.batchUpsert(listOf(resource5)) }
    }
}
