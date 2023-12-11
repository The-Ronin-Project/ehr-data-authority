package com.projectronin.ehr.dataauthority

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {
    @Value("\${auth0.audience}")
    lateinit var audience: String

    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    lateinit var issuer: String

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
    ): SecurityFilterChain {
        http {
            cors { }
            oauth2ResourceServer {
                jwt {
                    this.jwtDecoder = jwtDecoder
                }
            }
            authorizeRequests {
                authorize("/actuator/**", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize("/v3/api-docs/**", permitAll)
                authorize(anyRequest, authenticated)
            }
        }
        return http.build()
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val validator =
            DelegatingOAuth2TokenValidator(
                listOf(
                    JwtValidators.createDefaultWithIssuer(issuer),
                    JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD) { aud -> audience in aud },
                ),
            )

        // Build the JWT Decoder
        val jwtDecoder = JwtDecoders.fromOidcIssuerLocation<NimbusJwtDecoder>(issuer)
        jwtDecoder.setJwtValidator(validator)
        return jwtDecoder
    }
}
