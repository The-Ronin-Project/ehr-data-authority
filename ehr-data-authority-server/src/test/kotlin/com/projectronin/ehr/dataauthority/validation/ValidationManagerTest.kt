package com.projectronin.ehr.dataauthority.validation

import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.resource.RoninLocation
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.fhir.ronin.validation.ValidationClient
import com.projectronin.interop.fhir.validate.Validation
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ValidationManagerTest {
    private val validationClient = mockk<ValidationClient>()
    private val patientValidator = mockk<RoninPatient>()
    private val patientMapping = ValidatorMapping(Patient::class, patientValidator)
    private val locationValidator = mockk<RoninLocation>()
    private val locationMapping = ValidatorMapping(Location::class, locationValidator)

    private val manager = ValidationManager(validationClient, listOf(patientMapping, locationMapping))

    @Test
    fun `no validator found for resource`() {
        val appointment = mockk<Appointment>()

        val response = manager.validateResource(appointment, "test")
        assertTrue(response is FailedValidation)
        assertEquals("No validator found for resource Appointment", (response as FailedValidation).failureMessage)

        verify { validationClient wasNot Called }
    }

    @Test
    fun `validation has non-error issues`() {
        val validation =
            mockk<Validation> {
                every { hasIssues() } returns true
                every { hasErrors() } returns false
                every { issues() } returns emptyList()
            }

        val patient = mockk<Patient>()
        every { patientValidator.validate(patient) } returns validation
        every { validationClient.reportIssues(validation, patient, "test") } returns UUID.randomUUID()

        val response = manager.validateResource(patient, "test")
        assertTrue(response is PassedValidation)

        verify { validationClient.reportIssues(validation, patient, "test") }
    }

    @Test
    fun `validation has errors`() {
        val validation =
            mockk<Validation> {
                every { hasIssues() } returns true
                every { hasErrors() } returns true
                every { issues() } returns emptyList()
                every { getErrorString() } returns "the errors"
            }

        val patient = mockk<Patient>()
        every { patient.resourceType } returns "Patient"
        every { patient.id } returns Id("tenant1-12345fake")
        every { patientValidator.validate(patient) } returns validation
        every { validationClient.reportIssues(validation, patient, "test") } returns UUID.randomUUID()

        val response = manager.validateResource(patient, "test")
        assertTrue(response is FailedValidation)
        assertEquals("the errors", (response as FailedValidation).failureMessage)

        verify { validationClient.reportIssues(validation, patient, "test") }
    }

    @Test
    fun `validation has no issues`() {
        val validation =
            mockk<Validation> {
                every { hasIssues() } returns false
                every { hasErrors() } returns false
                every { issues() } returns emptyList()
            }

        val location = mockk<Location>()
        every { location.resourceType } returns "Location"
        every { location.id } returns Id("tenant1-12345fake")
        every { locationValidator.validate(location) } returns validation

        val response = manager.validateResource(location, "test")
        assertTrue(response is PassedValidation)

        verify { validationClient wasNot Called }
    }
}
