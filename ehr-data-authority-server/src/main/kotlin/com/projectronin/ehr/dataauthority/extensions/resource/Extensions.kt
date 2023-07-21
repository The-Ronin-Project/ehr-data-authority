package com.projectronin.ehr.dataauthority.extensions.resource

import com.projectronin.interop.fhir.r4.resource.Resource

fun Resource<*>.toKey() = "$resourceType:${id!!.value}"
