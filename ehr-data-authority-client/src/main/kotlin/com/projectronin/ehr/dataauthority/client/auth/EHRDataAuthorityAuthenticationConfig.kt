package com.projectronin.ehr.dataauthority.client.auth

import com.projectronin.interop.common.http.auth.AuthMethod
import com.projectronin.interop.common.http.auth.AuthenticationConfig
import com.projectronin.interop.common.http.auth.Client
import com.projectronin.interop.common.http.auth.InteropAuthenticationService
import com.projectronin.interop.common.http.auth.Token
import io.ktor.client.HttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component

@Component
class EHRDataAuthorityAuthenticationConfig(
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
    private val useAuth0: Boolean,
) {
    @Bean(name = [AUTH_SERVICE_BEAN_NAME])
    fun interopAuthenticationService() =
        InteropAuthenticationService(
            client,
            AuthenticationConfig(
                Token(authTokenUrl),
                audience,
                Client(
                    authClientId,
                    authClientSecret,
                ),
                if (useAuth0) AuthMethod.AUTH0 else AuthMethod.STANDARD,
            ),
        )

    companion object {
        const val AUTH_SERVICE_BEAN_NAME = "EHRDataAuthorityAuthenticationService"
    }
}
