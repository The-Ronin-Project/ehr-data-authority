package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.BaseEHRDataAuthorityIT
import com.projectronin.ehr.dataauthority.testclients.AidboxClient
import com.projectronin.ehr.dataauthority.testclients.DBClient
import com.projectronin.ehr.dataauthority.testclients.KafkaClient
import com.projectronin.ehr.dataauthority.testclients.ValidationClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ResourcesDeleteControllerIT : BaseEHRDataAuthorityIT() {
    @Test
    fun `delete works`() {
        val patient = roninPatient("ronintst-12345", "ronintst") { }
        AidboxClient.addResource(patient)
        DBClient.setHashValue("ronintst", "Patient", "ronintst-12345", patient.consistentHashCode())

        runBlocking { client.deleteResource("ronintst", "Patient", "ronintst-12345") }

        val aidboxP = runCatching { AidboxClient.getResource("Patient", "ronintst-12345") }.getOrNull()
        assertNull(aidboxP)

        val hashP = DBClient.getStoredHashValue("ronintst", "Patient", "ronintst-12345")
        assertNull(hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(0, kafkaEvents.size)

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)
    }
}
