package com.projectronin.ehr.dataauthority.validation

import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.resource.PractitionerRole
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.fhir.ronin.resource.RoninLocation
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.fhir.ronin.resource.RoninPractitioner
import com.projectronin.interop.fhir.ronin.resource.RoninPractitionerRole
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ValidatorMappingConfigTest {
    @Test
    fun `creates appointment mapping`() {
        val validator = mockk<RoninAppointment>()
        val mapping = ValidatorMappingConfig().appointmentValidator(validator)
        assertEquals(Appointment::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates condition mapping`() {
        val validator = mockk<RoninConditions>()
        val mapping = ValidatorMappingConfig().conditionValidator(validator)
        assertEquals(Condition::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates location mapping`() {
        val validator = mockk<RoninLocation>()
        val mapping = ValidatorMappingConfig().locationValidator(validator)
        assertEquals(Location::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates patient mapping`() {
        val validator = mockk<RoninPatient>()
        val mapping = ValidatorMappingConfig().patientValidator(validator)
        assertEquals(Patient::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates practitioner mapping`() {
        val validator = mockk<RoninPractitioner>()
        val mapping = ValidatorMappingConfig().practitionerValidator(validator)
        assertEquals(Practitioner::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates practitioner role mapping`() {
        val validator = mockk<RoninPractitionerRole>()
        val mapping = ValidatorMappingConfig().practitionerRoleValidator(validator)
        assertEquals(PractitionerRole::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }
}
