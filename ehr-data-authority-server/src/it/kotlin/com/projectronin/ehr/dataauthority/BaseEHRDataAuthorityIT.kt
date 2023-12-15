package com.projectronin.ehr.dataauthority

import com.projectronin.ehr.BaseEHRDataAuthority
import com.projectronin.ehr.dataauthority.testclients.DBClient
import com.projectronin.ehr.dataauthority.testclients.KafkaClient
import com.projectronin.ehr.dataauthority.testclients.ValidationClient
import com.projectronin.fhir.r4.Resource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.reflect.KClass

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class BaseEHRDataAuthorityIT : BaseEHRDataAuthority() {
    override fun getDockerEnv() = mutableMapOf(
        "AIDBOX_LICENSE_ID" to System.getenv("AIDBOX_LICENSE_ID"),
        "AIDBOX_LICENSE_KEY" to System.getenv("AIDBOX_LICENSE_KEY"),
        "AIDBOX_PORT" to "8888",
        "AIDBOX_CLIENT_ID" to "client",
        "AIDBOX_ADMIN_ID" to "admin",
        "AIDBOX_ADMIN_PASSWORD" to "secret",
        "AIDBOX_CLIENT_SECRET" to "secret",
        "AIDBOX_DEV_MODE" to "true",
        "AIDBOX_FHIR_VERSION" to "4.0.0",
        "PGPORT" to "5432",
        "PGHOSTPORT" to "5437",
        "PGHOST" to "database",
        "PGUSER" to "postgres",
        "POSTGRES_USER" to "postgres",
        "POSTGRES_PASSWORD" to "postgres",
        "POSTGRES_DB" to "devbox",
        "PGPASSWORD" to "postgres",
        "PGDATABASE" to "devbox",
        "box_features_validation_skip_reference" to "true"
    )

    override fun getDockerCompose() = "/docker-compose-it.yaml"

    open val resources = mapOf<String, KClass<out Resource>>()

    @BeforeEach
    fun setupKafka() {
        // Doing this as a before all causes problems, but KafkaClient.monitorResource already checks for
        // an existing resource so this really only happens once.
        resources.forEach { (topic, resource) ->
            KafkaClient.monitorResource(topic, resource)
        }
    }

    @AfterAll
    fun resetKafka() {
        // Only reset once at the end of each group of tests
        KafkaClient.reset()
    }

    @AfterEach
    fun cleanup() {
        DBClient.purgeHashes()
        ValidationClient.clearAllResources()
    }
}
