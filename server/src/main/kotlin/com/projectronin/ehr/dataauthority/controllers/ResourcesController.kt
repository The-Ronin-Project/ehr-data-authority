package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.interop.aidbox.AidboxPublishService
import com.projectronin.interop.fhir.r4.resource.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ResourcesController(private val aidboxPublishService: AidboxPublishService) {

    @PostMapping("/resources")
    fun addNewResources(@RequestBody resources: List<Resource<*>>): ResponseEntity<BatchResourceResponse> {
        val failed = mutableListOf<FailedResource>()
        val succeeded = mutableListOf<SucceededResource>()
        resources.forEach { resource ->
            addSingleResourceHelper(resource).let {
                when (it) {
                    is SucceededResource -> succeeded.add(it)
                    is FailedResource -> failed.add(it)
                }
            }
        }
        return ResponseEntity.ok(BatchResourceResponse(succeeded, failed))
    }

    private fun addSingleResourceHelper(resource: Resource<*>): ResourceResponse {
        val response = when (aidboxPublishService.publish(listOf(resource))) {
            true ->
                SucceededResource(
                    resource.resourceType,
                    resource.id!!.value!!,
                    ModificationType.CREATED
                )
            false ->
                FailedResource(
                    resource.resourceType,
                    resource.id!!.value!!,
                    "Error publishing to data store."
                )
        }
        return response
    }
}

data class BatchResourceResponse(
    val succeeded: List<SucceededResource> = emptyList(),
    val failed: List<FailedResource> = emptyList()
)

data class SucceededResource(
    override val resourceType: String,
    override val resourceId: String,
    val modificationType: ModificationType
) : ResourceResponse

data class FailedResource(
    override val resourceType: String,
    override val resourceId: String,
    val error: String
) : ResourceResponse

sealed interface ResourceResponse {
    val resourceType: String
    val resourceId: String
}

enum class ModificationType {
    CREATED,
    UPDATED,
    UNMODIFIED
}
