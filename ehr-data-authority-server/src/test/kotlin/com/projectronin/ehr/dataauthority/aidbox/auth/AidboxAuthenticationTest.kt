package com.projectronin.ehr.dataauthority.aidbox.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AidboxAuthenticationTest {
    @Test
    fun `ensures toString is overwritten`() {
        val authentication =
            AidboxAuthentication(
                tokenType = "type",
                accessToken = "token",
            )
        assertEquals("AidboxAuthentication", authentication.toString())
    }
}
