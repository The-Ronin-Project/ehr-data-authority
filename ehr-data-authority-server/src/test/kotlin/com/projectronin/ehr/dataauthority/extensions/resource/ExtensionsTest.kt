package com.projectronin.ehr.dataauthority.extensions.resource

import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExtensionsTest {
    @Test
    fun `toKey returns expected key`() {
        val patient = patient {
            id of Id("patId")
        }

        val actualKey = patient.toKey()
        assertEquals("Patient:patId", actualKey)
    }

    @Test
    fun `toKey has a null pointer exception when an id is not defined`() {
        val patient = patient {}

        assertThrows<NullPointerException> {
            patient.toKey()
        }
    }
}
