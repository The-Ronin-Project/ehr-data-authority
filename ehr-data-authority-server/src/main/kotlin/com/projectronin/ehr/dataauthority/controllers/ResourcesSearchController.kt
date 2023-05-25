package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.models.FoundResourceIdentifiers
import com.projectronin.ehr.dataauthority.models.Identifier
import com.projectronin.ehr.dataauthority.models.IdentifierSearchResponse
import com.projectronin.ehr.dataauthority.models.IdentifierSearchableResourceTypes
import com.projectronin.interop.aidbox.client.AidboxClient
import com.projectronin.interop.common.http.exceptions.HttpException
import com.projectronin.interop.common.logmarkers.getLogMarker
import com.projectronin.interop.fhir.r4.resource.Bundle
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ResourcesSearchController(private val aidboxClient: AidboxClient) {
    // Aidbox responds Gone once a resource has been deleted, but we should just treat this as NotFound.
    private val notFoundStatuses = listOf(HttpStatusCode.NotFound, HttpStatusCode.Gone)

    private val logger = KotlinLogging.logger { }

    @GetMapping("/tenants/{tenantId}/resources/{resourceType}/{udpId}")
    @PreAuthorize("hasAuthority('SCOPE_search:resources')")
    fun getResource(
        @PathVariable("tenantId") tenantId: String,
        @PathVariable("resourceType") resourceType: String,
        @PathVariable("udpId") udpId: String
    ): ResponseEntity<Resource<*>> {
        val resource = try {
            runBlocking {
                val response = aidboxClient.getResource(resourceType, udpId)
                response.body<Resource<*>>()
            }
        } catch (exception: Exception) {
            if (exception is HttpException && exception.status in notFoundStatuses) {
                return ResponseEntity.notFound().build()
            } else {
                logger.error(exception.getLogMarker(), exception) { "Exception will retrieving from Aidbox" }
                return ResponseEntity.internalServerError().build()
            }
        }

        // the tenant ID should be in identifiers, but that would require us casting to every resource type
        if (resource.id?.value?.startsWith("$tenantId-") == false) {
            return ResponseEntity(HttpStatus.BAD_REQUEST)
        }
        return ResponseEntity.ok(resource)
    }

    @GetMapping("/tenants/{tenantId}/resources/{resourceType}/identifiers")
    @PreAuthorize("hasAuthority('SCOPE_search:resources')")
    fun getResourceIdentifiers(
        @PathVariable("tenantId") tenantId: String,
        @PathVariable("resourceType") resourceType: IdentifierSearchableResourceTypes,
        @RequestParam("identifier") identifiers: Array<String>
    ): ResponseEntity<List<IdentifierSearchResponse>> {
        if (identifiers.isEmpty() || identifiers.any { !it.contains("|") }) {
            return ResponseEntity.badRequest().build()
        }

        val identifierList = identifiers.map { Identifier.fromToken(it) }

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
