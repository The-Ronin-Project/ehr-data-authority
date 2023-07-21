package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.BaseEHRDataAuthorityIT
import com.projectronin.ehr.dataauthority.models.ChangeType
import com.projectronin.ehr.dataauthority.testclients.AidboxClient
import com.projectronin.ehr.dataauthority.testclients.DBClient
import com.projectronin.fhir.r4.Patient
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.CodeableConcepts
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.util.asCode
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResourcesChangeControllerIT : BaseEHRDataAuthorityIT() {
    override val resources = mapOf("patient" to Patient::class)

    @Test
    fun `returns new when resource is new`() {
        val patient = roninPatient("tenant-9876", "tenant")

        val response = runBlocking { client.getResourcesChangeStatus("tenant", listOf(patient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success = response.succeeded[0]
        assertEquals("Patient", success.resourceType)
        assertEquals("tenant-9876", success.resourceId)
        assertEquals(ChangeType.NEW, success.changeType)
    }

    @Test
    fun `returns unchanged when resource is unchanged`() {
        val patient = roninPatient("tenant-9984", "tenant") {
            gender of AdministrativeGender.FEMALE.asCode()
        }
        AidboxClient.addResource(patient)
        DBClient.setHashValue(
            "tenant",
            "Patient",
            "tenant-9984",
            patient.copy(meta = null).consistentHashCode()
        )

        val response = runBlocking { client.getResourcesChangeStatus("tenant", listOf(patient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success = response.succeeded[0]
        assertEquals("Patient", success.resourceType)
        assertEquals("tenant-9984", success.resourceId)
        assertEquals(ChangeType.UNCHANGED, success.changeType)

        AidboxClient.deleteResource("Patient", "tenant-9984")
    }

    @Test
    fun `returns changed when resource is changed`() {
        val originalPatient = roninPatient("tenant-9784", "tenant") {
            gender of AdministrativeGender.FEMALE.asCode()
        }
        AidboxClient.addResource(originalPatient)
        DBClient.setHashValue(
            "tenant",
            "Patient",
            "tenant-9784",
            originalPatient.copy(meta = null).consistentHashCode()
        )

        val updatedPatient = originalPatient.copy(gender = AdministrativeGender.MALE.asCode())

        val response = runBlocking { client.getResourcesChangeStatus("tenant", listOf(updatedPatient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success = response.succeeded[0]
        assertEquals("Patient", success.resourceType)
        assertEquals("tenant-9784", success.resourceId)
        assertEquals(ChangeType.CHANGED, success.changeType)

        AidboxClient.deleteResource("Patient", "tenant-9784")
    }

    @Test
    fun `multi-resource pass`() {
        val patient1 = roninPatient("tenant-77889", "tenant")
        val patient2 = roninPatient("tenant-44990", "tenant")

        val response = runBlocking { client.getResourcesChangeStatus("tenant", listOf(patient1, patient2)) }
        assertEquals(2, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-77889", success1.resourceId)
        assertEquals(ChangeType.NEW, success1.changeType)

        val success2 = response.succeeded[1]
        assertEquals("Patient", success2.resourceType)
        assertEquals("tenant-44990", success2.resourceId)
        assertEquals(ChangeType.NEW, success2.changeType)
    }

    @Test
    fun `tenant mismatch fails`() {
        val patient = patient {
            id of Id("tenant-12345")
            identifier plus identifier {
                system of CodeSystem.RONIN_TENANT.uri
                value of "tenant"
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
        val patient = roninPatient("tenant-12345", "tenant")

        val response = runBlocking { client.getResourcesChangeStatus("tenant", listOf(patient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-12345", success1.resourceId)
        assertEquals(ChangeType.NEW, success1.changeType)

        // Now we do it all again with the same request.

        val response2 = runBlocking { client.getResourcesChangeStatus("tenant", listOf(patient)) }
        assertEquals(1, response2.succeeded.size)
        assertEquals(0, response2.failed.size)

        val success2 = response2.succeeded[0]
        assertEquals("Patient", success2.resourceType)
        assertEquals("tenant-12345", success2.resourceId)
        assertEquals(ChangeType.NEW, success2.changeType)
    }
}
