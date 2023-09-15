package com.projectronin.ehr.dataauthoritylocal.controllers

import com.projectronin.ehr.dataauthority.models.ChangeType
import com.projectronin.ehr.dataauthority.models.ModificationType
import com.projectronin.ehr.dataauthoritylocal.BaseEHRDataAuthorityLocalIT
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.CodeableConcepts
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.ronin.generators.resource.rcdmPatient
import com.projectronin.interop.fhir.util.asCode
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResourcesChangeControllerLocalIT : BaseEHRDataAuthorityLocalIT() {
    // /////////////////////  LOCAL STORAGE CLIENT TESTS  ///////////////////////
    @Test
    fun `returns new resource when added`() {
        val patient = rcdmPatient("ehrda") {
            id of Id("ehrda-9876")
        }

        val response = runBlocking { client.getResourcesChangeStatus("ehrda", listOf(patient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success = response.succeeded[0]
        assertEquals("Patient", success.resourceType)
        assertEquals("ehrda-9876", success.resourceId)
        assertEquals(ChangeType.NEW, success.changeType)
    }

    @Test
    fun `returns unchanged when resource is unchanged`() {
        val patient = rcdmPatient("ehrda") {
            id of Id("ehrda-9984")
            gender of AdministrativeGender.FEMALE.asCode()
        }
        runBlocking { client.addResources("ehrda", listOf(patient)) }

        val response = runBlocking { client.getResourcesChangeStatus("ehrda", listOf(patient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success = response.succeeded[0]
        assertEquals("Patient", success.resourceType)
        assertEquals("ehrda-9984", success.resourceId)
        assertEquals(ChangeType.UNCHANGED, success.changeType)
    }

    @Test
    fun `returns changed when resource is changed`() {
        val originalPatient = rcdmPatient("ehrda") {
            id of Id("ehrda-9784")
            gender of AdministrativeGender.FEMALE.asCode()
        }
        val addingOne = runBlocking { client.addResources("ehrda", listOf(originalPatient)) }
        assertEquals(1, addingOne.succeeded.size)
        assertEquals(0, addingOne.failed.size)

        val successAdd = addingOne.succeeded[0]
        assertEquals("Patient", successAdd.resourceType)
        assertEquals("ehrda-9784", successAdd.resourceId)
        assertEquals(successAdd.modificationType, ModificationType.CREATED)

        val updatedPatient = originalPatient.copy(gender = AdministrativeGender.MALE.asCode())

        val response = runBlocking { client.getResourcesChangeStatus("ehrda", listOf(updatedPatient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success = response.succeeded[0]
        assertEquals("Patient", success.resourceType)
        assertEquals("ehrda-9784", success.resourceId)
        assertEquals(ChangeType.CHANGED, success.changeType)
    }

    @Test
    fun `multi-resource pass`() {
        val patient1 = rcdmPatient("ehrda") {
            id of Id("ehrda-77889")
        }
        val patient2 = rcdmPatient("ehrda") {
            id of Id("ehrda-44990")
        }

        val response = runBlocking { client.getResourcesChangeStatus("ehrda", listOf(patient1, patient2)) }
        assertEquals(2, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("ehrda-77889", success1.resourceId)
        assertEquals(ChangeType.NEW, success1.changeType)

        val success2 = response.succeeded[1]
        assertEquals("Patient", success2.resourceType)
        assertEquals("ehrda-44990", success2.resourceId)
        assertEquals(ChangeType.NEW, success2.changeType)
    }

    @Test
    fun `tenant mismatch fails`() {
        val patient = patient {
            id of Id("ehrda-12345")
            identifier plus identifier {
                system of CodeSystem.RONIN_TENANT.uri
                value of "ehrda"
                type of CodeableConcepts.RONIN_TENANT
            }
        }

        HttpStatusCode.BadRequest
        val response =
            assertThrows<ClientFailureException> { runBlocking { client.getResourcesChangeStatus("notTenant", listOf(patient)) } }
        Assertions.assertTrue(response.message!!.startsWith("Received 400"))
    }

    @Test
    fun `repeat requests result in new -- verifying change controller is not adding resource`() {
        // First we do a full new request.
        val patient = rcdmPatient("ehrda") {
            id of Id("ehrda-12345")
        }

        val response = runBlocking { client.getResourcesChangeStatus("ehrda", listOf(patient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("ehrda-12345", success1.resourceId)
        assertEquals(ChangeType.NEW, success1.changeType)

        // Now we do it all again with the same request.

        val response2 = runBlocking { client.getResourcesChangeStatus("ehrda", listOf(patient)) }
        assertEquals(1, response2.succeeded.size)
        assertEquals(0, response2.failed.size)

        val success2 = response2.succeeded[0]
        assertEquals("Patient", success2.resourceType)
        assertEquals("ehrda-12345", success2.resourceId)
        assertEquals(ChangeType.NEW, success2.changeType)
    }
}
