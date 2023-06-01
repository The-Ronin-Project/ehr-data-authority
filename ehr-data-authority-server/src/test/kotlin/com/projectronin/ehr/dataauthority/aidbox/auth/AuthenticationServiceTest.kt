package com.projectronin.ehr.dataauthority.aidbox.auth

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.projectronin.interop.common.http.exceptions.ServiceUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AuthenticationServiceTest {
    private val baseUrl = "http://localhost:9999"
    private val authUrl = "http://localhost:9999/auth/token"

    @Test
    fun `minimal authentication returned`() {
        val expectedBody =
            """{"client_id":"client-id","client_secret":"client-secret","grant_type":"client_credentials"}"""
        val responseContent = """{
            |  "token_type" : "Bearer",
            |  "access_token": "abcd1234"
            |}
        """.trimMargin()

        val httpClient = makeClient(expectedBody, responseContent, HttpStatusCode.OK)
        val service = AidboxAuthenticationService(httpClient, baseUrl, AidboxCredentials("client-id", "client-secret"))
        val authentication = service.getAuthentication()
        assertEquals("Bearer", authentication.tokenType)
        assertEquals("abcd1234", authentication.accessToken)
        assertNull(authentication.expiresAt)
        assertNull(authentication.scope)
        assertNull(authentication.refreshToken)
    }

    @Test
    fun `full authentication returned`() {
        val expectedBody =
            """{"client_id":"client-id","client_secret":"client-secret","grant_type":"client_credentials"}"""
        val responseContent = """{
            |  "token_type" : "Bearer",
            |  "access_token": "abcd1234",
            |  "expires_in": 3600,
            |  "scope": "local",
            |  "refresh_token": "efgh5678"
            |}
        """.trimMargin()

        val httpClient = makeClient(expectedBody, responseContent, HttpStatusCode.OK)
        val service = AidboxAuthenticationService(httpClient, baseUrl, AidboxCredentials("client-id", "client-secret"))
        val authentication = service.getAuthentication()
        assertEquals("Bearer", authentication.tokenType)
        assertEquals("abcd1234", authentication.accessToken)
        assertNotNull(authentication.expiresAt)
        assertEquals("local", authentication.scope)
        assertEquals("efgh5678", authentication.refreshToken)
    }

    @Test
    fun `exception while authenticating`() {
        val expectedBody =
            """{"client_id":"client-id","client_secret":"client-secret","grant_type":"client_credentials"}"""

        val httpClient = makeClient(expectedBody, "", HttpStatusCode.ServiceUnavailable)
        val service = AidboxAuthenticationService(httpClient, baseUrl, AidboxCredentials("client-id", "client-secret"))
        assertThrows<ServiceUnavailableException> {
            service.getAuthentication()
        }
    }

    private fun makeClient(expectedBody: String, responseContent: String, status: HttpStatusCode): HttpClient =
        HttpClient(
            MockEngine { request ->
                assertEquals(authUrl, request.url.toString())
                assertEquals(expectedBody, String(request.body.toByteArray()))
                respond(
                    content = responseContent,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        ) {
            install(ContentNegotiation) {
                jackson {
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                }
            }
        }
}
