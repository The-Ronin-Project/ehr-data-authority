package com.projectronin.ehr.dataauthoritylocal.controllers

import com.projectronin.ehr.dataauthoritylocal.BaseEHRDataAuthorityLocalIT
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.ronin.generators.resource.rcdmPatient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ResourcesDeleteControllerLocalIT : BaseEHRDataAuthorityLocalIT() {
    // /////////////////////  LOCAL STORAGE CLIENT TESTS  ///////////////////////
    @Test
    fun `delete works`() {
        val patient = rcdmPatient("ehrda") {
            id of Id("ehrda-12345")
        }
        val patient2 = rcdmPatient("ehrda") {
            id of Id("ehrda-54321")
        }

        runBlocking { client.addResources("ehrda", listOf(patient, patient2)) }
        val response = runBlocking { client.getResource("ehrda", "Patient", "ehrda-12345") }
        runBlocking { client.getResource("ehrda", "Patient", "ehrda-12345") }
        assertNotNull(response)

        runBlocking { client.deleteResource("ehrda", "Patient", "ehrda-12345") }

        val shouldNotExist = runBlocking { client.getResource("ehrda", "Patient", "ehrda-12345") }
        assertNull(shouldNotExist)
    }
}
