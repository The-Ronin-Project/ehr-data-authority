package com.projectronin.ehr.dataauthority.aidbox.auth

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Credentials for accessing Aidbox.
 */
@Component
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AidboxCredentials(
    @Value("\${aidbox.client.id}")
    val clientId: String,
    @Value("\${aidbox.client.secret}")
    val clientSecret: String
) {
    val grantType: String = "client_credentials"

    // Override toString() to prevent accidentally leaking the clientSecret
    override fun toString(): String = this::class.simpleName!!
}
