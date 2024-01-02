package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.change.data.services.DataStorageService
import com.projectronin.ehr.dataauthority.change.data.services.StorageMode
import com.projectronin.ehr.dataauthority.models.FoundResourceIdentifiers
import com.projectronin.ehr.dataauthority.models.Identifier
import com.projectronin.ehr.dataauthority.models.IdentifierSearchResponse
import com.projectronin.ehr.dataauthority.models.IdentifierSearchableResourceTypes
import com.projectronin.interop.common.http.exceptions.HttpException
import com.projectronin.interop.common.logmarkers.getLogMarker
import com.projectronin.interop.datalake.DatalakeRetrieveService
import com.projectronin.interop.fhir.r4.resource.Bundle
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.http.HttpStatusCode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ResourcesSearchController(
    private val datalakeRetrieveService: DatalakeRetrieveService,
    private val dataStorageService: DataStorageService,
    private val storageMode: StorageMode,
) {
    // Aidbox responds Gone once a resource has been deleted, but we should just treat this as NotFound.
    private val notFoundStatuses = listOf(HttpStatusCode.NotFound, HttpStatusCode.Gone)

    private val logger = KotlinLogging.logger { }

    @Operation(
        summary = "Binary retrieval from OCI.",
        description = "Binary retrieval from OCI.",
    )
    @Parameter(name = "tenantId", example = "tenant")
    @Parameter(name = "udpId", example = "tenant-12345678")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Returns the associated resource if found"),
        ],
    )
    @GetMapping("/tenants/{tenantId}/resources/Binary/{udpId}")
    fun getBinaryResource(
        @PathVariable("tenantId") tenantId: String,
        @PathVariable("udpId") udpId: String,
    ): ResponseEntity<Resource<*>> {
        val resource =
            datalakeRetrieveService.retrieveBinaryData(tenantId, udpId)
                ?: return ResponseEntity.notFound().build()

        if (resource.id?.value?.startsWith("$tenantId-") == false) {
            return ResponseEntity(HttpStatus.BAD_REQUEST)
        }

        return ResponseEntity.ok(resource)
    }

    @Operation(
        summary = "Retrieves the resource related to the given resourceType and udpId for a tenantId.",
        description = "Retrieves the resource related to the given resourceType and udpId for a tenantId.",
    )
    @Parameter(name = "tenantId", example = "tenant")
    @Parameter(name = "resourceType", example = "Patient")
    @Parameter(name = "udpId", example = "tenant-12345678")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Returns the associated resource if found"),
        ],
    )
    @GetMapping("/tenants/{tenantId}/resources/{resourceType}/{udpId}")
    @PreAuthorize("hasAuthority('SCOPE_search:resources')")
    fun getResource(
        @PathVariable("tenantId") tenantId: String,
        @PathVariable("resourceType") resourceType: String,
        @PathVariable("udpId") udpId: String,
    ): ResponseEntity<Resource<*>> {
        val resource =
            try {
                dataStorageService.getResource(resourceType, udpId)
            } catch (exception: Exception) {
                if (exception is HttpException && exception.status in notFoundStatuses) {
                    return ResponseEntity.notFound().build()
                } else {
                    logger.error(exception.getLogMarker(), exception) { "Exception while retrieving from $storageMode" }
                    return ResponseEntity.internalServerError().build()
                }
            }

        // the tenant ID should be in identifiers, but that would require us casting to every resource type
        if (resource.id?.value?.startsWith("$tenantId-") == false) {
            return ResponseEntity(HttpStatus.BAD_REQUEST)
        }
        return ResponseEntity.ok(resource)
    }

    @Operation(
        summary = "Retrieves the identifiers associated to the resourceType",
        description = "Retrieves the identifiers associated to the resourceType with identifiers for tenantId",
    )
    @Parameter(name = "tenantId", example = "tenant")
    @Parameter(name = "resourceType", example = "Patient")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description =
                    "Returns a list of foundResources that are associated with the " +
                        "given identifiers",
            ),
        ],
    )
    @PostMapping("/tenants/{tenantId}/resources/{resourceType}/identifiers")
    @PreAuthorize("hasAuthority('SCOPE_search:resources')")
    fun getResourceIdentifiers(
        @PathVariable("tenantId") tenantId: String,
        @PathVariable("resourceType") resourceType: IdentifierSearchableResourceTypes,
        @RequestBody identifiers: Array<Identifier>,
    ): ResponseEntity<List<IdentifierSearchResponse>> {
        if (identifiers.isEmpty()) {
            return ResponseEntity.badRequest().build()
        }

        val resourceSearches =
            identifiers.associateWith {
                runBlocking {
                    dataStorageService.searchForResources(resourceType.name, tenantId, it.toToken())
                }
            }

        val identifierSearchResponses =
            resourceSearches.map {
                IdentifierSearchResponse(
                    searchedIdentifier = it.key,
                    foundResources = it.value.toFoundResource(resourceType),
                )
            }
        return ResponseEntity.ok(identifierSearchResponses)
    }

    fun Bundle.toFoundResource(resourceType: IdentifierSearchableResourceTypes): List<FoundResourceIdentifiers> {
        val resources = this.entry.mapNotNull { it.resource }
        return resources.map {
            FoundResourceIdentifiers(
                udpId = it.id?.value!!,
                identifiers = it.extractIdentifiers(resourceType),
            )
        }
    }

    // given a generic resource, as long as it's of the type our enums allow, grab the identifiers form them
    fun Resource<*>.extractIdentifiers(resourceType: IdentifierSearchableResourceTypes): List<Identifier> {
        val fhirIdentifiers =
            when (resourceType) {
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
