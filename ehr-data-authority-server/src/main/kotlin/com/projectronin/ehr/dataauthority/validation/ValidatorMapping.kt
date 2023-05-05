package com.projectronin.ehr.dataauthority.validation

import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.fhir.validate.ProfileValidator
import kotlin.reflect.KClass

data class ValidatorMapping<T : Resource<T>>(
    val resourceClass: KClass<in T>,
    val validator: ProfileValidator<out T>
)
