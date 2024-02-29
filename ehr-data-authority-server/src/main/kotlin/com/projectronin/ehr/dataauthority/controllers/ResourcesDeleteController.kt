package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.change.data.services.DataStorageService
import com.projectronin.ehr.dataauthority.change.data.services.ResourceHashDAOService
import com.projectronin.ehr.dataauthority.change.data.services.StorageMode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class ResourcesDeleteController(
    private val dataStorageService: DataStorageService,
    private val resourceHashDao: ResourceHashDAOService,
    private val storageMode: StorageMode,
) {
    private val logger = KotlinLogging.logger { }
    private val deletableTenantIndicators = setOf("ronin", "ehrda")
    private val isLocal = storageMode == StorageMode.LOCAL

    @Operation(
        summary = "Deletes the provided resource",
        description =
            "Deletes the resource for the given resourceType and udpId for a given tenantId from EHRDA. " +
                "\nNOTE: This API only supports deleting from testing tenants. Any non-testing tenant that is used " +
                "will result in a 400.",
    )
    @Parameter(name = "tenantId", example = "tenant")
    @Parameter(name = "resourceType", example = "Patient")
    @Parameter(name = "udpId", example = "tenant-1")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successful operation"),
            ApiResponse(responseCode = "400", description = "Failed operation"),
        ],
    )
    @DeleteMapping("/tenants/{tenantId}/resources/{resourceType}/{udpId}")
    @PreAuthorize("hasAuthority('SCOPE_delete:resources')")
    fun deleteResource(
        @PathVariable("tenantId") tenantId: String,
        @PathVariable("resourceType") resourceType: String,
        @PathVariable("udpId") udpId: String,
    ): ResponseEntity<Void> {
        if (deletableTenantIndicators.none { tenantId.contains(it) }) {
            logger.debug { "Attempted to delete from $tenantId, which is not an approved tenant for deletions" }
            return ResponseEntity.badRequest().build()
        } else if (!udpId.startsWith("$tenantId-")) {
            logger.debug { "Supplied resource $udpId does not belong to tenant $tenantId" }
            return ResponseEntity.badRequest().build()
        }

        runCatching { runBlocking { dataStorageService.deleteResource(resourceType, udpId) } }.onFailure {
            logger.error(it) { "Exception received while attempting to delete $resourceType/$udpId from $storageMode" }
            return ResponseEntity.internalServerError().build()
        }

        val hashDeleted = resourceHashDao.deleteHash(tenantId, resourceType, udpId)
        if (!hashDeleted) {
            logger.warn { "Failed to delete hash for $resourceType/$udpId" }
        }

        return ResponseEntity.ok().build()
    }

    @Operation(
        summary = "Deletes ALL resources from local storage",
        description =
            "Deletes ALL resources from Local Storage. " +
                "\nNOTE: This API only supports deleting the local storage.  This means the spring profile must be set to" +
                "'local'",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successful operation"),
            ApiResponse(responseCode = "400", description = "Bad request"),
        ],
    )
    @DeleteMapping("/local")
    @PreAuthorize("hasAuthority('SCOPE_delete:resources')")
    fun deleteAllResources(): ResponseEntity<Void> {
        if (isLocal) {
            runCatching { runBlocking { dataStorageService.deleteAllResources() } }.onFailure {
                logger.error(it) { "Exception while attempting to delete all of local storage" }
                return ResponseEntity.internalServerError().build()
            }
            val allOfHashDeleted = resourceHashDao.deleteAllOfHash()
            if (!allOfHashDeleted) {
                logger.warn { "Failed to delete full hash while deleting all resources." }
            }
            return ResponseEntity.ok().build()
        }
        logger.error { "Request to delete all resources is only allowed for local storage" }
        return ResponseEntity.badRequest().build()
    }
}
