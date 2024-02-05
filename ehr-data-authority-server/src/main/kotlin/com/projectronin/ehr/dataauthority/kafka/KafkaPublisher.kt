package com.projectronin.ehr.dataauthority.kafka

import com.google.common.base.CaseFormat
import com.projectronin.ehr.dataauthority.change.data.services.DataStorageService
import com.projectronin.ehr.dataauthority.models.ChangeType
import com.projectronin.ehr.dataauthority.models.kafka.EhrDAKafkaTopic
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.resource.Participant
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
class KafkaPublisher(
    private val kafkaClient: KafkaClient,
    topics: List<EhrDAKafkaTopic>,
    private val dataStorageService: DataStorageService,
) {
    private val topicsByResource = topics.associateBy { it.resourceClass }

    /**
     * Publishes the [resource] to Kafka based off the [changeType].
     */
    fun publishResource(
        resource: Resource<*>,
        changeType: ChangeType,
    ) {
        val resourceType = resource.resourceType
        val resourceId = resource.id!!.value!!

        val topic =
            topicsByResource[resource::class]
                ?: throw IllegalStateException("No Kafka topic is defined for the supplied resource of type $resourceType")
        val eventResource = convertResource(resource, topic)

        val storedResource = dataStorageService.getResource(resourceType, resourceId)
        val resourceVersion = storedResource.meta?.versionId?.value?.toInt()

        val tenantId = storedResource.findTenantId()
        val patientId = storedResource.findPatientId()

        val kafkaEvent =
            KafkaEvent(
                domain = topic.systemName,
                resource = resource.kafkaResourceType(),
                action = changeType.action(),
                resourceId = resourceId,
                resourceVersionId = resourceVersion,
                tenantId = tenantId,
                patientId = patientId,
                data = eventResource,
            )

        val response = kafkaClient.publishEvents(topic, listOf(kafkaEvent))
        response.failures.firstOrNull()?.let { (_, error) ->
            throw error
        }
    }

    /**
     * Converts the [resource] into the appropriate [ResourceEvent] as defined by the [topic].
     */
    private fun convertResource(
        resource: Resource<*>,
        topic: EhrDAKafkaTopic,
    ): ResourceEvent {
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
    private fun ChangeType.action() =
        when (this) {
            ChangeType.NEW -> KafkaAction.CREATE
            ChangeType.CHANGED -> KafkaAction.UPDATE
            else -> throw IllegalStateException("Kafka publishing is not supporting for supplied ChangeType $this")
        }
}

/**
 * Finds the patient ID from the resource, if one is present.
 */
@Suppress("UNCHECKED_CAST")
internal fun Resource<*>.findPatientId(): String? {
    if (resourceType == "Patient") {
        return id!!.value!!
    }

    if (resourceType == "Appointment") {
        val participants = getDeclaredField("participant") as? List<Participant> ?: return null
        return participants.mapNotNull { it.findPatientIdIn("actor") }.singleOrNull()
    }

    // Try looking in "subject" first, and then fall-back to "patient" if no subject found.
    return findPatientIdIn("subject") ?: findPatientIdIn("patient")
}

internal fun Any.getDeclaredField(location: String): Any? {
    return runCatching {
        val field = this::class.java.getDeclaredField(location)
        field.isAccessible = true
        field.get(this)
    }.getOrNull()
}

/**
 * Finds the patient ID in the supplied location, if one is present.
 */
internal fun Any.findPatientIdIn(location: String): String? {
    val value = getDeclaredField(location) ?: return null

    val referenceType = "Patient"

    val singleReference =
        if (value is Reference) {
            if (value.isForType(referenceType)) {
                value
            } else {
                null
            }
        } else {
            null
        }

    return singleReference?.decomposedId()
}
