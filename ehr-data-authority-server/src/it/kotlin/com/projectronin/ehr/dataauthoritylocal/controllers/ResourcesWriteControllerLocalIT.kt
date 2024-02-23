package com.projectronin.ehr.dataauthoritylocal.controllers

import com.projectronin.ehr.dataauthority.models.ModificationType
import com.projectronin.ehr.dataauthoritylocal.BaseEHRDataAuthorityLocalIT
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.CodeableConcepts
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.ronin.generators.resource.rcdmPatient
import com.projectronin.interop.fhir.util.asCode
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResourcesWriteControllerLocalIT : BaseEHRDataAuthorityLocalIT() {
    // /////////////////////  LOCAL STORAGE CLIENT TESTS  ///////////////////////
    @Test
    fun `adds new resource - local storage client`() {
        val patient =
            patient {
                id of Id(value = "ehrda-12345")
                identifier of
                    listOf(
                        identifier {
                            system of CodeSystem.RONIN_MRN.uri.value!!
                            value of "value"
                        },
                        identifier {
                            system of CodeSystem.RONIN_TENANT.uri.value!!
                            value of "ehrda"
                        },
                    )
            }

        val response = runBlocking { client.addResources("ehrda", listOf(patient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("ehrda-12345", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)
        runBlocking { client.deleteResource("ehrda", "Patient", "ehrda-12345") }
    }

    @Test
    fun `updates resource when changed - local storage client`() {
        val originalPatient =
            rcdmPatient("ehrda") {
                id of Id("ehrda-12345")
                gender of AdministrativeGender.FEMALE.asCode()
            }
        val addedResource = runBlocking { client.addResources("ehrda", listOf(originalPatient)) }
        assertEquals(1, addedResource.succeeded.size)
        assertEquals(0, addedResource.failed.size)

        val resourceSuccess = addedResource.succeeded[0]
        assertEquals("Patient", resourceSuccess.resourceType)
        assertEquals("ehrda-12345", resourceSuccess.resourceId)
        assertEquals(ModificationType.CREATED, resourceSuccess.modificationType)

        val updatedPatient = originalPatient.copy(gender = AdministrativeGender.MALE.asCode())

        val response = runBlocking { client.addResources("ehrda", listOf(updatedPatient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("ehrda-12345", success1.resourceId)
        assertEquals(ModificationType.UPDATED, success1.modificationType)
        runBlocking { client.deleteResource("ehrda", "Patient", "ehrda-12345") }
    }

    @Test
    fun `updates resource when same hash code, but changed - local storage client`() {
        val originalPatient =
            rcdmPatient("ehrda") {
                id of Id("ehrda-12345")
                gender of AdministrativeGender.FEMALE.asCode()
            }
        val addedPatient = runBlocking { client.addResources("ehrda", listOf(originalPatient)) }
        assertEquals(1, addedPatient.succeeded.size)
        assertEquals(0, addedPatient.failed.size)

        val success = addedPatient.succeeded[0]
        assertEquals("Patient", success.resourceType)
        assertEquals("ehrda-12345", success.resourceId)
        assertEquals(ModificationType.CREATED, success.modificationType)

        val updatedPatient = originalPatient.copy(gender = AdministrativeGender.MALE.asCode())

        val response = runBlocking { client.addResources("ehrda", listOf(updatedPatient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("ehrda-12345", success1.resourceId)
        assertEquals(ModificationType.UPDATED, success1.modificationType)
        runBlocking { client.deleteResource("ehrda", "Patient", "ehrda-12345") }
    }

    @Test
    fun `returns success when resource is unchanged - local storage client`() {
        val originalPatient =
            rcdmPatient("ehrda") {
                id of Id("ehrda-12345")
                gender of AdministrativeGender.FEMALE.asCode()
            }
        val addedResource = runBlocking { client.addResources("ehrda", listOf(originalPatient)) }
        assertEquals(1, addedResource.succeeded.size)
        assertEquals(0, addedResource.failed.size)
        val success = addedResource.succeeded[0]
        assertEquals("Patient", success.resourceType)
        assertEquals("ehrda-12345", success.resourceId)
        assertEquals(ModificationType.CREATED, success.modificationType)

        val response = runBlocking { client.addResources("ehrda", listOf(originalPatient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("ehrda-12345", success1.resourceId)
        assertEquals(ModificationType.UNMODIFIED, success1.modificationType)
        runBlocking { client.deleteResource("ehrda", "Patient", "ehrda-12345") }
    }

    @Test
    fun `multi resource - pass - local storage client`() {
        val patient1 =
            rcdmPatient("ehrda") {
                id of Id("ehrda-12345")
            }
        val patient2 =
            rcdmPatient("ehrda") {
                id of Id("ehrda-67890")
            }

        val response = runBlocking { client.addResources("ehrda", listOf(patient1, patient2)) }
        assertEquals(2, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("ehrda-12345", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val success2 = response.succeeded[1]
        assertEquals("Patient", success2.resourceType)
        assertEquals("ehrda-67890", success2.resourceId)
        assertEquals(ModificationType.CREATED, success2.modificationType)
        runBlocking { client.deleteResource("ehrda", "Patient", "ehrda-12345") }
        runBlocking { client.deleteResource("ehrda", "Patient", "ehrda-67890") }
    }

    @Test
    fun `tenant mismatch just fails - local storage client`() {
        val patient =
            patient {
                id of Id("ehrda-12345")
                identifier plus
                    identifier {
                        system of CodeSystem.RONIN_TENANT.uri
                        value of "ehrda"
                        type of CodeableConcepts.RONIN_TENANT
                    }
            }

        HttpStatusCode.BadRequest
        val response =
            assertThrows<ClientFailureException> { runBlocking { client.addResources("notTenant", listOf(patient)) } }
        assertTrue(response.message!!.startsWith("Received 400"))

        val localP = runBlocking { client.getResource("ehrda", "Patient", "ehrda-12345") }
        assertNull(localP)
    }

    @Test
    fun `repeat requests result in unmodified - local storage client`() {
        // first - new request.
        val patient =
            rcdmPatient("ehrda") {
                id of Id("ehrda-12345")
            }

        val response = runBlocking { client.addResources("ehrda", listOf(patient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("ehrda-12345", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val localP = runBlocking { client.getResource("ehrda", "Patient", "ehrda-12345") }
        assertEquals("ehrda-12345", localP?.id!!.value)

        // same request again.

        val response2 = runBlocking { client.addResources("ehrda", listOf(patient)) }
        assertEquals(1, response2.succeeded.size)
        assertEquals(0, response2.failed.size)

        val success2 = response2.succeeded[0]
        assertEquals("Patient", success2.resourceType)
        assertEquals("ehrda-12345", success2.resourceId)
        assertEquals(ModificationType.UNMODIFIED, success2.modificationType)
        runBlocking { client.deleteResource("ehrda", "Patient", "ehrda-12345") }
    }

    @Test
    fun `same resource with different source is considered unmodified - local storage client`() {
        val patient =
            rcdmPatient("ehrda") {
                id of Id("ehrda-12345")
            }

        val response = runBlocking { client.addResources("ehrda", listOf(patient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("ehrda-12345", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val localPatient = runBlocking { client.getResource("ehrda", "Patient", "ehrda-12345") }
        assertEquals("ehrda-12345", localPatient?.id!!.value)

        val patient2 = patient.copy(meta = patient.meta?.copy(source = Uri("some-other-source")))

        val response2 = runBlocking { client.addResources("ehrda", listOf(patient2)) }
        assertEquals(1, response2.succeeded.size)
        assertEquals(0, response2.failed.size)

        val success2 = response2.succeeded[0]
        assertEquals("Patient", success2.resourceType)
        assertEquals("ehrda-12345", success2.resourceId)
        assertEquals(ModificationType.UNMODIFIED, success2.modificationType)
        runBlocking { client.deleteResource("ehrda", "Patient", "ehrda-12345") }
    }
}
