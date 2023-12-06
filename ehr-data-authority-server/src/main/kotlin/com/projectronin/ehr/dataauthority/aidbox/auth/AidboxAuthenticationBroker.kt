package com.projectronin.ehr.dataauthority.aidbox.auth

import com.projectronin.interop.common.auth.Authentication
import mu.KotlinLogging
import java.time.Instant

/**
 * Brokers [Authentication] allowing re-use of existing credentials as long as they have not expired.
 */
class AidboxAuthenticationBroker(private val authenticationService: AidboxAuthenticationService) {
    private val logger = KotlinLogging.logger { }
    private val expirationBuffer: Long = 60 // seconds
    private var cachedAuthentication: Authentication? = null

    /**
     * Retrieves the current [Authentication] to use. If cached authentication is invalid/expired
     * delete the token session before requesting fresh authentication for Aidbox
     */
    fun getAuthentication(): Authentication {
        val isCacheValid =
            cachedAuthentication?.expiresAt?.isAfter(Instant.now().plusSeconds(expirationBuffer)) ?: false
        if (isCacheValid) {
            logger.debug { "Returning cached authentication for Aidbox" }
            return cachedAuthentication!!
        } else if (cachedAuthentication?.accessToken?.isNotBlank() == true) {
            logger.debug { "Deleting token Session" }
            authenticationService.deleteAuthenticationTokenSession(cachedAuthentication!!.accessToken)
        }
        logger.debug { "Requesting fresh authentication for Aidbox" }
        val authentication = authenticationService.getAuthentication()
        logger.debug { "Retrieved authentication Aidbox has expiration (${authentication.expiresAt})" }
        authentication.expiresAt?.let {
            logger.debug { "Caching expiration" }
            cachedAuthentication = authentication
        }
        return authentication
    }

    fun reauthenticate(): Authentication {
        logger.debug { "Invalidating cached credentials for Aidbox" }
        this.cachedAuthentication = null
        return this.getAuthentication()
    }
}
