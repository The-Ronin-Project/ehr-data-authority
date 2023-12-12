package com.projectronin.ehr.dataauthority.client.spring

import com.projectronin.ehr.dataauthority.client.EHRDataAuthorityClient
import io.ktor.client.HttpClient
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.getBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [EHRDataAuthorityClientSpringConfig::class, TestConfig::class])
class EHRDataAuthorityClientSpringConfigTest {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `loads client`() {
        val client = applicationContext.getBean<EHRDataAuthorityClient>()
        assertNotNull(client)
        assertInstanceOf(EHRDataAuthorityClient::class.java, client)
    }
}

@Configuration
class TestConfig {
    @Bean
    fun httpClient() = mockk<HttpClient>(relaxed = true)
}
