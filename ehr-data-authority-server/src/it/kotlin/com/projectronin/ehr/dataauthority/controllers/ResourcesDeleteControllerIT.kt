package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.BaseEHRDataAuthorityIT
import com.projectronin.ehr.dataauthority.testclients.AidboxClient
import com.projectronin.ehr.dataauthority.testclients.DBClient
import com.projectronin.ehr.dataauthority.testclients.KafkaClient
import com.projectronin.ehr.dataauthority.testclients.ValidationClient
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.ronin.generators.resource.rcdmPatient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ResourcesDeleteControllerIT : BaseEHRDataAuthorityIT() {
    @Test
    fun `delete works`() {
        Thread.sleep(20000) // need to wait for db and tables to be ready or it yells at us.
        val patient = rcdmPatient("ehrda") {
            id of Id("ehrda-12345")
        }
        AidboxClient.addResource(patient)
        DBClient.setHashValue("ehrda", "Patient", "ehrda-12345", patient.consistentHashCode())
        runBlocking { client.deleteResource("ehrda", "Patient", "ehrda-12345") }

        val aidboxP = runCatching { AidboxClient.getResource("Patient", "ehrda-12345") }.getOrNull()
        assertNull(aidboxP)

        val hashP = DBClient.getStoredHashValue("ehrda", "Patient", "ehrda-12345")
        assertNull(hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(0, kafkaEvents.size)

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)
    }
}
