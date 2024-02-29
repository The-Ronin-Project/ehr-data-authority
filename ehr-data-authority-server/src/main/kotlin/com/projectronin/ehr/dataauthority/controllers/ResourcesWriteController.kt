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
    storageMode: StorageMode,
) {
    private val logger = KotlinLogging.logger { }
    private val isLocal = storageMode == StorageMode.LOCAL
    private val emptyBatchResponse = BatchResourceResponse(emptyList(), emptyList())

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

        val response = processResources(resourcesByKey, changeStatusesByKey, tenantId)
        return ResponseEntity.ok(response)
    }

    private fun processResources(
        resourcesByKey: Map<String, Resource<*>>,
        changeStatusesByKey: Map<String, ChangeStatus>,
        tenantId: String,
    ): BatchResourceResponse {
        val (unchangedKeys, changedKeys) = changeStatusesByKey.entries.partition { it.value.type == ChangeType.UNCHANGED }
        val changedResources = changedKeys.associate { it.key to resourcesByKey[it.key]!! }

        val unchangedResponses =
            unchangedKeys.map { (key, _) ->
                val resource = resourcesByKey[key]!!
                SucceededResource(
                    resource.resourceType,
                    resource.id!!.value!!,
                    ModificationType.UNMODIFIED,
                )
            }

        val publishResponse =
            if (changedResources.isEmpty()) {
                emptyBatchResponse
            } else if (isLocal) {
                publishResources(changedResources, changeStatusesByKey, tenantId)
            } else {
                validateAndPublishResources(changedResources, changeStatusesByKey, tenantId)
            }

        return BatchResourceResponse(
            succeeded = publishResponse.succeeded + unchangedResponses,
            failed = publishResponse.failed,
        )
    }

    private fun publishResources(
        resourcesByKey: Map<String, Resource<*>>,
        changeStatusesByKey: Map<String, ChangeStatus>,
        tenantId: String,
    ): BatchResourceResponse {
        val publishedResponsesByKey = publishService.publish(resourcesByKey.values.toList()).associateBy { it.toKey() }

        val failedResponses = mutableListOf<FailedResource>()
        failedResponses.addAll(
            resourcesByKey.keys.minus(publishedResponsesByKey.keys).map {
                val resource = resourcesByKey[it]!!
                FailedResource(resource.resourceType, resource.id!!.value!!, "Error publishing to data store")
            },
        )

        val passedKafkaResources = mutableMapOf<String, Resource<*>>()
        if (isLocal) {
            passedKafkaResources.putAll(publishedResponsesByKey)
        } else {
            publishedResponsesByKey.forEach { key, aidboxResource ->
                val resource = resourcesByKey[key]!!
                val changeStatus = changeStatusesByKey[key]!!
                runCatching { kafkaPublisher.publishResource(resource, aidboxResource, changeStatus.type) }.fold(
                    onSuccess = { passedKafkaResources[key] = resource },
                    onFailure = {
                        failedResponses.add(
                            FailedResource(
                                resource.resourceType,
                                resource.id!!.value!!,
                                "Failed to publish to Kafka: ${it.localizedMessage}",
                            ),
                        )
                    },
                )
            }
        }

        val successfulResponses =
            passedKafkaResources.mapNotNull { (key, resource) ->
                val resourceType = resource.resourceType
                val resourceId = resource.id!!.value!!

                val changeStatus = changeStatusesByKey[key]!!
                val modificationType =
                    when (changeStatus.type) {
                        ChangeType.NEW -> ModificationType.CREATED
                        ChangeType.CHANGED -> ModificationType.UPDATED
                        else -> throw IllegalStateException("Only new or changed statuses are allowed to be published")
                    }

                val resourceHashesDO = changeStatus.toHashDO(tenantId)
                runCatching { resourceHashDao.upsertHash(resourceHashesDO) }.fold(
                    onSuccess = {
                        SucceededResource(resourceType, resourceId, modificationType)
                    },
                    onFailure = {
                        logger.error(it) { "Exception persisting new hash for $resourceHashesDO" }
                        failedResponses.add(
                            FailedResource(
                                resource.resourceType,
                                resource.id!!.value!!,
                                "Error updating the hash store",
                            ),
                        )
                        null
                    },
                )
            }

        return BatchResourceResponse(
            succeeded = successfulResponses,
            failed = failedResponses,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R : Resource<R>> validateAndPublishResources(
        resourcesByKey: Map<String, Resource<R>>,
        changeStatusesByKey: Map<String, ChangeStatus>,
        tenantId: String,
    ): BatchResourceResponse {
        val validationByKey =
            resourcesByKey.mapNotNull { (key, resource) ->
                runCatching { validationService.validate(resource as R, tenantId) }.fold(
                    onSuccess = { key to it },
                    onFailure = { key to FailedValidation(it.localizedMessage) },
                )
            }

        val passedValidationResources =
            validationByKey.filter { it.second is PassedValidation }.map { it.first to resourcesByKey[it.first]!! }
                .toMap()
        val publishResponse =
            if (passedValidationResources.isEmpty()) {
                emptyBatchResponse
            } else {
                publishResources(passedValidationResources, changeStatusesByKey, tenantId)
            }

        val failedValidation =
            validationByKey.filter { it.second is FailedValidation }.map { (key, validation) ->
                val resource = resourcesByKey[key]!!
                FailedResource(
                    resource.resourceType,
                    resource.id!!.value!!,
                    (validation as FailedValidation).failureMessage,
                )
            }

        return BatchResourceResponse(
            succeeded = publishResponse.succeeded,
            failed = publishResponse.failed + failedValidation,
        )
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
