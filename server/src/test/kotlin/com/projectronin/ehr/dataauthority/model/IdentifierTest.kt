package com.projectronin.ehr.dataauthority.model

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.AssertionError
import com.projectronin.interop.fhir.r4.datatype.Identifier as FhirIdentifier

class IdentifierTest {

    @Test
    fun `identifier can be made from token`() {
        val ident = Identifier.fromToken("sys|val")
        assertEquals("sys", ident.system)
        assertEquals("val", ident.value)
    }

    @Test
    fun `identifier fails when not a token`() {
        val error = assertThrows<AssertionError> { Identifier.fromToken("sys|val|garbage") }
        assertEquals("'token' must be a string with one '|'", error.message)
    }

    @Test
    fun `identifier can be made from fhir identifiers`() {
        val fhirIdent = mockk<FhirIdentifier> {
            every { system?.value } returns "sys"
            every { value?.value } returns "val"
        }
        val idents = Identifier.fromFhirIdentifiers(listOf(fhirIdent))
        val ident = idents.first()
        assertEquals(1, idents.size)
        assertEquals("sys", ident.system)
        assertEquals("val", ident.value)
    }

    @Test
    fun `identifier throws error from bad fhir identifier system`() {
        val fhirIdent = mockk<FhirIdentifier> {
            every { system?.value } returns null
            every { value?.value } returns "val"
        }
        assertThrows<NullPointerException> { Identifier.fromFhirIdentifiers(listOf(fhirIdent)) }
    }

    @Test
    fun `identifier throws error from bad fhir identifier value`() {
        val fhirIdent = mockk<FhirIdentifier> {
            every { system?.value } returns "sys"
            every { value?.value } returns null
        }
        assertThrows<NullPointerException> { Identifier.fromFhirIdentifiers(listOf(fhirIdent)) }
    }

    @Test
    fun `identifier toToken works`() {
        val ident = Identifier("sys", "val")
        assertEquals("sys|val", ident.toToken())
    }
}
