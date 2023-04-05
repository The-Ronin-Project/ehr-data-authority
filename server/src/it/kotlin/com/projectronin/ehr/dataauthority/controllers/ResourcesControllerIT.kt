package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.BaseEHRDataAuthorityIT
import com.projectronin.ehr.dataauthority.testclients.AidboxClient
import com.projectronin.ehr.dataauthority.testclients.EHRDAClient
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResourcesControllerIT : BaseEHRDataAuthorityIT() {

    @Test
    fun `single resource - pass`() {
        val patient = patient {
            id of Id(value = "12345")
        }
        val response = EHRDAClient.addResource(patient)
        assertEquals("12345", response.succeeded.first().resourceId)
        val aidboxP = AidboxClient.getResource("Patient", "12345")
        assertEquals("12345", aidboxP.id!!.value)
        AidboxClient.deleteResource("Patient", "12345")
    }

    @Test
    fun `multi resource - pass`() {
        val patient1 = patient {
            id of Id(value = "12345")
        }
        val patient2 = patient {
            id of Id(value = "67890")
        }
        val response1 = EHRDAClient.addResource(patient1)
        val response2 = EHRDAClient.addResource(patient2)
        assertEquals("12345", response1.succeeded.first().resourceId)
        assertEquals("67890", response2.succeeded.first().resourceId)
        val aidboxP1 = AidboxClient.getResource("Patient", "12345")
        val aidboxP2 = AidboxClient.getResource("Patient", "67890")
        assertEquals("12345", aidboxP1.id!!.value)
        assertEquals("67890", aidboxP2.id!!.value)
        AidboxClient.deleteResource("Patient", "12345")
        AidboxClient.deleteResource("Patient", "67890")
    }
}
