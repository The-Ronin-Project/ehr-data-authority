package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.BaseEHRDataAuthorityIT
import com.projectronin.ehr.dataauthority.models.ModificationType
import com.projectronin.ehr.dataauthority.testclients.AidboxClient
import com.projectronin.ehr.dataauthority.testclients.DBClient
import com.projectronin.ehr.dataauthority.testclients.KafkaClient
import com.projectronin.ehr.dataauthority.testclients.ValidationClient
import com.projectronin.fhir.r4.Patient
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.CodeableConcepts
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.util.asCode
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResourcesWriteControllerIT : BaseEHRDataAuthorityIT() {
    override val resources = mapOf("patient" to Patient::class)

    @Test
    fun `adds resource when new`() {
        val patient = roninPatient("tenant-12345", "tenant")

        val response = runBlocking { client.addResources("tenant", listOf(patient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-12345", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "tenant-12345")
        assertEquals("tenant-12345", aidboxP.id!!.value)
        AidboxClient.deleteResource("Patient", "tenant-12345")

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertEquals(patient.copy(meta = null).consistentHashCode(), hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(1, kafkaEvents.size)
        assertTrue(kafkaEvents[0].type.endsWith("patient.create"))

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)
    }

    @Test
    fun `updates resource when changed`() {
        val originalPatient = roninPatient("tenant-12345", "tenant") {
            gender of AdministrativeGender.FEMALE.asCode()
        }
        val addedPatient = AidboxClient.addResource(originalPatient)
        DBClient.setHashValue(
            "tenant",
            "Patient",
            "tenant-12345",
            originalPatient.copy(meta = null).consistentHashCode()
        )

        val updatedPatient = originalPatient.copy(gender = AdministrativeGender.MALE.asCode())

        val response = runBlocking { client.addResources("tenant", listOf(updatedPatient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-12345", success1.resourceId)
        assertEquals(ModificationType.UPDATED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "tenant-12345")
        assertEquals("tenant-12345", aidboxP.id!!.value)
        assertNotEquals(addedPatient.meta!!.lastUpdated!!, aidboxP.meta!!.lastUpdated!!)
        AidboxClient.deleteResource("Patient", "tenant-12345")

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertEquals(updatedPatient.copy(meta = null).consistentHashCode(), hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(1, kafkaEvents.size)
        assertTrue(kafkaEvents[0].type.endsWith("patient.update"))

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)
    }

    @Test
    fun `updates resource when same hash code, but changed`() {
        val originalPatient = roninPatient("tenant-12345", "tenant") {
            gender of AdministrativeGender.FEMALE.asCode()
        }
        val addedPatient = AidboxClient.addResource(originalPatient)

        val updatedPatient = originalPatient.copy(gender = AdministrativeGender.MALE.asCode())

        // Use the updated patient's hashCode.
        DBClient.setHashValue(
            "tenant",
            "Patient",
            "tenant-12345",
            updatedPatient.copy(meta = null).consistentHashCode()
        )

        val response = runBlocking { client.addResources("tenant", listOf(updatedPatient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-12345", success1.resourceId)
        assertEquals(ModificationType.UPDATED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "tenant-12345")
        assertEquals("tenant-12345", aidboxP.id!!.value)
        assertNotEquals(addedPatient.meta!!.lastUpdated!!, aidboxP.meta!!.lastUpdated!!)
        AidboxClient.deleteResource("Patient", "tenant-12345")

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertEquals(updatedPatient.copy(meta = null).consistentHashCode(), hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(1, kafkaEvents.size)
        assertTrue(kafkaEvents[0].type.endsWith("patient.update"))

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)
    }

    @Test
    fun `returns success when resource is unchanged`() {
        val originalPatient = roninPatient("tenant-12345", "tenant") {
            gender of AdministrativeGender.FEMALE.asCode()
        }
        val addedPatient = AidboxClient.addResource(originalPatient)
        DBClient.setHashValue(
            "tenant",
            "Patient",
            "tenant-12345",
            originalPatient.copy(meta = null).consistentHashCode()
        )

        val response = runBlocking { client.addResources("tenant", listOf(originalPatient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-12345", success1.resourceId)
        assertEquals(ModificationType.UNMODIFIED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "tenant-12345")
        assertEquals("tenant-12345", aidboxP.id!!.value)
        assertEquals(addedPatient.meta!!.lastUpdated!!, aidboxP.meta!!.lastUpdated!!)
        AidboxClient.deleteResource("Patient", "tenant-12345")

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertEquals(originalPatient.copy(meta = null).consistentHashCode(), hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(0, kafkaEvents.size)

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)
    }

    @Test
    fun `multi resource - pass`() {
        val patient1 = roninPatient("tenant-12345", "tenant")
        val patient2 = roninPatient("tenant-67890", "tenant")

        val response = runBlocking { client.addResources("tenant", listOf(patient1, patient2)) }
        assertEquals(2, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-12345", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val success2 = response.succeeded[1]
        assertEquals("Patient", success2.resourceType)
        assertEquals("tenant-67890", success2.resourceId)
        assertEquals(ModificationType.CREATED, success2.modificationType)

        val aidboxP1 = AidboxClient.getResource("Patient", "tenant-12345")
        val aidboxP2 = AidboxClient.getResource("Patient", "tenant-67890")
        assertEquals("tenant-12345", aidboxP1.id!!.value)
        assertEquals("tenant-67890", aidboxP2.id!!.value)
        AidboxClient.deleteResource("Patient", "tenant-12345")
        AidboxClient.deleteResource("Patient", "tenant-67890")

        val hashP1 = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertEquals(patient1.copy(meta = null).consistentHashCode(), hashP1)
        val hashP2 = DBClient.getStoredHashValue("tenant", "Patient", "tenant-67890")
        assertEquals(patient2.copy(meta = null).consistentHashCode(), hashP2)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(2, kafkaEvents.size)
        assertTrue(kafkaEvents[0].type.endsWith("patient.create"))
        assertTrue(kafkaEvents[1].type.endsWith("patient.create"))

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)
    }

    @Test
    fun `invalid resource returns failure and adds to validation service`() {
        val patient = patient {
            id of Id("tenant-12345")
            identifier plus identifier {
                system of CodeSystem.RONIN_TENANT.uri
                value of "tenant"
                type of CodeableConcepts.RONIN_TENANT
            }
        }

        val response = runBlocking { client.addResources("tenant", listOf(patient)) }
        assertEquals(0, response.succeeded.size)
        assertEquals(1, response.failed.size)

        val failure1 = response.failed[0]
        assertEquals("Patient", failure1.resourceType)
        assertEquals("tenant-12345", failure1.resourceId)
        assertNotNull(failure1.error)

        val aidboxP = runCatching { AidboxClient.getResource("Patient", "tenant-12345") }.getOrNull()
        assertNull(aidboxP)

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertNull(hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(0, kafkaEvents.size)

        val validationResources = ValidationClient.getResources()
        assertEquals(1, validationResources.size)
        assertEquals("tenant", validationResources[0].organizationId)
        assertEquals("Patient", validationResources[0].resourceType)
    }

    @Test
    fun `tenant mismatch just fails`() {
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
            assertThrows<ClientFailureException> { runBlocking { client.addResources("notTenant", listOf(patient)) } }
        assertTrue(response.message!!.startsWith("Received 400"))

        val aidboxP = runCatching { AidboxClient.getResource("Patient", "tenant-12345") }.getOrNull()
        assertNull(aidboxP)

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertNull(hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(0, kafkaEvents.size)

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)
    }

    @Test
    fun `repeat requests result in unmodified`() {
        // First we do a full new request.
        val patient = roninPatient("tenant-12345", "tenant")

        val response = runBlocking { client.addResources("tenant", listOf(patient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-12345", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "tenant-12345")
        assertEquals("tenant-12345", aidboxP.id!!.value)

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertEquals(patient.copy(meta = null).consistentHashCode(), hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(1, kafkaEvents.size)
        assertTrue(kafkaEvents[0].type.endsWith("patient.create"))

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)

        // Now we do it all again with the same request.

        val response2 = runBlocking { client.addResources("tenant", listOf(patient)) }
        assertEquals(1, response2.succeeded.size)
        assertEquals(0, response2.failed.size)

        val success2 = response2.succeeded[0]
        assertEquals("Patient", success2.resourceType)
        assertEquals("tenant-12345", success2.resourceId)
        assertEquals(ModificationType.UNMODIFIED, success2.modificationType)

        val kafkaEvents2 = KafkaClient.readEvents("patient")
        assertEquals(0, kafkaEvents2.size)

        val validationResources2 = ValidationClient.getResources()
        assertEquals(0, validationResources2.size)

        AidboxClient.deleteResource("Patient", "tenant-12345")
    }

    @Test
    fun `same resource with different source is considered unmodified`() {
        val patient = roninPatient("tenant-12345", "tenant")

        val response = runBlocking { client.addResources("tenant", listOf(patient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-12345", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "tenant-12345")
        assertEquals("tenant-12345", aidboxP.id!!.value)

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertEquals(patient.copy(meta = null).consistentHashCode(), hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(1, kafkaEvents.size)
        assertTrue(kafkaEvents[0].type.endsWith("patient.create"))

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)

        val patient2 = patient.copy(meta = patient.meta?.copy(source = Uri("some-other-source")))

        val response2 = runBlocking { client.addResources("tenant", listOf(patient2)) }
        assertEquals(1, response2.succeeded.size)
        assertEquals(0, response2.failed.size)

        val success2 = response2.succeeded[0]
        assertEquals("Patient", success2.resourceType)
        assertEquals("tenant-12345", success2.resourceId)
        assertEquals(ModificationType.UNMODIFIED, success2.modificationType)

        AidboxClient.deleteResource("Patient", "tenant-12345")
    }
}
