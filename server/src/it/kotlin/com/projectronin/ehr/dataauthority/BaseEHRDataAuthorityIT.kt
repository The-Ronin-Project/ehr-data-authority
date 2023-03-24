package com.projectronin.ehr.dataauthority

import com.projectronin.interop.common.http.spring.HttpSpringConfig
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File

abstract class BaseEHRDataAuthorityIT {
    companion object {
        val docker =
            DockerComposeContainer(File(BaseEHRDataAuthorityIT::class.java.getResource("docker-compose.yaml")!!.file))
                .waitingFor("ehr-data-authority", Wait.forLogMessage(".*Started EHRDataAuthorityServerKt.*", 1))
                .start()
    }
    protected val serverUrl = "http://localhost:8080"
    protected val httpClient = HttpSpringConfig().getHttpClient()
}
