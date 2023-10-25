package com.projectronin.ehr.dataauthority.validation

import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.CarePlan
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.DiagnosticReport
import com.projectronin.interop.fhir.r4.resource.DocumentReference
import com.projectronin.interop.fhir.r4.resource.Encounter
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Medication
import com.projectronin.interop.fhir.r4.resource.MedicationAdministration
import com.projectronin.interop.fhir.r4.resource.MedicationRequest
import com.projectronin.interop.fhir.r4.resource.MedicationStatement
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.resource.PractitionerRole
import com.projectronin.interop.fhir.r4.resource.Procedure
import com.projectronin.interop.fhir.r4.resource.RequestGroup
import com.projectronin.interop.fhir.r4.resource.ServiceRequest
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.fhir.ronin.resource.RoninCarePlan
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.fhir.ronin.resource.RoninDiagnosticReports
import com.projectronin.interop.fhir.ronin.resource.RoninDocumentReference
import com.projectronin.interop.fhir.ronin.resource.RoninEncounter
import com.projectronin.interop.fhir.ronin.resource.RoninLocation
import com.projectronin.interop.fhir.ronin.resource.RoninMedication
import com.projectronin.interop.fhir.ronin.resource.RoninMedicationAdministration
import com.projectronin.interop.fhir.ronin.resource.RoninMedicationRequest
import com.projectronin.interop.fhir.ronin.resource.RoninMedicationStatement
import com.projectronin.interop.fhir.ronin.resource.RoninObservations
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.fhir.ronin.resource.RoninPractitioner
import com.projectronin.interop.fhir.ronin.resource.RoninPractitionerRole
import com.projectronin.interop.fhir.ronin.resource.RoninProcedure
import com.projectronin.interop.fhir.ronin.resource.RoninRequestGroup
import com.projectronin.interop.fhir.ronin.resource.RoninServiceRequest
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

    @Test
    fun `creates encounter mapping`() {
        val validator = mockk<RoninEncounter>()
        val mapping = ValidatorMappingConfig().encounterValidator(validator)
        assertEquals(Encounter::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates medication mapping`() {
        val validator = mockk<RoninMedication>()
        val mapping = ValidatorMappingConfig().medicationValidator(validator)
        assertEquals(Medication::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates medication request mapping`() {
        val validator = mockk<RoninMedicationRequest>()
        val mapping = ValidatorMappingConfig().medicationRequestValidator(validator)
        assertEquals(MedicationRequest::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates medication statement mapping`() {
        val validator = mockk<RoninMedicationStatement>()
        val mapping = ValidatorMappingConfig().medicationStatementValidator(validator)
        assertEquals(MedicationStatement::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates observation mapping`() {
        val validator = mockk<RoninObservations>()
        val mapping = ValidatorMappingConfig().observationValidator(validator)
        assertEquals(Observation::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates request group mapping`() {
        val validator = mockk<RoninRequestGroup>()
        val mapping = ValidatorMappingConfig().requestGroupValidator(validator)
        assertEquals(RequestGroup::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates care plan mapping`() {
        val validator = mockk<RoninCarePlan>()
        val mapping = ValidatorMappingConfig().carePlanValidator(validator)
        assertEquals(CarePlan::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates document reference mapping`() {
        val validator = mockk<RoninDocumentReference>()
        val mapping = ValidatorMappingConfig().documentReferenceValidator(validator)
        assertEquals(DocumentReference::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates medication administration mapping`() {
        val validator = mockk<RoninMedicationAdministration>()
        val mapping = ValidatorMappingConfig().medicationAdministration(validator)
        assertEquals(MedicationAdministration::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates service request mapping`() {
        val validator = mockk<RoninServiceRequest>()
        val mapping = ValidatorMappingConfig().serviceRequestValidator(validator)
        assertEquals(ServiceRequest::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates diagnostic report mapping`() {
        val validator = mockk<RoninDiagnosticReports>()
        val mapping = ValidatorMappingConfig().diagnosticReportValidator(validator)
        assertEquals(DiagnosticReport::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }

    @Test
    fun `creates procedure mapping`() {
        val validator = mockk<RoninProcedure>()
        val mapping = ValidatorMappingConfig().procedureValidator(validator)
        assertEquals(Procedure::class, mapping.resourceClass)
        assertEquals(validator, mapping.validator)
    }
}
