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
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.rcdm.validate.FailedValidation
import com.projectronin.interop.rcdm.validate.PassedValidation
import com.projectronin.interop.rcdm.validate.ValidationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneOffset
import io.swagger.v3.oas.annotations.parameters.RequestBody as RequestBodyParam

@RestController
class ResourcesWriteController(
    private val resourceHashDao: ResourceHashDAOService,
    private val changeDetectionService: ChangeDetectionService,
    private val kafkaPublisher: KafkaPublisher,
    private val validationService: ValidationService,
    private val publishService: PublishService,
    private val storageMode: StorageMode,
) {
    private val logger = KotlinLogging.logger { }
    private val isLocal = storageMode == StorageMode.LOCAL

    @Operation(
        summary = "Takes in a list of resources, and posts them",
        description =
            "Takes a list of resources and determines if the resource is new or has been modified, " +
                "it is published to the Ronin clinical data store.",
    )
    @Parameter(name = "tenantId", example = "tenant")
    @RequestBodyParam(description = "Complete FHIR Resource")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns a list of resources there successfully added and a list of failed/not found resources",
            ),
        ],
    )
    @PostMapping("/tenants/{tenantId}/resources")
    @PreAuthorize("hasAuthority('SCOPE_write:resources')")
    fun addNewResources(
        @PathVariable("tenantId") tenantId: String,
        @RequestBody resources: List<Resource<*>>,
    ): ResponseEntity<BatchResourceResponse> {
        val failedResources = ResourceTenantMismatchUtil.getMismatchResourceFailures(resources, tenantId)
        if (failedResources.isNotEmpty()) {
            return ResponseEntity.badRequest().body(BatchResourceResponse(emptyList(), failedResources))
        }

        val resourcesByKey = resources.associateBy { it.toKey() }
        val changeStatusesByKey = changeDetectionService.determineChangeStatuses(tenantId, resourcesByKey)

        val responses =
            resourcesByKey.map { (key, resource) ->
                val changeStatus = changeStatusesByKey[key]!!
                when (changeStatus.type) {
                    ChangeType.UNCHANGED ->
                        SucceededResource(
                            resource.resourceType,
                            resource.id!!.value!!,
                            ModificationType.UNMODIFIED,
                        )

                    else -> {
                        if (isLocal) {
                            publishResource(resource, tenantId, changeStatus)
                        } else {
                            validateAndPublishResource(resource, tenantId, changeStatus)
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
        changeStatus: ChangeStatus,
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
                    "Failed to publish to Kafka: ${it.localizedMessage}",
                )
            }
        }

        val resourceHashesDO = changeStatus.toHashDO(tenantId)
        val modificationType =
            when (changeStatus.type) {
                ChangeType.NEW -> {
                    runCatching { resourceHashDao.upsertHash(resourceHashesDO) }.onFailure {
                        logger.error(it) { "Exception persisting new hash for $resourceHashesDO" }
                        return FailedResource(
                            resource.resourceType,
                            resource.id!!.value!!,
                            "Error updating the hash store",
                        )
                    }
                    ModificationType.CREATED
                }

                ChangeType.CHANGED -> {
                    runCatching { resourceHashDao.upsertHash(resourceHashesDO) }.onFailure {
                        logger.error(it) { "Exception persisting updated hash for $resourceHashesDO" }
                        return FailedResource(
                            resource.resourceType,
                            resource.id!!.value!!,
                            "Error updating the hash store",
                        )
                    }
                    ModificationType.UPDATED
                }

                else -> throw IllegalStateException("Only new or changed statuses are allowed to be published")
            }

        return SucceededResource(resource.resourceType, resource.id!!.value!!, modificationType)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R : Resource<R>> validateAndPublishResource(
        resource: Resource<R>,
        tenantId: String,
        changeStatus: ChangeStatus,
    ): ResourceResponse {
        val validation =
            runCatching { validationService.validate(resource as R, tenantId) }.fold(
                onSuccess = { it },
                onFailure = { exception ->
                    return FailedResource(
                        resource.resourceType,
                        resource.id!!.value!!,
                        exception.localizedMessage,
                    )
                },
            )

        return when (validation) {
            is PassedValidation -> publishResource(resource, tenantId, changeStatus)
            is FailedValidation ->
                FailedResource(
                    resource.resourceType,
                    resource.id!!.value!!,
                    validation.failureMessage,
                )
        }
    }

    private fun ChangeStatus.toHashDO(tenant: String) =
        ResourceHashesDO {
            hashId = this@toHashDO.hashId
            resourceId = this@toHashDO.resourceId
            resourceType = this@toHashDO.resourceType
            tenantId = tenant
            hash = this@toHashDO.hash
            updateDateTime = OffsetDateTime.now(ZoneOffset.UTC)
        }
}
