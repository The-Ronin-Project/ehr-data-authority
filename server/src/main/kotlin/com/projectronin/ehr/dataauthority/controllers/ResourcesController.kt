package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.change.ChangeDetectionService
import com.projectronin.ehr.dataauthority.change.data.ResourceHashesDAO
import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import com.projectronin.ehr.dataauthority.change.model.ChangeStatus
import com.projectronin.ehr.dataauthority.change.model.ChangeType
import com.projectronin.ehr.dataauthority.model.BatchResourceResponse
import com.projectronin.ehr.dataauthority.model.FailedResource
import com.projectronin.ehr.dataauthority.model.ModificationType
import com.projectronin.ehr.dataauthority.model.ResourceResponse
import com.projectronin.ehr.dataauthority.model.SucceededResource
import com.projectronin.interop.aidbox.AidboxPublishService
import com.projectronin.interop.fhir.r4.resource.Resource
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RestController
class ResourcesController(
    private val aidboxPublishService: AidboxPublishService,
    private val changeDetectionService: ChangeDetectionService,
    private val resourceHashesDAO: ResourceHashesDAO
) {
    @PostMapping("/tenants/{tenantId}/resources")
    @PreAuthorize("hasAuthority('SCOPE_write:resources')")
    fun addNewResources(
        @PathVariable("tenantId") tenantId: String,
        @RequestBody resources: List<Resource<*>>
    ): ResponseEntity<BatchResourceResponse> {
        val resourcesByKey = resources.associateBy { it.toKey() }
        val changeStatusesByKey = changeDetectionService.determineChangeStatuses(tenantId, resourcesByKey)

        val responses = resourcesByKey.map { (key, resource) ->
            val changeStatus = changeStatusesByKey[key]!!
            when (changeStatus.type) {
                ChangeType.UNCHANGED -> SucceededResource(
                    resource.resourceType,
                    resource.id!!.value!!,
                    ModificationType.UNMODIFIED
                )

                else -> publishResource(resource, tenantId, changeStatus)
            }
        }

        val succeeded = responses.filterIsInstance<SucceededResource>()
        val failed = responses.filterIsInstance<FailedResource>()
        return ResponseEntity.ok(BatchResourceResponse(succeeded, failed))
    }

    private fun publishResource(
        resource: Resource<*>,
        tenantId: String,
        changeStatus: ChangeStatus
    ): ResourceResponse {
        val published = aidboxPublishService.publish(listOf(resource))
        if (!published) {
            return FailedResource(resource.resourceType, resource.id!!.value!!, "Error publishing to data store.")
        }

        val modificationType = when (changeStatus.type) {
            ChangeType.NEW -> {
                resourceHashesDAO.insertHash(changeStatus.toHashDO(tenantId))
                ModificationType.CREATED
            }

            ChangeType.CHANGED -> {
                resourceHashesDAO.updateHash(changeStatus.hashId!!, changeStatus.hash)
                ModificationType.UPDATED
            }

            else -> throw IllegalStateException("Only new or changed statuses are allowed to be published")
        }

        return SucceededResource(resource.resourceType, resource.id!!.value!!, modificationType)
    }

    fun Resource<*>.toKey() = "$resourceType:${id!!.value}"

    fun ChangeStatus.toHashDO(tenant: String) =
        ResourceHashesDO {
            resourceId = this@toHashDO.resourceId
            resourceType = this@toHashDO.resourceType
            tenantId = tenant
            hash = this@toHashDO.hash
            updateDateTime = OffsetDateTime.now(ZoneOffset.UTC)
        }
}
