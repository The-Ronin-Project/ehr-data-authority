package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.change.ChangeDetectionService
import com.projectronin.ehr.dataauthority.extensions.resource.toKey
import com.projectronin.ehr.dataauthority.models.BatchResourceChangeResponse
import com.projectronin.ehr.dataauthority.models.ChangeStatusResource
import com.projectronin.ehr.dataauthority.util.ResourceTenantMismatchUtil
import com.projectronin.interop.fhir.r4.resource.Resource
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ResourcesChangeController(
    private val changeDetectionService: ChangeDetectionService
) {

    /**
     * Takes a list of [resources] and determines if those have changed
     * from what is already stored
     */
    @PostMapping("/tenants/{tenantId}/resources/changeStatus")
    @PreAuthorize("hasAuthority('SCOPE_search:resources')")
    fun determineIfResourcesChanged(
        @PathVariable("tenantId") tenantId: String,
        @RequestBody resources: List<Resource<*>>
    ): ResponseEntity<BatchResourceChangeResponse> {
        val failedResources = ResourceTenantMismatchUtil.getMismatchResourceFailures(resources, tenantId)
        if (failedResources.isNotEmpty()) {
            return ResponseEntity.badRequest().body(BatchResourceChangeResponse(emptyList(), failedResources))
        }

        val resourcesByKey = resources.associateBy { it.toKey() }
        val changeStatusesByKey = changeDetectionService.determineChangeStatuses(tenantId, resourcesByKey)
        return ResponseEntity.ok(
            BatchResourceChangeResponse(
                resourcesByKey.map { (key, resource) ->
                    ChangeStatusResource(
                        resource.resourceType,
                        resource.id!!.value!!,
                        changeStatusesByKey[key]!!.type
                    )
                }
            )
        )
    }
}
