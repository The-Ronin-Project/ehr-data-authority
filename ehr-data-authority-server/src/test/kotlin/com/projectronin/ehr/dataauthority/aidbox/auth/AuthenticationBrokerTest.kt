package com.projectronin.ehr.dataauthority.aidbox.auth

import com.projectronin.interop.common.auth.Authentication
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class AuthenticationBrokerTest {
    private lateinit var authenticationService: AidboxAuthenticationService
    private lateinit var broker: AidboxAuthenticationBroker

    private val cachedAuthorizationProperty =
        AidboxAuthenticationBroker::class.memberProperties.first { it.name == "cachedAuthentication" } as KMutableProperty<Authentication?>

    init {
        cachedAuthorizationProperty.isAccessible = true
    }

    @BeforeEach
    fun initTest() {
        authenticationService = mockk()
        broker = AidboxAuthenticationBroker(authenticationService)
    }

    @Test
    fun `loads partially specified authentication when not cached`() {
        every { authenticationService.getAuthentication() } returns AidboxAuthentication("Bearer", "token")

        val authentication = broker.getAuthentication()
        assertEquals("Bearer", authentication.tokenType)
        assertEquals("token", authentication.accessToken)
        assertNull(authentication.expiresAt)
        assertNull(authentication.scope)
        assertNull(authentication.refreshToken)
    }

    @Test
    fun `loads fully specified authentication when not cached`() {
        every { authenticationService.getAuthentication() } returns AidboxAuthentication(
            "Bearer",
            "token",
            360,
            "refresh",
            "scope"
        )
        val authentication = broker.getAuthentication()
        assertEquals("Bearer", authentication.tokenType)
        assertEquals("token", authentication.accessToken)
        assertNotNull(authentication.expiresAt)
        assertEquals("scope", authentication.scope)
        assertEquals("refresh", authentication.refreshToken)
    }

    @Test
    fun `caches authentication when expiration provided`() {
        every { authenticationService.getAuthentication() } returns AidboxAuthentication("Bearer", "token", 360)

        val authentication = broker.getAuthentication()
        assertEquals("Bearer", authentication.tokenType)
        assertEquals("token", authentication.accessToken)
        assertNotNull(authentication.expiresAt)
        assertNull(authentication.scope)
        assertNull(authentication.refreshToken)

        assertEquals(authentication, getCachedAuthentication())
    }

    @Test
    fun `does not cache authentication when no expiration provided`() {
        every { authenticationService.getAuthentication() } returns AidboxAuthentication("Bearer", "token")

        val authentication = broker.getAuthentication()
        assertEquals("Bearer", authentication.tokenType)
        assertEquals("token", authentication.accessToken)
        assertNull(authentication.expiresAt)
        assertNull(authentication.scope)
        assertNull(authentication.refreshToken)

        assertNull(getCachedAuthentication())
    }

    @Test
    fun `loads authentication when cached has no expiration`() {
        val cachedAuthentication = mockk<Authentication> {
            every { expiresAt } returns null
            every { accessToken } returns "token"
        }
        setCachedAuthentication(cachedAuthentication)

        every { authenticationService.getAuthentication() } returns AidboxAuthentication("Bearer", "token")
        every { authenticationService.deleteAuthenticationTokenSession("token") } returns HttpStatusCode.OK
        val authentication = broker.getAuthentication()
        assertEquals("Bearer", authentication.tokenType)
        assertEquals("token", authentication.accessToken)
        assertNull(authentication.expiresAt)
        assertNull(authentication.scope)
        assertNull(authentication.refreshToken)
    }

    @Test
    fun `loads authentication when cached has expired`() {
        val cachedAuthentication = mockk<Authentication> {
            every { expiresAt } returns Instant.now().minusSeconds(600)
            every { accessToken } returns "token"
        }
        setCachedAuthentication(cachedAuthentication)

        every { authenticationService.getAuthentication() } returns AidboxAuthentication(
            "Bearer",
            "token"
        )
        every { authenticationService.deleteAuthenticationTokenSession("token") } returns HttpStatusCode.OK
        val authentication = broker.getAuthentication()

        assertEquals("Bearer", authentication.tokenType)
        assertEquals("token", authentication.accessToken)
        assertNull(authentication.expiresAt)
        assertNull(authentication.scope)
        assertNull(authentication.refreshToken)
    }

    @Test
    fun `loads authentication when cached expires within buffer`() {
        val cachedAuthentication = mockk<Authentication> {
            every { expiresAt } returns Instant.now().plusSeconds(25)
            every { accessToken } returns "token"
        }
        setCachedAuthentication(cachedAuthentication)

        every { authenticationService.getAuthentication() } returns AidboxAuthentication("Bearer", "token")
        every { authenticationService.deleteAuthenticationTokenSession("token") } returns HttpStatusCode.OK

        val authentication = broker.getAuthentication()
        assertEquals("Bearer", authentication.tokenType)
        assertEquals("token", authentication.accessToken)
        assertNull(authentication.expiresAt)
        assertNull(authentication.scope)
        assertNull(authentication.refreshToken)
    }

    @Test
    fun `returns cached authentication when still valid`() {
        val cachedAuthentication = mockk<Authentication> {
            every { expiresAt } returns Instant.now().plusSeconds(600)
            every { tokenType } returns "Basic"
            every { accessToken } returns "cached_token"
            every { scope } returns null
            every { refreshToken } returns null
        }
        setCachedAuthentication(cachedAuthentication)

        val authentication = broker.getAuthentication()
        assertEquals("Basic", authentication.tokenType)
        assertEquals("cached_token", authentication.accessToken)
        assertNotNull(authentication.expiresAt)
        assertNull(authentication.scope)
        assertNull(authentication.refreshToken)
    }

    private fun getCachedAuthentication() = cachedAuthorizationProperty.getter.call(broker)
    private fun setCachedAuthentication(authentication: Authentication?) =
        cachedAuthorizationProperty.setter.call(broker, authentication)
}
