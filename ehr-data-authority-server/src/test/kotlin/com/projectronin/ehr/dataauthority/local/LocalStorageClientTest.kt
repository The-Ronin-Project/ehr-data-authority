package com.projectronin.ehr.dataauthority.local

import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.HumanName
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.Period
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.DateTime
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRBoolean
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.AvailableTime
import com.projectronin.interop.fhir.r4.resource.Bundle
import com.projectronin.interop.fhir.r4.resource.BundleEntry
import com.projectronin.interop.fhir.r4.resource.NotAvailable
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.resource.PractitionerRole
import com.projectronin.interop.fhir.r4.valueset.BundleType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LocalStorageClientTest {
    private val localStorageClient = LocalStorageClient()
    private val practitioner1 =
        Practitioner(
            id = Id("cmjones"),
            identifier =
                listOf(
                    Identifier(system = CodeSystem.RONIN_TENANT.uri, value = "test".asFHIR()),
                    Identifier(system = CodeSystem.NPI.uri, value = "third".asFHIR()),
                    Identifier(system = Uri("system"), value = "value".asFHIR()),
                ),
            name = listOf(HumanName(family = "Jones".asFHIR(), given = listOf("Cordelia", "May").asFHIR())),
        )
    private val practitioner2 =
        Practitioner(
            id = Id("rallyr"),
            identifier =
                listOf(
                    Identifier(system = CodeSystem.NPI.uri, value = "second".asFHIR()),
                ),
            name = listOf(HumanName(family = "Llyr".asFHIR(), given = listOf("Regan", "Anne").asFHIR())),
        )
    private val practitioner3 =
        Practitioner(
            id = Id("gwalsh"),
            identifier =
                listOf(
                    Identifier(system = CodeSystem.NPI.uri, value = "first".asFHIR()),
                ),
            name = listOf(HumanName(family = "Walsh".asFHIR(), given = listOf("Goneril").asFHIR())),
        )
    private val practitionerRole1 =
        PractitionerRole(
            id = Id("12347"),
            identifier =
                listOf(
                    Identifier(system = CodeSystem.NPI.uri, value = "id3".asFHIR()),
                ),
            active = FHIRBoolean.TRUE,
            period = Period(end = DateTime("2022")),
            practitioner = Reference(reference = "Practitioner/cmjones".asFHIR()),
            location = listOf(Reference(reference = "Location/12345".asFHIR())),
            healthcareService = listOf(Reference(reference = "HealthcareService/3456".asFHIR())),
            availableTime = listOf(AvailableTime(allDay = FHIRBoolean.FALSE)),
            notAvailable = listOf(NotAvailable(description = "Not available now".asFHIR())),
            availabilityExceptions = "exceptions".asFHIR(),
            endpoint = listOf(Reference(reference = "Endpoint/1357".asFHIR())),
        )
    private val practitionerRole2 =
        PractitionerRole(
            id = Id("12348"),
            identifier =
                listOf(
                    Identifier(system = CodeSystem.NPI.uri, value = "id4".asFHIR()),
                ),
            active = FHIRBoolean.TRUE,
            period = Period(end = DateTime("2022")),
            practitioner = Reference(reference = "Practitioner/rallyr".asFHIR()),
            location = listOf(Reference(reference = "Location/12346".asFHIR())),
            healthcareService = listOf(Reference(reference = "HealthcareService/3456".asFHIR())),
            availableTime = listOf(AvailableTime(allDay = FHIRBoolean.TRUE)),
            notAvailable = listOf(NotAvailable(description = "Available now".asFHIR())),
            availabilityExceptions = "No exceptions".asFHIR(),
            endpoint =
                listOf(
                    Reference(reference = "Endpoint/1358".asFHIR()),
                    Reference(reference = "Endpoint/1359".asFHIR()),
                ),
        )
    private val practitioners = listOf(practitioner1, practitioner2, practitioner3)
    private val practitionerRoles = listOf(practitionerRole1, practitionerRole2)

    @BeforeEach
    fun `add data to localStorageMap`() {
        runBlocking { localStorageClient.batchUpsert(practitioners) }
    }

    @AfterEach
    fun `clear localStorageMap and localHashMap`() {
        runBlocking { localStorageClient.deleteAllResources() }
    }

    @Test
    fun `resource retrieve test`() {
        val actual = localStorageClient.getResource("Practitioner", "cmjones")
        assertEquals("Practitioner", actual.resourceType)
        assertEquals("cmjones", actual.id?.value)
        assertEquals(practitioner1, actual)

        val actual2 = localStorageClient.getResource("Practitioner", "rallyr")
        assertEquals("Practitioner", actual2.resourceType)
        assertEquals("rallyr", actual2.id?.value)
        assertEquals(practitioner2, actual2)
    }

    @Test
    fun `delete resource works`() {
        //  3 practitioner resources already added
        val expectedResponseStatus = HttpStatusCode.OK
        val actualResponse = runBlocking { localStorageClient.deleteResource("Practitioner", "cmjones") }
        assertEquals(actualResponse, expectedResponseStatus)
    }

    @Test
    fun `delete all works for localStorage`() {
        // check that there is something to delete
        val actual = localStorageClient.getResource("Practitioner", "cmjones")
        assertEquals("Practitioner", actual.resourceType)
        assertEquals("cmjones", actual.id?.value)
        assertEquals(practitioner1, actual)

        val removeItAll = runBlocking { localStorageClient.deleteAllResources() }
        assertEquals(removeItAll, HttpStatusCode.OK)
    }

    @Test
    fun `localStorage batch upsert of 2 Practitioners returns response 200`() {
        runBlocking { localStorageClient.deleteAllResources() } // clear storage
        val expectedResponseStatus = HttpStatusCode.OK
        val actualResponse =
            runBlocking {
                localStorageClient.batchUpsert(practitioners)
            }
        assertEquals(expectedResponseStatus, actualResponse)
    }

    @Test
    fun `localStorage batchupsert of PractitionerRoles with reference targets missing, returns 200 - no validation`() {
        runBlocking { localStorageClient.deleteAllResources() } // clear storage
        val expectedResponseStatus = HttpStatusCode.OK
        val actualResponse =
            runBlocking {
                localStorageClient.batchUpsert(practitionerRoles)
            }
        assertEquals(actualResponse, expectedResponseStatus)
    }

    @Test
    fun `search works localStorageClient`() {
        runBlocking {
            localStorageClient.batchUpsert(practitionerRoles)
        }
        val entries = mutableListOf<BundleEntry>()
        entries.add(
            BundleEntry(
                resource =
                    Practitioner(
                        id = Id("cmjones"),
                        identifier =
                            listOf(
                                Identifier(system = CodeSystem.RONIN_TENANT.uri, value = "test".asFHIR()),
                                Identifier(system = CodeSystem.NPI.uri, value = "third".asFHIR()),
                                Identifier(system = Uri("system"), value = "value".asFHIR()),
                            ),
                        name = listOf(HumanName(family = "Jones".asFHIR(), given = listOf("Cordelia", "May").asFHIR())),
                    ),
            ),
        )
        val responseBundle = Bundle(entry = entries, type = Code(BundleType.TRANSACTION_RESPONSE.code))
        val searchToken = "system|value"
        val actual =
            runBlocking {
                localStorageClient.searchForResources("Practitioner", "test", searchToken)
            }
        assertEquals(responseBundle, actual)
        assertEquals("Bundle", actual.resourceType)
        assertEquals(1, actual.entry.size)

        val resource = actual.entry.first().resource
        val practitioner = resource as Practitioner
        assertEquals(3, practitioner.identifier.size)
        assertEquals(1, practitioner.identifier.filter { it.system?.value == "system" }.size)
        assertEquals(1, practitioner.identifier.filter { it.value?.value == "value" }.size)
    }

    @Test
    fun `search works when nothing is found localStorageClient`() {
        val responseBundle = Bundle(entry = emptyList(), type = Code(BundleType.TRANSACTION_RESPONSE.code))
        val searchToken = "not|there"
        val actual =
            runBlocking {
                localStorageClient.searchForResources("Practitioner", "test", searchToken)
            }
        assertEquals(responseBundle, actual)
        assertEquals("Bundle", actual.resourceType)
        assertEquals(0, actual.entry.size)
    }

    @Test
    fun `search works when no Practitioner in localStorageClient`() {
        val responseBundle = Bundle(entry = emptyList(), type = Code(BundleType.TRANSACTION_RESPONSE.code))
        val searchToken = "not|there"
        val actual =
            runBlocking {
                localStorageClient.searchForResources("Patient", "test", searchToken)
            }
        assertEquals(responseBundle, actual)
        assertEquals("Bundle", actual.resourceType)
        assertEquals(0, actual.entry.size)
    }
}
