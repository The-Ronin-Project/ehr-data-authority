package com.projectronin.ehr.dataauthority.client.auth.auth0

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class Auth0AuthenticationTest {
    @Test
    fun `expiresAt is null when no expiresIn provided`() {
        val authentication = Auth0Authentication(
            tokenType = "type",
            accessToken = "token",
            expiresIn = null
        )
        assertNull(authentication.expiresAt)
    }

    @Test
    fun `expiresAt is set when expiresIn provided`() {
        val authentication = Auth0Authentication(
            tokenType = "type",
            accessToken = "token",
            expiresIn = 30
        )

        val expiresAt = authentication.expiresAt
        expiresAt!!

        // Give a little bit of gap in case something is running slow.
        val twoSecondsEarly = Instant.now().plusSeconds(28)
        assertEquals(true, expiresAt.isAfter(twoSecondsEarly) || expiresAt == twoSecondsEarly)
        val exactly = Instant.now().plusSeconds(30)
        assertEquals(true, expiresAt.isBefore(exactly) || expiresAt == exactly)
    }

    @Test
    fun `ensures toString is overwritten`() {
        val authentication = Auth0Authentication(
            tokenType = "type",
            accessToken = "token"
        )
        assertEquals("Auth0Authentication", authentication.toString())
    }
}
