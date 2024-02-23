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
open class BaseEHRDataAuthorityIT : BaseEHRDataAuthority(8080, 8081) {
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
