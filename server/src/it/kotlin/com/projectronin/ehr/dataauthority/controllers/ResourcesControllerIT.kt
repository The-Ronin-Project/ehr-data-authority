package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.BaseEHRDataAuthorityIT
import com.projectronin.ehr.dataauthority.model.ModificationType
import com.projectronin.ehr.dataauthority.testclients.AidboxClient
import com.projectronin.ehr.dataauthority.testclients.DBClient
import com.projectronin.ehr.dataauthority.testclients.EHRDAClient
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.util.asCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ResourcesControllerIT : BaseEHRDataAuthorityIT() {
    @Test
    fun `adds resource when new`() {
        val patient = patient {
            id of Id(value = "12345")
        }
        val response = EHRDAClient.addResources("tenant", listOf(patient))
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("12345", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "12345")
        assertEquals("12345", aidboxP.id!!.value)
        AidboxClient.deleteResource("Patient", "12345")

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "12345")
        assertEquals(patient.hashCode(), hashP)
        DBClient.purgeHashes()
    }

    @Test
    fun `updates resource when changed`() {
        val originalPatient = patient {
            id of Id(value = "12345")
            gender of AdministrativeGender.FEMALE.asCode()
        }
        val addedPatient = AidboxClient.addResource(originalPatient)
        DBClient.setHashValue("tenant", "Patient", "12345", originalPatient.hashCode())

        val updatedPatient = originalPatient.copy(gender = AdministrativeGender.MALE.asCode())

        val response = EHRDAClient.addResources("tenant", listOf(updatedPatient))
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("12345", success1.resourceId)
        assertEquals(ModificationType.UPDATED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "12345")
        assertEquals("12345", aidboxP.id!!.value)
        assertNotEquals(addedPatient.meta!!.lastUpdated!!, aidboxP.meta!!.lastUpdated!!)
        AidboxClient.deleteResource("Patient", "12345")

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "12345")
        assertEquals(updatedPatient.hashCode(), hashP)
        DBClient.purgeHashes()
    }

    @Test
    fun `updates resource when same hash code, but changed`() {
        val originalPatient = patient {
            id of Id(value = "12345")
            gender of AdministrativeGender.FEMALE.asCode()
        }
        val addedPatient = AidboxClient.addResource(originalPatient)

        val updatedPatient = originalPatient.copy(gender = AdministrativeGender.MALE.asCode())

        // Use the updated patient's hashCode.
        DBClient.setHashValue("tenant", "Patient", "12345", updatedPatient.hashCode())

        val response = EHRDAClient.addResources("tenant", listOf(updatedPatient))
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("12345", success1.resourceId)
        assertEquals(ModificationType.UPDATED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "12345")
        assertEquals("12345", aidboxP.id!!.value)
        assertNotEquals(addedPatient.meta!!.lastUpdated!!, aidboxP.meta!!.lastUpdated!!)
        AidboxClient.deleteResource("Patient", "12345")

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "12345")
        assertEquals(updatedPatient.hashCode(), hashP)
        DBClient.purgeHashes()
    }

    @Test
    fun `returns success when resource is unchanged`() {
        val originalPatient = patient {
            id of Id(value = "12345")
            gender of AdministrativeGender.FEMALE.asCode()
        }
        val addedPatient = AidboxClient.addResource(originalPatient)
        DBClient.setHashValue("tenant", "Patient", "12345", originalPatient.hashCode())

        val response = EHRDAClient.addResources("tenant", listOf(originalPatient))
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("12345", success1.resourceId)
        assertEquals(ModificationType.UNMODIFIED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "12345")
        assertEquals("12345", aidboxP.id!!.value)
        assertEquals(addedPatient.meta!!.lastUpdated!!, aidboxP.meta!!.lastUpdated!!)
        AidboxClient.deleteResource("Patient", "12345")

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "12345")
        assertEquals(originalPatient.hashCode(), hashP)
        DBClient.purgeHashes()
    }

    @Test
    fun `multi resource - pass`() {
        val patient1 = patient {
            id of Id(value = "12345")
        }
        val patient2 = patient {
            id of Id(value = "67890")
        }
        val response = EHRDAClient.addResources("tenant", listOf(patient1, patient2))
        assertEquals(2, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("12345", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val success2 = response.succeeded[1]
        assertEquals("Patient", success2.resourceType)
        assertEquals("67890", success2.resourceId)
        assertEquals(ModificationType.CREATED, success2.modificationType)

        val aidboxP1 = AidboxClient.getResource("Patient", "12345")
        val aidboxP2 = AidboxClient.getResource("Patient", "67890")
        assertEquals("12345", aidboxP1.id!!.value)
        assertEquals("67890", aidboxP2.id!!.value)
        AidboxClient.deleteResource("Patient", "12345")
        AidboxClient.deleteResource("Patient", "67890")

        val hashP1 = DBClient.getStoredHashValue("tenant", "Patient", "12345")
        assertEquals(patient1.hashCode(), hashP1)
        val hashP2 = DBClient.getStoredHashValue("tenant", "Patient", "67890")
        assertEquals(patient2.hashCode(), hashP2)
        DBClient.purgeHashes()
    }
}
