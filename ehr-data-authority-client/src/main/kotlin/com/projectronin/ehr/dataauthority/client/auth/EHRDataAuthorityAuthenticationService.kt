package com.projectronin.ehr.dataauthority.client.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.projectronin.interop.common.auth.Authentication
import com.projectronin.interop.common.auth.BrokeredAuthenticator
import com.projectronin.interop.common.http.throwExceptionFromHttpStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Service responsible for retrieving [Authentication] to call the Data Ingestion Validation Error Management Server.
 */
@Component
class EHRDataAuthorityAuthenticationService(
    private val client: HttpClient,
    @Value("\${ehrda.auth.token.url}")
    private val authTokenUrl: String,
    @Value("\${ehrda.auth.audience}")
    private val audience: String,
    @Value("\${ehrda.auth.client.id}")
    private val authClientId: String,
    @Value("\${ehrda.auth.client.secret}")
    private val authClientSecret: String,
    @Value("\${ehrda.auth.auth0:true}")
    private val useAuth0: Boolean
) : BrokeredAuthenticator() {
    override fun reloadAuthentication(): Authentication = if (useAuth0) {
        retrieveAuth0Authentication()
    } else {
        retrieveFormBasedAuthentication()
    }

    /**
     * Uses Auth0 to retrieve authentication.
     */
    private fun retrieveAuth0Authentication(): Authentication = runBlocking {
        val payload = Auth0Payload(authClientId, authClientSecret, audience)
        val httpResponse: HttpResponse = client.post(authTokenUrl) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        httpResponse.throwExceptionFromHttpStatus("Auth0", "POST $authTokenUrl")
        httpResponse.body<Auth0Authentication>()
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    private data class Auth0Payload(
        val clientId: String,
        val clientSecret: String,
        val audience: String,
        val grantType: String = "client_credentials"
    )

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

    /**
     * Uses a more traditional form-based authentication.
     */
    private fun retrieveFormBasedAuthentication(): Authentication = runBlocking {
        val json: JsonNode = client.submitForm(
            url = authTokenUrl,
            formParameters = Parameters.build {
                append("grant_type", "client_credentials")
                append("client_id", authClientId)
                append("client_secret", authClientSecret)
            }
        ).body()
        val accessToken = json.get("access_token").asText()
        FormBasedAuthentication(accessToken)
    }

    data class FormBasedAuthentication(override val accessToken: String) : Authentication {
        override val tokenType: String = "Bearer"
        override val expiresAt: Instant? = null
        override val refreshToken: String? = null
        override val scope: String? = null
    }
}
