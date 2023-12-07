package com.projectronin.ehr.dataauthority.client.auth

import com.projectronin.ehr.dataauthority.client.auth.EHRDataAuthorityAuthenticationService.Auth0Authentication
import com.projectronin.ehr.dataauthority.client.auth.EHRDataAuthorityAuthenticationService.FormBasedAuthentication
import com.projectronin.interop.common.http.exceptions.ClientAuthenticationException
import com.projectronin.interop.common.http.spring.HttpSpringConfig
import io.ktor.http.HttpStatusCode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EHRDataAuthorityAuthenticationServiceTest {
    private val mockWebServer = MockWebServer()
    private val authUrl = mockWebServer.url("/oauth2/token").toString()
    private val httpClient = HttpSpringConfig().getHttpClient()
    private val audience = "audience"
    private val clientId = "MyTestClientId"
    private val clientSecret = "SuperSecretAndSafe"
    private val service =
        EHRDataAuthorityAuthenticationService(httpClient, authUrl, audience, clientId, clientSecret, true)

    private val expectedPayload =
        """{"client_id":"$clientId","client_secret":"$clientSecret","audience":"$audience","grant_type":"client_credentials"}"""

    @Test
    fun `error while retrieving authentication`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.Forbidden.value)
                .setBody("")
                .setHeader("Content-Type", "application/json"),
        )

        val exception =
            assertThrows<ClientAuthenticationException> {
                service.getAuthentication()
            }
        val message = exception.message!!
        assertTrue(message.contains("Received 403 Client Error when calling Auth0"))
        assertTrue(message.contains("for POST"))
        assertTrue(message.contains("/oauth2/token"))

        val request = mockWebServer.takeRequest()
        assertEquals("application/json", request.getHeader("Content-Type"))
        assertEquals(expectedPayload, request.body.readUtf8())
    }

    @Test
    fun `retrieves authentication`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.OK.value)
                .setBody("""{"access_token":"YouShallPass","token_type":"Bearer","expires_in":180}""")
                .setHeader("Content-Type", "application/json"),
        )

        val authentication = service.getAuthentication()
        assertInstanceOf(Auth0Authentication::class.java, authentication)
        assertEquals("YouShallPass", authentication.accessToken)
        assertEquals("Bearer", authentication.tokenType)
        assertNotNull(authentication.expiresAt)
        assertNull(authentication.refreshToken)
        assertNull(authentication.scope)

        val request = mockWebServer.takeRequest()
        assertEquals("application/json", request.getHeader("Content-Type"))
        assertEquals(expectedPayload, request.body.readUtf8())
    }

    @Test
    fun `retrieves form-based authentication`() {
        val service =
            EHRDataAuthorityAuthenticationService(httpClient, authUrl, audience, clientId, clientSecret, false)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpStatusCode.OK.value)
                .setBody("""{"access_token":"YouShallPass","token_type":"Bearer"}""")
                .setHeader("Content-Type", "application/json"),
        )

        val authentication = service.getAuthentication()
        assertInstanceOf(FormBasedAuthentication::class.java, authentication)
        assertEquals("YouShallPass", authentication.accessToken)
        assertEquals("Bearer", authentication.tokenType)
        assertNull(authentication.expiresAt)
        assertNull(authentication.refreshToken)
        assertNull(authentication.scope)

        val request = mockWebServer.takeRequest()
        assertEquals("application/x-www-form-urlencoded; charset=UTF-8", request.getHeader("Content-Type"))
        assertEquals(
            "grant_type=client_credentials&client_id=$clientId&client_secret=$clientSecret",
            request.body.readUtf8(),
        )
    }
}
