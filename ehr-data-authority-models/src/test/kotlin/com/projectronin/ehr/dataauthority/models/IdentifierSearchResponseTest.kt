package com.projectronin.ehr.dataauthority.models

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IdentifierSearchResponseTest {
    @Test
    fun `can serialize and deserialize`() {
        val response = IdentifierSearchResponse(
            searchedIdentifier = Identifier(system = "system", value = "value"),
            foundResources = listOf(
                FoundResourceIdentifiers(
                    udpId = "tenant-12345",
                    identifiers = listOf(
                        Identifier(system = "system", value = "value"),
                        Identifier(system = "system2", value = "value2")
                    )
                )
            )
        )
        val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response)

        val expectedJson = """
            {
              "searchedIdentifier" : {
                "system" : "system",
                "value" : "value"
              },
              "foundResources" : [ {
                "udpId" : "tenant-12345",
                "identifiers" : [ {
                  "system" : "system",
                  "value" : "value"
                }, {
                  "system" : "system2",
                  "value" : "value2"
                } ]
              } ]
            }
        """.trimIndent()
        assertEquals(expectedJson, json)

        val deserialized = objectMapper.readValue<IdentifierSearchResponse>(json)
        assertEquals(response, deserialized)
    }
}
