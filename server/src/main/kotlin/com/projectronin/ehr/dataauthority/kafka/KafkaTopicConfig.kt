package com.projectronin.ehr.dataauthority.kafka

import com.google.common.base.CaseFormat
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.kafka.spring.KafkaConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.reflect.KClass
import com.projectronin.fhir.r4.Resource as EventResource

@Configuration
class KafkaTopicConfig(kafkaConfig: KafkaConfig) {
    private val vendor = kafkaConfig.cloud.vendor
    private val region = kafkaConfig.cloud.region
    private val systemName = kafkaConfig.retrieve.serviceId

    @Bean
    fun appointmentTopic() = createTopic(
        com.projectronin.interop.fhir.r4.resource.Appointment::class,
        com.projectronin.fhir.r4.Appointment::class
    )

    @Bean
    fun conditionTopic() = createTopic(
        com.projectronin.interop.fhir.r4.resource.Condition::class,
        com.projectronin.fhir.r4.Condition::class
    )

    @Bean
    fun locationTopic() = createTopic(
        com.projectronin.interop.fhir.r4.resource.Location::class,
        com.projectronin.fhir.r4.Location::class
    )

    @Bean
    fun patientTopic() = createTopic(
        com.projectronin.interop.fhir.r4.resource.Patient::class,
        com.projectronin.fhir.r4.Patient::class
    )

    @Bean
    fun practitionerTopic() = createTopic(
        com.projectronin.interop.fhir.r4.resource.Practitioner::class,
        com.projectronin.fhir.r4.Practitioner::class
    )

    @Bean
    fun practitionerRoleTopic() = createTopic(
        com.projectronin.interop.fhir.r4.resource.PractitionerRole::class,
        com.projectronin.fhir.r4.PractitionerRole::class
    )

    private fun createTopic(
        resourceClass: KClass<out Resource<*>>,
        eventClass: KClass<out EventResource>
    ): EhrDAKafkaTopic {
        val resourceType = eventClass.simpleName!!
        val topicName =
            "$vendor.$region.$systemName.${CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, resourceType)}.v1"
        val schema =
            "https://github.com/projectronin/ronin-fhir-models/blob/main/common-fhir-r4-models/v1/$resourceType-v1.schema.json"
        return EhrDAKafkaTopic(
            systemName = systemName,
            topicName = topicName,
            dataSchema = schema,
            resourceClass = resourceClass,
            eventClass = eventClass
        )
    }
}
