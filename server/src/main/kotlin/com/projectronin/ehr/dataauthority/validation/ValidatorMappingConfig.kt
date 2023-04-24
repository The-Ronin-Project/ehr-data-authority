package com.projectronin.ehr.dataauthority.validation

import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.Encounter
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Medication
import com.projectronin.interop.fhir.r4.resource.MedicationRequest
import com.projectronin.interop.fhir.r4.resource.MedicationStatement
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.resource.PractitionerRole
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.fhir.ronin.resource.RoninEncounter
import com.projectronin.interop.fhir.ronin.resource.RoninLocation
import com.projectronin.interop.fhir.ronin.resource.RoninMedication
import com.projectronin.interop.fhir.ronin.resource.RoninMedicationRequest
import com.projectronin.interop.fhir.ronin.resource.RoninMedicationStatement
import com.projectronin.interop.fhir.ronin.resource.RoninObservations
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.fhir.ronin.resource.RoninPractitioner
import com.projectronin.interop.fhir.ronin.resource.RoninPractitionerRole
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ValidatorMappingConfig {
    @Bean
    fun appointmentValidator(roninAppointment: RoninAppointment) =
        ValidatorMapping(Appointment::class, roninAppointment)

    @Bean
    fun conditionValidator(roninConditions: RoninConditions) =
        ValidatorMapping(Condition::class, roninConditions)

    @Bean
    fun encounterValidator(roninEncounter: RoninEncounter) =
        ValidatorMapping(Encounter::class, roninEncounter)

    @Bean
    fun locationValidator(roninLocation: RoninLocation) =
        ValidatorMapping(Location::class, roninLocation)

    @Bean
    fun medicationValidator(roninMedication: RoninMedication) =
        ValidatorMapping(Medication::class, roninMedication)

    @Bean
    fun medicationRequestValidator(roninMedicationRequest: RoninMedicationRequest) =
        ValidatorMapping(MedicationRequest::class, roninMedicationRequest)

    @Bean
    fun medicationStatementValidator(roninMedicationStatement: RoninMedicationStatement) =
        ValidatorMapping(MedicationStatement::class, roninMedicationStatement)

    @Bean
    fun observationValidator(roninObservations: RoninObservations) =
        ValidatorMapping(Observation::class, roninObservations)

    @Bean
    fun patientValidator(roninPatient: RoninPatient) =
        ValidatorMapping(Patient::class, roninPatient)

    @Bean
    fun practitionerValidator(roninPractitioner: RoninPractitioner) =
        ValidatorMapping(Practitioner::class, roninPractitioner)

    @Bean
    fun practitionerRoleValidator(roninPractitionerRole: RoninPractitionerRole) =
        ValidatorMapping(PractitionerRole::class, roninPractitionerRole)
}