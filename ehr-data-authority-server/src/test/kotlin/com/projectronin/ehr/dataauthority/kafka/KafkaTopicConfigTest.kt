package com.projectronin.ehr.dataauthority.kafka

import com.projectronin.interop.kafka.spring.KafkaConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KafkaTopicConfigTest {
    private val vendor = "oci"
    private val region = "us-phoenix-1"
    private val system = "ehr-data-authority"

    private val kafkaConfig = mockk<KafkaConfig> {
        every { cloud.vendor } returns vendor
        every { cloud.region } returns region
        every { retrieve.serviceId } returns system
    }

    private val kafkaTopicConfig = KafkaTopicConfig(kafkaConfig)

    @Test
    fun `creates appointment topic`() {
        val topic = kafkaTopicConfig.appointmentTopic()

        assertEquals(system, topic.systemName)
        assertEquals("oci.us-phoenix-1.ehr-data-authority.appointment.v1", topic.topicName)
        assertEquals(
            "https://github.com/projectronin/ronin-fhir-models/blob/main/common-fhir-r4-models/v1/Appointment-v1.schema.json",
            topic.dataSchema
        )
        assertEquals(com.projectronin.interop.fhir.r4.resource.Appointment::class, topic.resourceClass)
        assertEquals(com.projectronin.fhir.r4.Appointment::class, topic.eventClass)
    }

    @Test
    fun `creates condition topic`() {
        val topic = kafkaTopicConfig.conditionTopic()

        assertEquals(system, topic.systemName)
        assertEquals("oci.us-phoenix-1.ehr-data-authority.condition.v1", topic.topicName)
        assertEquals(
            "https://github.com/projectronin/ronin-fhir-models/blob/main/common-fhir-r4-models/v1/Condition-v1.schema.json",
            topic.dataSchema
        )
        assertEquals(com.projectronin.interop.fhir.r4.resource.Condition::class, topic.resourceClass)
        assertEquals(com.projectronin.fhir.r4.Condition::class, topic.eventClass)
    }

    @Test
    fun `creates location topic`() {
        val topic = kafkaTopicConfig.locationTopic()

        assertEquals(system, topic.systemName)
        assertEquals("oci.us-phoenix-1.ehr-data-authority.location.v1", topic.topicName)
        assertEquals(
            "https://github.com/projectronin/ronin-fhir-models/blob/main/common-fhir-r4-models/v1/Location-v1.schema.json",
            topic.dataSchema
        )
        assertEquals(com.projectronin.interop.fhir.r4.resource.Location::class, topic.resourceClass)
        assertEquals(com.projectronin.fhir.r4.Location::class, topic.eventClass)
    }

    @Test
    fun `creates patient topic`() {
        val topic = kafkaTopicConfig.patientTopic()

        assertEquals(system, topic.systemName)
        assertEquals("oci.us-phoenix-1.ehr-data-authority.patient.v1", topic.topicName)
        assertEquals(
            "https://github.com/projectronin/ronin-fhir-models/blob/main/common-fhir-r4-models/v1/Patient-v1.schema.json",
            topic.dataSchema
        )
        assertEquals(com.projectronin.interop.fhir.r4.resource.Patient::class, topic.resourceClass)
        assertEquals(com.projectronin.fhir.r4.Patient::class, topic.eventClass)
    }

    @Test
    fun `creates practitioner topic`() {
        val topic = kafkaTopicConfig.practitionerTopic()

        assertEquals(system, topic.systemName)
        assertEquals("oci.us-phoenix-1.ehr-data-authority.practitioner.v1", topic.topicName)
        assertEquals(
            "https://github.com/projectronin/ronin-fhir-models/blob/main/common-fhir-r4-models/v1/Practitioner-v1.schema.json",
            topic.dataSchema
        )
        assertEquals(com.projectronin.interop.fhir.r4.resource.Practitioner::class, topic.resourceClass)
        assertEquals(com.projectronin.fhir.r4.Practitioner::class, topic.eventClass)
    }

    @Test
    fun `creates practitioner role topic`() {
        val topic = kafkaTopicConfig.practitionerRoleTopic()

        assertEquals(system, topic.systemName)
        assertEquals("oci.us-phoenix-1.ehr-data-authority.practitioner-role.v1", topic.topicName)
        assertEquals(
            "https://github.com/projectronin/ronin-fhir-models/blob/main/common-fhir-r4-models/v1/PractitionerRole-v1.schema.json",
            topic.dataSchema
        )
        assertEquals(com.projectronin.interop.fhir.r4.resource.PractitionerRole::class, topic.resourceClass)
        assertEquals(com.projectronin.fhir.r4.PractitionerRole::class, topic.eventClass)
    }

    @Test
    fun `creates encounter topic`() {
        val topic = kafkaTopicConfig.encounterTopic()

        assertEquals(system, topic.systemName)
        assertEquals("oci.us-phoenix-1.ehr-data-authority.encounter.v1", topic.topicName)
        assertEquals(
            "https://github.com/projectronin/ronin-fhir-models/blob/main/common-fhir-r4-models/v1/Encounter-v1.schema.json",
            topic.dataSchema
        )
        assertEquals(com.projectronin.interop.fhir.r4.resource.Encounter::class, topic.resourceClass)
        assertEquals(com.projectronin.fhir.r4.Encounter::class, topic.eventClass)
    }

    @Test
    fun `creates medication topic`() {
        val topic = kafkaTopicConfig.medicationTopic()

        assertEquals(system, topic.systemName)
        assertEquals("oci.us-phoenix-1.ehr-data-authority.medication.v1", topic.topicName)
        assertEquals(
            "https://github.com/projectronin/ronin-fhir-models/blob/main/common-fhir-r4-models/v1/Medication-v1.schema.json",
            topic.dataSchema
        )
        assertEquals(com.projectronin.interop.fhir.r4.resource.Medication::class, topic.resourceClass)
        assertEquals(com.projectronin.fhir.r4.Medication::class, topic.eventClass)
    }

    @Test
    fun `creates medication request topic`() {
        val topic = kafkaTopicConfig.medicationRequestTopic()

        assertEquals(system, topic.systemName)
        assertEquals("oci.us-phoenix-1.ehr-data-authority.medication-request.v1", topic.topicName)
        assertEquals(
            "https://github.com/projectronin/ronin-fhir-models/blob/main/common-fhir-r4-models/v1/MedicationRequest-v1.schema.json",
            topic.dataSchema
        )
        assertEquals(com.projectronin.interop.fhir.r4.resource.MedicationRequest::class, topic.resourceClass)
        assertEquals(com.projectronin.fhir.r4.MedicationRequest::class, topic.eventClass)
    }

    @Test
    fun `creates medication statement topic`() {
        val topic = kafkaTopicConfig.medicationStatementTopic()

        assertEquals(system, topic.systemName)
        assertEquals("oci.us-phoenix-1.ehr-data-authority.medication-statement.v1", topic.topicName)
        assertEquals(
            "https://github.com/projectronin/ronin-fhir-models/blob/main/common-fhir-r4-models/v1/MedicationStatement-v1.schema.json",
            topic.dataSchema
        )
        assertEquals(com.projectronin.interop.fhir.r4.resource.MedicationStatement::class, topic.resourceClass)
        assertEquals(com.projectronin.fhir.r4.MedicationStatement::class, topic.eventClass)
    }

    @Test
    fun `creates observation topic`() {
        val topic = kafkaTopicConfig.observationTopic()

        assertEquals(system, topic.systemName)
        assertEquals("oci.us-phoenix-1.ehr-data-authority.observation.v1", topic.topicName)
        assertEquals(
            "https://github.com/projectronin/ronin-fhir-models/blob/main/common-fhir-r4-models/v1/Observation-v1.schema.json",
            topic.dataSchema
        )
        assertEquals(com.projectronin.interop.fhir.r4.resource.Observation::class, topic.resourceClass)
        assertEquals(com.projectronin.fhir.r4.Observation::class, topic.eventClass)
    }

    @Test
    fun `creates request group topic`() {
        val topic = kafkaTopicConfig.requestGroupTopic()

        assertEquals(system, topic.systemName)
        assertEquals("oci.us-phoenix-1.ehr-data-authority.request-group.v1", topic.topicName)
        assertEquals(
            "https://github.com/projectronin/ronin-fhir-models/blob/main/common-fhir-r4-models/v1/RequestGroup-v1.schema.json",
            topic.dataSchema
        )
        assertEquals(com.projectronin.interop.fhir.r4.resource.RequestGroup::class, topic.resourceClass)
        assertEquals(com.projectronin.fhir.r4.RequestGroup::class, topic.eventClass)
    }
}
