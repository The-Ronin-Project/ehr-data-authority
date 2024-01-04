package com.projectronin.ehr.dataauthority.aidbox.auth

import com.projectronin.interop.common.auth.Authentication
import com.projectronin.interop.common.auth.BrokeredAuthenticator

/**
 * Brokers [Authentication] allowing re-use of existing credentials as long as they have not expired.
 */
class AidboxAuthenticationBroker(
    private val authenticationService: AidboxAuthenticationService,
    expirationBufferInSeconds: Long = 60,
) :
    BrokeredAuthenticator(expirationBufferInSeconds) {
    override fun reloadAuthentication(): Authentication {
        return authenticationService.getAuthentication()
    }
}
