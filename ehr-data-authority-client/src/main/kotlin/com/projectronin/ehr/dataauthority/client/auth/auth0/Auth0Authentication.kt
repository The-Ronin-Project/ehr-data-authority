package com.projectronin.ehr.dataauthority.client.auth.auth0

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.projectronin.interop.common.auth.Authentication
import java.time.Instant

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class Auth0Authentication(
    override val accessToken: String,
    override val tokenType: String,
    override val scope: String? = null,
    private val expiresIn: Long? = null,
    override val refreshToken: String? = null
) : Authentication {
    override val expiresAt: Instant? = expiresIn?.let { Instant.now().plusSeconds(expiresIn) }

    // Override toString() to prevent accidentally leaking the accessToken
    override fun toString(): String = this::class.simpleName!!
}
