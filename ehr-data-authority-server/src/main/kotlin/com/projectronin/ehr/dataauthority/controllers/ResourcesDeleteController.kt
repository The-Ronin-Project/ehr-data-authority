package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.change.data.ResourceHashesDAO
import com.projectronin.interop.aidbox.client.AidboxClient
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class ResourcesDeleteController(
    private val aidboxClient: AidboxClient,
    private val resourceHashesDAO: ResourceHashesDAO
) {
    private val logger = KotlinLogging.logger { }

    private val deletableTenantIndicators = setOf("ronin", "ehrda")

    @DeleteMapping("/tenants/{tenantId}/resources/{resourceType}/{udpId}")
    @PreAuthorize("hasAuthority('SCOPE_delete:resources')")
    fun deleteResource(
        @PathVariable("tenantId") tenantId: String,
        @PathVariable("resourceType") resourceType: String,
        @PathVariable("udpId") udpId: String
    ): ResponseEntity<Void> {
        if (deletableTenantIndicators.none { tenantId.contains(it) }) {
            logger.debug { "Attempted to delete from $tenantId, which is not an approved tenant for deletions" }
            return ResponseEntity.badRequest().build()
        } else if (!udpId.startsWith("$tenantId-")) {
            logger.debug { "Supplied resource $udpId does not belong to tenant $tenantId" }
            return ResponseEntity.badRequest().build()
        }

        runCatching { runBlocking { aidboxClient.deleteResource(resourceType, udpId) } }.onFailure {
            logger.error(it) { "Exception received while attempting to delete $resourceType/$udpId from Aidbox" }
            return ResponseEntity.internalServerError().build()
        }

        val hashDeleted = resourceHashesDAO.deleteHash(tenantId, resourceType, udpId)
        if (!hashDeleted) {
            logger.warn { "Failed to delete hash for $resourceType/$udpId" }
        }

        return ResponseEntity.ok().build()
    }
}
