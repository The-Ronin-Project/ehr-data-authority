package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.change.ChangeDetectionService
import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import com.projectronin.ehr.dataauthority.change.data.services.ResourceHashDAOService
import com.projectronin.ehr.dataauthority.change.data.services.StorageMode
import com.projectronin.ehr.dataauthority.change.model.ChangeStatus
import com.projectronin.ehr.dataauthority.extensions.resource.toKey
import com.projectronin.ehr.dataauthority.kafka.KafkaPublisher
import com.projectronin.ehr.dataauthority.models.BatchResourceResponse
import com.projectronin.ehr.dataauthority.models.ChangeType
import com.projectronin.ehr.dataauthority.models.FailedResource
import com.projectronin.ehr.dataauthority.models.ModificationType
import com.projectronin.ehr.dataauthority.models.ResourceResponse
import com.projectronin.ehr.dataauthority.models.SucceededResource
import com.projectronin.ehr.dataauthority.publish.PublishService
import com.projectronin.ehr.dataauthority.util.ResourceTenantMismatchUtil
import com.projectronin.ehr.dataauthority.validation.FailedValidation
import com.projectronin.ehr.dataauthority.validation.PassedValidation
import com.projectronin.ehr.dataauthority.validation.ValidationManager
import com.projectronin.interop.fhir.r4.resource.Resource
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RestController
class ResourcesWriteController(
    private val resourceHashDao: ResourceHashDAOService,
    private val changeDetectionService: ChangeDetectionService,
    private val kafkaPublisher: KafkaPublisher,
    private val validationManager: ValidationManager,
    private val publishService: PublishService,
    private val storageMode: StorageMode
) {
    private val logger = KotlinLogging.logger { }
    private val isLocal = storageMode == StorageMode.LOCAL

    @PostMapping("/tenants/{tenantId}/resources")
    @PreAuthorize("hasAuthority('SCOPE_write:resources')")
    fun addNewResources(
        @PathVariable("tenantId") tenantId: String,
        @RequestBody resources: List<Resource<*>>
    ): ResponseEntity<BatchResourceResponse> {
        val failedResources = ResourceTenantMismatchUtil.getMismatchResourceFailures(resources, tenantId)
        if (failedResources.isNotEmpty()) {
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
                    if (isLocal) {
                        publishResource(resource, tenantId, changeStatus)
                    } else {
                        when (val validation = validationManager.validateResource(resource, tenantId)) {
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
        val published = publishService.publish(listOf(resource))
        if (!published) {
            return FailedResource(resource.resourceType, resource.id!!.value!!, "Error publishing to data store.")
        }

        if (!isLocal) {
            runCatching { kafkaPublisher.publishResource(resource, changeStatus.type) }.exceptionOrNull()?.let {
                return FailedResource(
                    resource.resourceType,
                    resource.id!!.value!!,
                    "Failed to publish to Kafka: ${it.localizedMessage}"
                )
            }
        }

        val resourceHashesDO = changeStatus.toHashDO(tenantId)
        val modificationType = when (changeStatus.type) {
            ChangeType.NEW -> {
                runCatching { resourceHashDao.upsertHash(resourceHashesDO) }.onFailure {
                    logger.error(it) { "Exception persisting new hash for $resourceHashesDO" }
                    return FailedResource(resource.resourceType, resource.id!!.value!!, "Error updating the hash store")
                }
                ModificationType.CREATED
            }

            ChangeType.CHANGED -> {
                runCatching { resourceHashDao.upsertHash(resourceHashesDO) }.onFailure {
                    logger.error(it) { "Exception persisting updated hash for $resourceHashesDO" }
                    return FailedResource(resource.resourceType, resource.id!!.value!!, "Error updating the hash store")
                }
                ModificationType.UPDATED
            }

            else -> throw IllegalStateException("Only new or changed statuses are allowed to be published")
        }

        return SucceededResource(resource.resourceType, resource.id!!.value!!, modificationType)
    }

    fun ChangeStatus.toHashDO(tenant: String) =
        ResourceHashesDO {
            hashId = this@toHashDO.hashId
            resourceId = this@toHashDO.resourceId
            resourceType = this@toHashDO.resourceType
            tenantId = tenant
            hash = this@toHashDO.hash
            updateDateTime = OffsetDateTime.now(ZoneOffset.UTC)
        }
}
