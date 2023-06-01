package com.projectronin.ehr.dataauthority.aidbox.auth

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.projectronin.interop.common.auth.Authentication
import java.time.Instant

/**
 * Aidbox implementation of [Authentication]
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AidboxAuthentication(
    override val tokenType: String,
    override val accessToken: String,
    private val expiresIn: Long? = null,
    override val refreshToken: String? = null,
    override val scope: String? = null
) : Authentication {
    override val expiresAt: Instant? = expiresIn?.let { Instant.now().plusSeconds(expiresIn) }

    // Override toString() to prevent accidentally leaking the accessToken
    override fun toString(): String = this::class.simpleName!!
}
