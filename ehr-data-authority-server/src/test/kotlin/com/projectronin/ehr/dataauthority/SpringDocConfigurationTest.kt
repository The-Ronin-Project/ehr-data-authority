package com.projectronin.ehr.dataauthority

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SpringDocConfigurationTest {
    @Test
    fun `works`() {
        assertNotNull(SpringDocConfiguration().apiInfo())
    }
}
