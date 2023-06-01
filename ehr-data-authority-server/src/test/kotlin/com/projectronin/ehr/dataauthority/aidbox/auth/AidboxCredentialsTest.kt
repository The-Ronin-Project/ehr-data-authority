package com.projectronin.ehr.dataauthority.aidbox.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AidboxCredentialsTest {
    @Test
    fun `ensures toString is overwritten`() {
        val credentials = AidboxCredentials(
            clientId = "id",
            clientSecret = "secret"
        )
        assertEquals("AidboxCredentials", credentials.toString())
    }
}
