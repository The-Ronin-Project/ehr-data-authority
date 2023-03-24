package com.projectronin.ehr.dataauthority

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EHRDataAuthorityServerTest {

    @Autowired
    private lateinit var server: EHRDataAuthorityServer

    @Test
    fun `server works`() {
        assertNotNull(server)
    }
}
