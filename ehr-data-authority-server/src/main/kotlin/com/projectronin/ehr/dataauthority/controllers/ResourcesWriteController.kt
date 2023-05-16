package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.change.ChangeDetectionService
import com.projectronin.ehr.dataauthority.change.data.ResourceHashesDAO
import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import com.projectronin.ehr.dataauthority.change.model.ChangeStatus
import com.projectronin.ehr.dataauthority.change.model.ChangeType
import com.projectronin.ehr.dataauthority.kafka.KafkaPublisher
import com.projectronin.ehr.dataauthority.model.BatchResourceResponse
import com.projectronin.ehr.dataauthority.model.FailedResource
import com.projectronin.ehr.dataauthority.model.ModificationType
import com.projectronin.ehr.dataauthority.model.ResourceResponse
import com.projectronin.ehr.dataauthority.model.SucceededResource
import com.projectronin.ehr.dataauthority.validation.FailedValidation
import com.projectronin.ehr.dataauthority.validation.PassedValidation
import com.projectronin.ehr.dataauthority.validation.ValidationManager
import com.projectronin.interop.aidbox.AidboxPublishService
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.resource.Resource
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.reflect.full.memberProperties

@RestController
class ResourcesWriteController(
    private val aidboxPublishService: AidboxPublishService,
    private val changeDetectionService: ChangeDetectionService,
    private val resourceHashesDAO: ResourceHashesDAO,
    private val kafkaPublisher: KafkaPublisher,
    private val validationManager: ValidationManager
) {
    @PostMapping("/tenants/{tenantId}/resources")
    @PreAuthorize("hasAuthority('SCOPE_write:resources')")
    fun addNewResources(
        @PathVariable("tenantId") tenantId: String,
        @RequestBody resources: List<Resource<*>>
    ): ResponseEntity<BatchResourceResponse> {
        val mismatches = getResourceTenantMismatches(resources, tenantId)
        if (mismatches.isNotEmpty()) {
            val failedResources = mismatches.map {
                FailedResource(
                    it.key.resourceType,
                    it.key.id!!.value!!,
                    it.value
                )
            }
            return ResponseEntity.badRequest().body(BatchResourceResponse(emptyList(), failedResources))
        }
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

                else -> {
                    val validation = validationManager.validateResource(resource, tenantId)
                    when (validation) {
                        is PassedValidation -> publishResource(resource, tenantId, changeStatus)
                        is FailedValidation -> FailedResource(
                            resource.resourceType,
                            resource.id!!.value!!,
                            validation.failureMessage
                        )
                    }
                }
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

        runCatching { kafkaPublisher.publishResource(resource, changeStatus.type) }.exceptionOrNull()?.let {
            return FailedResource(
                resource.resourceType,
                resource.id!!.value!!,
                "Failed to publish to Kafka: ${it.localizedMessage}"
            )
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

    private fun getResourceTenantMismatches(resources: List<Resource<*>>, tenantId: String): Map<Resource<*>, String> {
        return resources.mapNotNull { resource ->
            val error = checkResourcesAgainstTenant(resource, tenantId)
            if (error != null) {
                Pair(resource, error)
            } else {
                null
            }
        }.associate { it.first to it.second }
    }

    private fun checkResourcesAgainstTenant(resource: Resource<*>, tenantId: String): String? {
        if (resource.id?.value?.startsWith(tenantId) != true) {
            return "Resource ID does not match given tenant $tenantId"
        }
        val properties = resource.javaClass.kotlin.memberProperties
        val identifierProperty = properties.singleOrNull {
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
