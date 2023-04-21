package com.projectronin.ehr.dataauthority

import com.projectronin.ehr.dataauthority.testclients.DBClient
import com.projectronin.ehr.dataauthority.testclients.KafkaClient
import com.projectronin.ehr.dataauthority.testclients.ValidationClient
import com.projectronin.fhir.r4.Resource
import com.projectronin.interop.common.http.spring.HttpSpringConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import kotlin.reflect.KClass

abstract class BaseEHRDataAuthorityIT {
    companion object {

        val docker =
            DockerComposeContainer(File(BaseEHRDataAuthorityIT::class.java.getResource("/docker-compose-it.yaml")!!.file)).withEnv(
                mapOf<String, String>(
                    "AIDBOX_LICENSE_ID" to System.getenv("AIDBOX_LICENSE_ID"),
                    "AIDBOX_LICENSE_KEY" to System.getenv("AIDBOX_LICENSE_KEY"),
                    "AIDBOX_PORT" to "8888",
                    "AIDBOX_CLIENT_ID" to "client",
                    "AIDBOX_ADMIN_ID" to "admin",
                    "AIDBOX_ADMIN_PASSWORD" to "secret",
                    "AIDBOX_CLIENT_SECRET" to "secret",
                    "AIDBOX_DEV_MODE" to "true",
                    "PGPORT" to "5432",
                    "PGHOSTPORT" to "5437",
                    "AIDBOX_FHIR_VERSION" to "4.0.0",
                    "PGHOST" to "database",
                    "PGUSER" to "postgres",
                    "POSTGRES_USER" to "postgres",
                    "POSTGRES_PASSWORD" to "postgres",
                    "POSTGRES_DB" to "devbox",
                    "PGPASSWORD" to "postgres",
                    "PGDATABASE" to "devbox",
                    "box_features_validation_skip_reference" to "true"
                )
            )
                .waitingFor("ehr-data-authority", Wait.forLogMessage(".*Started EHRDataAuthorityServerKt.*", 1))
                .start()
    }

    protected val serverUrl = "http://localhost:8080"
    protected val httpClient = HttpSpringConfig().getHttpClient()

    open val resources = mapOf<String, KClass<out Resource>>()

    @BeforeEach
    fun setup() {
        resources.forEach { (topic, resource) ->
            KafkaClient.monitorResource(topic, resource)
        }
    }

    @AfterEach
    fun cleanup() {
        DBClient.purgeHashes()
        KafkaClient.reset()
        ValidationClient.clearAllResources()
    }
}
