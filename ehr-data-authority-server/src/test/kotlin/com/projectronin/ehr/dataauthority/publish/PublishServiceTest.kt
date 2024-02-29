package com.projectronin.ehr.dataauthority.publish

import com.projectronin.ehr.dataauthority.aidbox.AidboxClient
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `publish list of 2 Practitioner resources to aidbox`() {
        coEvery {
            runBlocking { dataStorageService.batchUpsert(collection) }
        } returns collection

        val actualResponse = runBlocking { publishService.publish(collection) }
        assertEquals(collection, actualResponse)
    }

    @Test
    fun `supports empty list`() {
        val collection = listOf<Resource<*>>()
        val actualResponse =
            runBlocking {
                publishService.publish(collection)
            }
        assertEquals(collection, actualResponse)
    }

    @Test
    fun `uses batches for larger collections`() {
        val resource1 = mockk<Patient>()
        val resource2 = mockk<Appointment>()
        val resource3 = mockk<Practitioner>()
        val resource4 = mockk<Condition>()
        val resource5 = mockk<Location>()

        coEvery {
            dataStorageService.batchUpsert(
                listOf(
                    resource1,
                    resource2,
                ),
            )
        } returns listOf(resource1, resource2)
        coEvery {
            dataStorageService.batchUpsert(
                listOf(
                    resource3,
                    resource4,
                ),
            )
        } returns listOf(resource3, resource4)
        coEvery { dataStorageService.batchUpsert(listOf(resource5)) } returns listOf(resource5)

        val actualResponse =
            runBlocking {
                publishService.publish(listOf(resource1, resource2, resource3, resource4, resource5))
            }
        assertEquals(listOf(resource1, resource2, resource3, resource4, resource5), actualResponse)
    }

    @Test
    fun `continues processing batches even after a failure`() {
        val resource1 = mockk<Patient>()
        val resource2 = mockk<Appointment>()
        val resource3 = mockk<Practitioner>()
        val resource4 = mockk<Condition>()
        val resource5 = mockk<Location>()

        coEvery {
            dataStorageService.batchUpsert(
                listOf(
                    resource1,
                    resource2,
                ),
            )
        } throws IllegalStateException("Exception!")
        coEvery { dataStorageService.batchUpsert(listOf(resource3, resource4)) } returns listOf(resource3, resource4)
        coEvery { dataStorageService.batchUpsert(listOf(resource5)) } returns listOf(resource5)

        val actualResponse =
            runBlocking {
                publishService.publish(listOf(resource1, resource2, resource3, resource4, resource5))
            }
        assertEquals(listOf(resource3, resource4, resource5), actualResponse)

        coVerify(exactly = 1) { dataStorageService.batchUpsert(listOf(resource1, resource2)) }
        coVerify(exactly = 1) { dataStorageService.batchUpsert(listOf(resource3, resource4)) }
        coVerify(exactly = 1) { dataStorageService.batchUpsert(listOf(resource5)) }
    }
}
