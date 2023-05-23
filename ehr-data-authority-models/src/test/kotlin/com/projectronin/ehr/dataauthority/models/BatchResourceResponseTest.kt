package com.projectronin.ehr.dataauthority.models

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BatchResourceResponseTest {
    @Test
    fun `can serialize and deserialize`() {
        val response = BatchResourceResponse(
            succeeded = listOf(
                SucceededResource(
                    resourceType = "Patient",
                    resourceId = "tenant-1234",
                    modificationType = ModificationType.CREATED
                )
            ),
            failed = listOf(
                FailedResource(
                    resourceType = "Practitioner",
                    resourceId = "tenant-5678",
                    error = "Error encountered"
                )
            )
        )
        val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response)

        val expectedJson = """
            {
              "succeeded" : [ {
                "resourceType" : "Patient",
                "resourceId" : "tenant-1234",
                "modificationType" : "CREATED"
              } ],
              "failed" : [ {
                "resourceType" : "Practitioner",
                "resourceId" : "tenant-5678",
                "error" : "Error encountered"
              } ]
            }
        """.trimIndent()
        assertEquals(expectedJson, json)

        val deserialized = objectMapper.readValue<BatchResourceResponse>(json)
        assertEquals(response, deserialized)
    }
}
