package com.projectronin.ehr.dataauthority.util

import com.projectronin.ehr.dataauthority.models.FailedResource
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.resource.Resource
import kotlin.reflect.full.memberProperties

class ResourceTenantMismatchUtil {
    companion object {
        fun getMismatchResourceFailures(
            resources: List<Resource<*>>,
            tenantId: String,
        ): List<FailedResource> {
            return resources.mapNotNull { resource ->
                checkResourcesAgainstTenant(resource, tenantId)?.let {
                    FailedResource(
                        resource.resourceType,
                        resource.id!!.value!!,
                        it,
                    )
                }
            }
        }

        private fun checkResourcesAgainstTenant(
            resource: Resource<*>,
            tenantId: String,
        ): String? {
            if (resource.id?.value?.startsWith(tenantId) != true) {
                return "Resource ID does not match given tenant $tenantId"
            }
            val properties = resource.javaClass.kotlin.memberProperties
            val identifierProperty =
                properties.singleOrNull {
                    it.name == "identifier"
                } ?: return null
            // the null here is a weird case, where we have a property called 'identifier'
            // but it's not a list of identifier classes, something like a bundle would get through
            val identifiers = identifierProperty.get(resource) as? List<Identifier> ?: return null
            val tenantIdentifier = identifiers.singleOrNull { it.system == CodeSystem.RONIN_TENANT.uri }
            if (tenantIdentifier == null || tenantIdentifier.value?.value != tenantId) {
                return "Resource does not contain a tenant identifier for $tenantId"
            }
            return null
        }
    }
}
