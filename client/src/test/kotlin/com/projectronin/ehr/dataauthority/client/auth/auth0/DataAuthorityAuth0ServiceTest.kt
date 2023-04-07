package com.projectronin.ehr.dataauthority.client.auth.auth0

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
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DataAuthorityAuth0ServiceTest {
    private val mockWebServer = MockWebServer()
    private val authUrl = mockWebServer.url("/oauth2/token").toString()
    private val audience = "audience"
    private val clientId = "MyTestClientId"
    private val clientSecret = "SuperSecretAndSafe"
    private val expectedPayload =
        """{"client_id":"$clientId","client_secret":"$clientSecret","audience":"$audience","grant_type":"client_credentials"}"""

    @Test
    fun `error while retrieving authentication`() {
        val httpClient = makeClient(expectedPayload, "", HttpStatusCode.ServiceUnavailable)
        val service = DataAuthorityAuth0Service(
            httpClient,
            authUrl,
            "audience",
            "MyTestClientId",
            "SuperSecretAndSafe"
        )
        assertThrows<ServiceUnavailableException> {
            service.getAuthentication()
        }
    }

    @Test
    fun `retrieves authentication`() {
        val responseContent = """{
            |  "token_type" : "Bearer",
            |  "access_token": "abcd1234",
            |  "expires_in": 3600,
            |  "scope": "local",
            |  "refresh_token": "efgh5678"
            |}
        """.trimMargin()

        val httpClient = makeClient(expectedPayload, responseContent, HttpStatusCode.OK)
        val service = DataAuthorityAuth0Service(
            httpClient,
            authUrl,
            "audience",
            "MyTestClientId",
            "SuperSecretAndSafe"
        )
        val authentication = service.getAuthentication()
        assertEquals("Bearer", authentication.tokenType)
        assertEquals("abcd1234", authentication.accessToken)
        Assertions.assertNotNull(authentication.expiresAt)
        assertEquals("local", authentication.scope)
        assertEquals("efgh5678", authentication.refreshToken)
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
