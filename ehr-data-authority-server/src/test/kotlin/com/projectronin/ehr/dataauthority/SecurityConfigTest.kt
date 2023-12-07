package com.projectronin.ehr.dataauthority

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.DefaultSecurityFilterChain

class SecurityConfigTest {
    @Test
    fun `builds JwtDecoder`() {
        mockkStatic(JwtDecoders::class)
        val mockJwtDecoder =
            mockk<NimbusJwtDecoder> {
                every { setJwtValidator(any()) } just Runs
            }

        every { JwtDecoders.fromOidcIssuerLocation<NimbusJwtDecoder>("issuer") } returns mockJwtDecoder

        val securityConfig =
            SecurityConfig().apply {
                audience = "audience"
                issuer = "issuer"
            }

        val jwtDecoder = securityConfig.jwtDecoder()
        assertEquals(mockJwtDecoder, jwtDecoder)

        unmockkAll()
    }

    @Test
    fun `builds SecurityFilterChain`() {
        mockkStatic(JwtDecoders::class)
        val mockJwtDecoder =
            mockk<NimbusJwtDecoder> {
                every { setJwtValidator(any()) } just Runs
            }

        every { JwtDecoders.fromOidcIssuerLocation<NimbusJwtDecoder>("issuer") } returns mockJwtDecoder

        val mockSecurityFilterChain = mockk<DefaultSecurityFilterChain>()
        val httpSecurity =
            mockk<HttpSecurity> {
                every {
                    authorizeRequests()
                        .antMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest().authenticated()
                        .and().cors()
                        .and().oauth2ResourceServer().jwt().decoder(any())
                } returns mockk()
                every { build() } returns mockSecurityFilterChain
            }

        val securityConfig =
            SecurityConfig().apply {
                audience = "audience"
                issuer = "issuer"
            }

        val securityFilterChain = securityConfig.securityFilterChain(httpSecurity)
        assertEquals(mockSecurityFilterChain, securityFilterChain)

        unmockkAll()
    }
}
