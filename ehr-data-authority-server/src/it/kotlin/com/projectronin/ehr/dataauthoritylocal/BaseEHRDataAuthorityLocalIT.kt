package com.projectronin.ehr.dataauthoritylocal

import com.projectronin.ehr.BaseEHRDataAuthority
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class BaseEHRDataAuthorityLocalIT : BaseEHRDataAuthority() {
    override fun getDockerEnv() = mutableMapOf("SPRING_PROFILES_ACTIVE" to "local")

    override fun getDockerCompose() = "/docker-compose-local.yaml"

    @BeforeEach
    fun sleep() {
        Thread.sleep(10000)
    }
}
