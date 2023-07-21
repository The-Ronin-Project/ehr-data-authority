package com.projectronin.ehr.dataauthority.kafka

import com.google.common.base.CaseFormat
import com.projectronin.ehr.dataauthority.models.ChangeType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.kafka.client.KafkaClient
import com.projectronin.interop.kafka.model.KafkaAction
import com.projectronin.interop.kafka.model.KafkaEvent
import org.springframework.stereotype.Component
import com.projectronin.fhir.r4.Resource as ResourceEvent

/**
 * Publisher responsible for publish EHR data to Kafka.
 */
@Component
class KafkaPublisher(private val kafkaClient: KafkaClient, topics: List<EhrDAKafkaTopic>) {
    private val topicsByResource = topics.associateBy { it.resourceClass }

    /**
     * Publishes the [resource] to Kafka based off the [changeType].
     */
    fun publishResource(resource: Resource<*>, changeType: ChangeType) {
        val topic = topicsByResource[resource::class]
            ?: throw IllegalStateException("No Kafka topic is defined for the supplied resource of type ${resource.resourceType}")
        val eventResource = convertResource(resource, topic)

        val kafkaEvent = KafkaEvent(
            domain = topic.systemName,
            resource = resource.kafkaResourceType(),
            action = changeType.action(),
            resourceId = resource.id!!.value!!,
            data = eventResource
        )

        val response = kafkaClient.publishEvents(topic, listOf(kafkaEvent))
        response.failures.firstOrNull()?.let { (_, error) ->
            throw error
        }
    }

    /**
     * Converts the [resource] into the appropriate [ResourceEvent] as defined by the [topic].
     */
    private fun convertResource(resource: Resource<*>, topic: EhrDAKafkaTopic): ResourceEvent {
        val resourceJson = JacksonManager.objectMapper.writeValueAsString(resource)
        return JacksonManager.objectMapper.readValue(resourceJson, topic.eventClass.java)
    }

    /**
     * Returns the Kafka resource type name for this resource.
     */
    private fun Resource<*>.kafkaResourceType() = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, resourceType)

    /**
     * Returns the [KafkaAction] associated to this ChangeType.
     */
    private fun ChangeType.action() = when (this) {
        ChangeType.NEW -> KafkaAction.CREATE
        ChangeType.CHANGED -> KafkaAction.UPDATE
        else -> throw IllegalStateException("Kafka publishing is not supporting for supplied ChangeType $this")
    }
}
