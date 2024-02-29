package com.projectronin.ehr.dataauthority.kafka

import com.projectronin.ehr.dataauthority.models.ChangeType
import com.projectronin.ehr.dataauthority.models.kafka.EhrDAKafkaTopic
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.Meta
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Participant
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.PractitionerRole
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.fhir.r4.valueset.AppointmentStatus
import com.projectronin.interop.fhir.r4.valueset.ParticipationStatus
import com.projectronin.interop.fhir.util.asCode
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import com.projectronin.fhir.r4.Identifier as EventIdentifier
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
        val location = Location(id = Id("tenant-1357"))

        val exception =
            assertThrows<IllegalStateException> {
                publisher.publishResource(location, location, ChangeType.NEW)
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
                resourceVersionId = null,
                tenantId = null,
                patientId = "tenant-1234",
                data = eventPatient,
            )

        every { kafkaClient.publishEvents(patientTopic, listOf(kafkaEvent)) } returns
            PushResponse(
                successful =
                    listOf(
                        kafkaEvent,
                    ),
            )

        publisher.publishResource(patient, patient, ChangeType.NEW)

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
                resourceVersionId = null,
                tenantId = null,
                patientId = "tenant-1234",
                data = eventPatient,
            )

        every { kafkaClient.publishEvents(patientTopic, listOf(kafkaEvent)) } returns
            PushResponse(
                successful =
                    listOf(
                        kafkaEvent,
                    ),
            )

        publisher.publishResource(patient, patient, ChangeType.CHANGED)

        verify(exactly = 1) { kafkaClient.publishEvents(patientTopic, listOf(kafkaEvent)) }
    }

    @Test
    fun `throws error when unsupported change type provided`() {
        val exception =
            assertThrows<IllegalStateException> {
                publisher.publishResource(patient, patient, ChangeType.UNCHANGED)
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
                resourceVersionId = null,
                tenantId = null,
                patientId = null,
                data = eventPractitionerRole,
            )

        every { kafkaClient.publishEvents(practitionerRoleTopic, listOf(kafkaEvent)) } returns
            PushResponse(
                successful =
                    listOf(
                        kafkaEvent,
                    ),
            )

        publisher.publishResource(practitionerRole, practitionerRole, ChangeType.NEW)

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
                resourceVersionId = null,
                tenantId = null,
                patientId = "tenant-1234",
                data = eventPatient,
            )

        val publishException = IllegalStateException("FAILURE")
        every { kafkaClient.publishEvents(patientTopic, listOf(kafkaEvent)) } returns
            PushResponse(
                failures = listOf(Failure(kafkaEvent, publishException)),
            )

        val exception =
            assertThrows<IllegalStateException> {
                publisher.publishResource(
                    patient,
                    patient,
                    ChangeType.CHANGED,
                )
            }
        assertEquals(publishException, exception)

        verify(exactly = 1) { kafkaClient.publishEvents(patientTopic, listOf(kafkaEvent)) }
    }

    @Test
    fun `includes resource version id when found`() {
        val eventPatient =
            EventPatient().apply {
                resourceType = "Patient"
                id = "tenant-1234"
            }

        val kafkaEvent =
            KafkaEvent(
                domain = "system",
                resource = "patient",
                action = KafkaAction.CREATE,
                resourceId = "tenant-1234",
                resourceVersionId = 204,
                tenantId = null,
                patientId = "tenant-1234",
                data = eventPatient,
            )

        every { kafkaClient.publishEvents(patientTopic, listOf(kafkaEvent)) } returns
            PushResponse(
                successful =
                    listOf(
                        kafkaEvent,
                    ),
            )

        val aidboxPatient =
            Patient(
                id = Id("tenant-1234"),
                meta = Meta(versionId = Id("204")),
            )
        publisher.publishResource(patient, aidboxPatient, ChangeType.NEW)

        verify(exactly = 1) { kafkaClient.publishEvents(patientTopic, listOf(kafkaEvent)) }
    }

    @Test
    fun `includes tenant id when found`() {
        val eventPatient =
            EventPatient().apply {
                resourceType = "Patient"
                id = "tenant-1234"
                identifier =
                    listOf(
                        EventIdentifier().apply {
                            system = "http://projectronin.com/id/tenantId"
                            value = "tenant"
                        },
                    )
            }

        val kafkaEvent =
            KafkaEvent(
                domain = "system",
                resource = "patient",
                action = KafkaAction.CREATE,
                resourceId = "tenant-1234",
                resourceVersionId = null,
                tenantId = "tenant",
                patientId = "tenant-1234",
                data = eventPatient,
            )

        every { kafkaClient.publishEvents(patientTopic, listOf(kafkaEvent)) } returns
            PushResponse(
                successful =
                    listOf(
                        kafkaEvent,
                    ),
            )

        val patient =
            Patient(
                id = Id("tenant-1234"),
                identifier = listOf(Identifier(system = CodeSystem.RONIN_TENANT.uri, value = "tenant".asFHIR())),
            )
        publisher.publishResource(patient, patient, ChangeType.NEW)

        verify(exactly = 1) { kafkaClient.publishEvents(patientTopic, listOf(kafkaEvent)) }
    }

    @Test
    fun `findPatientIdIn handles unknown location`() {
        val location = Location()
        val patientId = location.findPatientIdIn("patient")
        assertNull(patientId)
    }

    @Test
    fun `findPatientIdIn handles known location with null value`() {
        val location = Location(id = null)
        val patientId = location.findPatientIdIn("id")
        assertNull(patientId)
    }

    @Test
    fun `findPatientIdIn handles location that is not a reference`() {
        val location = Location(id = Id("1234"))
        val patientId = location.findPatientIdIn("id")
        assertNull(patientId)
    }

    @Test
    fun `findPatientIdIn handles location that has a Patient reference`() {
        val condition = Condition(subject = Reference(reference = FHIRString("Patient/1234")))
        val patientId = condition.findPatientIdIn("subject")
        assertEquals("1234", patientId)
    }

    @Test
    fun `findPatientIdIn handles location that has a non-Patient reference`() {
        val condition = Condition(subject = Reference(reference = FHIRString("Group/1234")))
        val patientId = condition.findPatientIdIn("subject")
        assertNull(patientId)
    }

    @Test
    fun `findPatientId handles Patient`() {
        val patient = Patient(id = Id("1234"))
        val patientId = patient.findPatientId()
        assertEquals("1234", patientId)
    }

    @Test
    fun `findPatientId handles resource with no appointment participants`() {
        val appointment =
            Appointment(
                status = AppointmentStatus.BOOKED.asCode(),
                participant = listOf(),
            )
        val patientId = appointment.findPatientId()
        assertNull(patientId)
    }

    @Test
    fun `findPatientId handles resource with non-patient appointment participants`() {
        val appointment =
            Appointment(
                status = AppointmentStatus.BOOKED.asCode(),
                participant =
                    listOf(
                        Participant(
                            status = ParticipationStatus.ACCEPTED.asCode(),
                            actor = Reference(reference = FHIRString("Practitioner/1234")),
                        ),
                        Participant(
                            status = ParticipationStatus.ACCEPTED.asCode(),
                            actor = Reference(reference = FHIRString("Location/5678")),
                        ),
                    ),
            )
        val patientId = appointment.findPatientId()
        assertNull(patientId)
    }

    @Test
    fun `findPatientId handles resource with single patient appointment participant`() {
        val appointment =
            Appointment(
                status = AppointmentStatus.BOOKED.asCode(),
                participant =
                    listOf(
                        Participant(
                            status = ParticipationStatus.ACCEPTED.asCode(),
                            actor = Reference(reference = FHIRString("Practitioner/1234")),
                        ),
                        Participant(
                            status = ParticipationStatus.ACCEPTED.asCode(),
                            actor = Reference(reference = FHIRString("Patient/5678")),
                        ),
                    ),
            )
        val patientId = appointment.findPatientId()
        assertEquals("5678", patientId)
    }

    @Test
    fun `findPatientId handles resource with multiple patient appointment participants`() {
        val appointment =
            Appointment(
                status = AppointmentStatus.BOOKED.asCode(),
                participant =
                    listOf(
                        Participant(
                            status = ParticipationStatus.ACCEPTED.asCode(),
                            actor = Reference(reference = FHIRString("Patient/1234")),
                        ),
                        Participant(
                            status = ParticipationStatus.ACCEPTED.asCode(),
                            actor = Reference(reference = FHIRString("Patient/5678")),
                        ),
                    ),
            )
        val patientId = appointment.findPatientId()
        assertNull(patientId)
    }

    @Test
    fun `findPatientId handles resource with patient in subject`() {
        val condition = Condition(subject = Reference(reference = FHIRString("Patient/1234")))
        val patientId = condition.findPatientId()
        assertEquals("1234", patientId)
    }

    @Test
    fun `findPatientId handles resource with patient in patient`() {
        // this is found in claims, which we haven't yet added support for yet, so creating an example
        data class Claim(
            override val id: Id? = null,
            override var meta: Meta? = null,
            override val implicitRules: Uri? = null,
            override val language: Code? = null,
            val patient: Reference? = null,
        ) : Resource<Condition> {
            override val resourceType: String = "Claim"
        }

        val claim = Claim(patient = Reference(reference = FHIRString("Patient/1357")))
        val patientId = claim.findPatientId()
        assertEquals("1357", patientId)
    }

    @Test
    fun `findPatientId handles resource with no patient`() {
        val location = Location()
        val patientId = location.findPatientId()
        assertNull(patientId)
    }
}
