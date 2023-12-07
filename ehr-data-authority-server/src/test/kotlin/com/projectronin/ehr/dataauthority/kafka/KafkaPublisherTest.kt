package com.projectronin.ehr.dataauthority.kafka

import com.projectronin.ehr.dataauthority.models.ChangeType
import com.projectronin.ehr.dataauthority.models.kafka.EhrDAKafkaTopic
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.PractitionerRole
import com.projectronin.interop.kafka.client.KafkaClient
import com.projectronin.interop.kafka.model.Failure
import com.projectronin.interop.kafka.model.KafkaAction
import com.projectronin.interop.kafka.model.KafkaEvent
import com.projectronin.interop.kafka.model.PushResponse
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import com.projectronin.fhir.r4.Patient as EventPatient
import com.projectronin.fhir.r4.PractitionerRole as EventPractitionerRole

class KafkaPublisherTest {
    private val patientTopic =
        mockk<EhrDAKafkaTopic> {
            every { systemName } returns "system"
            every { resourceClass } returns Patient::class
            every { eventClass } returns EventPatient::class
        }
    private val practitionerRoleTopic =
        mockk<EhrDAKafkaTopic> {
            every { systemName } returns "system"
            every { resourceClass } returns PractitionerRole::class
            every { eventClass } returns EventPractitionerRole::class
        }

    private val kafkaClient = mockk<KafkaClient>()
    private val topics = listOf(patientTopic, practitionerRoleTopic)
    private val publisher = KafkaPublisher(kafkaClient, topics)

    private val patient =
        Patient(
            id = Id("tenant-1234"),
        )
    private val eventPatient =
        EventPatient().apply {
            resourceType = "Patient"
            id = "tenant-1234"
        }

    private val practitionerRole =
        PractitionerRole(
            id = Id("tenant-5678"),
        )
    private val eventPractitionerRole =
        EventPractitionerRole().apply {
            resourceType = "PractitionerRole"
            id = "tenant-5678"
        }

    @Test
    fun `throws error when no topic found for resource`() {
        val location = Location()

        val exception =
            assertThrows<IllegalStateException> {
                publisher.publishResource(location, ChangeType.NEW)
            }

        assertEquals("No Kafka topic is defined for the supplied resource of type Location", exception.message)

        verify { kafkaClient wasNot Called }
    }

    @Test
    fun `supports new resources`() {
        val kafkaEvent =
            KafkaEvent(
                domain = "system",
                resource = "patient",
                action = KafkaAction.CREATE,
                resourceId = "tenant-1234",
                data = eventPatient,
            )

        every { kafkaClient.publishEvents(patientTopic, listOf(kafkaEvent)) } returns
            PushResponse(
                successful =
                    listOf(
                        kafkaEvent,
                    ),
            )

        publisher.publishResource(patient, ChangeType.NEW)

        verify(exactly = 1) { kafkaClient.publishEvents(patientTopic, listOf(kafkaEvent)) }
    }

    @Test
    fun `supports changed resources`() {
        val kafkaEvent =
            KafkaEvent(
                domain = "system",
                resource = "patient",
                action = KafkaAction.UPDATE,
                resourceId = "tenant-1234",
                data = eventPatient,
            )

        every { kafkaClient.publishEvents(patientTopic, listOf(kafkaEvent)) } returns
            PushResponse(
                successful =
                    listOf(
                        kafkaEvent,
                    ),
            )

        publisher.publishResource(patient, ChangeType.CHANGED)

        verify(exactly = 1) { kafkaClient.publishEvents(patientTopic, listOf(kafkaEvent)) }
    }

    @Test
    fun `throws error when unsupported change type provided`() {
        val exception =
            assertThrows<IllegalStateException> {
                publisher.publishResource(patient, ChangeType.UNCHANGED)
            }

        assertEquals("Kafka publishing is not supporting for supplied ChangeType UNCHANGED", exception.message)

        verify { kafkaClient wasNot Called }
    }

    @Test
    fun `supports multi-name resource types`() {
        val kafkaEvent =
            KafkaEvent(
                domain = "system",
                resource = "practitioner-role",
                action = KafkaAction.CREATE,
                resourceId = "tenant-5678",
                data = eventPractitionerRole,
            )

        every { kafkaClient.publishEvents(practitionerRoleTopic, listOf(kafkaEvent)) } returns
            PushResponse(
                successful =
                    listOf(
                        kafkaEvent,
                    ),
            )

        publisher.publishResource(practitionerRole, ChangeType.NEW)

        verify(exactly = 1) { kafkaClient.publishEvents(practitionerRoleTopic, listOf(kafkaEvent)) }
    }

    @Test
    fun `throws error when kafka reports a failure`() {
        val kafkaEvent =
            KafkaEvent(
                domain = "system",
                resource = "patient",
                action = KafkaAction.UPDATE,
                resourceId = "tenant-1234",
                data = eventPatient,
            )

        val publishException = IllegalStateException("FAILURE")
        every { kafkaClient.publishEvents(patientTopic, listOf(kafkaEvent)) } returns
            PushResponse(
                failures = listOf(Failure(kafkaEvent, publishException)),
            )

        val exception = assertThrows<IllegalStateException> { publisher.publishResource(patient, ChangeType.CHANGED) }
        assertEquals(publishException, exception)

        verify(exactly = 1) { kafkaClient.publishEvents(patientTopic, listOf(kafkaEvent)) }
    }
}
