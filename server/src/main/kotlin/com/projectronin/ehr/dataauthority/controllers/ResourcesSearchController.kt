package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.model.FoundResourceIdentifiers
import com.projectronin.ehr.dataauthority.model.Identifier
import com.projectronin.ehr.dataauthority.model.IdentifierSearchResponse
import com.projectronin.ehr.dataauthority.model.IdentifierSearchableResourceTypes
import com.projectronin.interop.aidbox.client.AidboxClient
import com.projectronin.interop.fhir.r4.resource.Bundle
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.client.call.body
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class ResourcesSearchController(private val aidboxClient: AidboxClient) {
    @GetMapping("/tenants/{tenantId}/resources/{resourceType}/{udpId}")
    @PreAuthorize("hasAuthority('SCOPE_search:resources')")
    fun getResource(
        @PathVariable("tenantId") tenantId: String,
        @PathVariable("resourceType") resourceType: String,
        @PathVariable("udpId") udpId: String
    ): ResponseEntity<Resource<*>> {
        val resource = runBlocking {
            val response = aidboxClient.getResource(resourceType, udpId)
            response.body<Resource<*>>()
        }
        // the tenant ID should be in identifiers, but that would require us casting to every resource type
        if (resource.id?.value?.startsWith("$tenantId-") == false) {
            return ResponseEntity(HttpStatus.BAD_REQUEST)
        }
        return ResponseEntity.ok(resource)
    }

    @GetMapping("/tenants/{tenantId}/resources/{resourceType}/identifiers/{identifier}")
    @PreAuthorize("hasAuthority('SCOPE_search:resources')")
    fun getResourceIdentifiers(
        @PathVariable("tenantId") tenantId: String,
        @PathVariable("resourceType") resourceType: IdentifierSearchableResourceTypes,
        @PathVariable("identifier") identifier: String
    ): ResponseEntity<List<IdentifierSearchResponse>> {
        if (identifier.isEmpty() || !identifier.contains("|")) {
            return ResponseEntity(HttpStatus.BAD_REQUEST)
        }
        val identifierList = identifier.split(",").map {
            Identifier.fromToken(it)
        }

        val aidboxSearches = identifierList.associateWith {
            runBlocking {
                val response = aidboxClient.searchForResources(resourceType.name, tenantId, it.toToken())
                response.body<Bundle>()
            }
        }

        val identifierSearchResponses = aidboxSearches.map {
            IdentifierSearchResponse(
                searchedIdentifier = it.key,
                foundResources = it.value.toFoundResource(resourceType)
            )
        }
        return ResponseEntity.ok(identifierSearchResponses)
    }

    fun Bundle.toFoundResource(resourceType: IdentifierSearchableResourceTypes): List<FoundResourceIdentifiers> {
        val resources = this.entry.mapNotNull { it.resource }
        return resources.map {
            FoundResourceIdentifiers(
                udpId = it.id?.value!!,
                identifiers = it.extractIdentifiers(resourceType)
            )
        }
    }

    // given a generic resource, as long as it's of the type our enums allow, grab the identifiers form them
    fun Resource<*>.extractIdentifiers(resourceType: IdentifierSearchableResourceTypes): List<Identifier> {
        val fhirIdentifiers = when (resourceType) {
            IdentifierSearchableResourceTypes.Patient -> {
                this as Patient
                this.identifier
            }
            IdentifierSearchableResourceTypes.Location -> {
                this as Location
                this.identifier
            }
            IdentifierSearchableResourceTypes.Practitioner -> {
                this as Practitioner
                this.identifier
            }
        }
        return Identifier.fromFhirIdentifiers(fhirIdentifiers)
    }
}
