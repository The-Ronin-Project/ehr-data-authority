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
    fun locationValidator(roninLocation: RoninLocation) =
        ValidatorMapping(Location::class, roninLocation)

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
