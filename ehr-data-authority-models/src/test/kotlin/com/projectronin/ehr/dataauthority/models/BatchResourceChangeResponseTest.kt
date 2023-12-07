package com.projectronin.ehr.dataauthority.models

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectronin.interop.common.jackson.JacksonManager
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class BatchResourceChangeResponseTest {
    @Test
    fun `can serialize and deserialize`() {
        val response =
            BatchResourceChangeResponse(
                succeeded =
                    listOf(
                        ChangeStatusResource(
                            resourceType = "Patient",
                            resourceId = "tenant-1234",
                            changeType = ChangeType.CHANGED,
                        ),
                    ),
                failed =
                    listOf(
                        FailedResource(
                            resourceType = "Practitioner",
                            resourceId = "tenant-5678",
                            error = "Error encountered",
                        ),
                    ),
            )
        val json = JacksonManager.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response)

        val expectedJson =
            """
            {
              "succeeded" : [ {
                "resourceType" : "Patient",
                "resourceId" : "tenant-1234",
                "changeType" : "CHANGED"
              } ],
              "failed" : [ {
                "resourceType" : "Practitioner",
                "resourceId" : "tenant-5678",
                "error" : "Error encountered"
              } ]
            }
            """.trimIndent()
        Assertions.assertEquals(expectedJson, json)

        val deserialized = JacksonManager.objectMapper.readValue<BatchResourceChangeResponse>(json)
        Assertions.assertEquals(response, deserialized)
    }

    @Test
    fun `empty constructor initialization returns empty lists`() {
        val response = BatchResourceChangeResponse()
        Assertions.assertEquals(emptyList<ChangeStatusResource>(), response.succeeded)
        Assertions.assertEquals(emptyList<FailedResource>(), response.failed)
    }

    @Test
    fun `adding failed only creates empty list for succeeded`() {
        val response =
            BatchResourceChangeResponse(
                failed =
                    listOf(
                        FailedResource(
                            resourceType = "Practitioner",
                            resourceId = "tenant-5678",
                            error = "Error encountered",
                        ),
                    ),
            )
        Assertions.assertEquals(emptyList<ChangeStatusResource>(), response.succeeded)
    }

    @Test
    fun `adding succeeded only creates empty list for failed`() {
        val response =
            BatchResourceChangeResponse(
                succeeded =
                    listOf(
                        ChangeStatusResource(
                            resourceType = "Patient",
                            resourceId = "tenant-1234",
                            changeType = ChangeType.CHANGED,
                        ),
                    ),
            )
        Assertions.assertEquals(emptyList<FailedResource>(), response.failed)
    }
}
