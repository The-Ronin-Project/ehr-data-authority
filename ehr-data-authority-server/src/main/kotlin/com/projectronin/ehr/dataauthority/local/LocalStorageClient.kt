package com.projectronin.ehr.dataauthority.local

import com.projectronin.ehr.dataauthority.change.data.services.DataStorageService
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.resource.Bundle
import com.projectronin.interop.fhir.r4.resource.BundleEntry
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.fhir.r4.valueset.BundleType
import io.ktor.http.HttpStatusCode
import org.springframework.context.annotation.Profile
import java.util.concurrent.ConcurrentSkipListMap

/**
 * Client for accessing an localStorage "server" via its configured base URL for REST API calls.
 */
@Profile("local")
class LocalStorageClient : DataStorageService {
    var localStorageMap: ConcurrentSkipListMap<String, MutableMap<String, Resource<*>>> = ConcurrentSkipListMap<String, MutableMap<String, Resource<*>>>()

    override suspend fun batchUpsert(resourceCollection: List<Resource<*>>): HttpStatusCode {
        resourceCollection.forEach {
            localStorageMap.computeIfAbsent(it.resourceType) { key -> mutableMapOf() }
                .put(it.id!!.value!!, it)
        }
        return HttpStatusCode.OK
    }

    override fun getResource(resourceType: String, resourceFHIRID: String): Resource<*> {
        val resource = localStorageMap.get(resourceType)?.get(resourceFHIRID)
        return resource ?: throw ClientFailureException(HttpStatusCode.NotFound, "Local Server")
    }

    override suspend fun searchForResources(
        resourceType: String,
        tenantId: String,
        identifierToken: String
    ): Bundle {
        val resourcesFound = mutableListOf<Resource<*>>()
        val resources = localStorageMap[resourceType]?.values
        val (system, systemValue) = identifierToken.split("|")

        resources?.forEach { resource ->
            val identifiers = accessIdentifiers(resource)
            if (identifiers.isNotEmpty()) {
                identifiers.forEach { id ->
                    if (id.system?.value == system && id.value?.value == systemValue) {
                        resourcesFound.add(resource)
                    }
                }
            }
        }
        return Bundle(
            type = Code(BundleType.TRANSACTION_RESPONSE.code), // type is required
            entry = resourcesFound.map { BundleEntry(resource = it) }
        )
    }

    override suspend fun deleteResource(resourceType: String, udpId: String): HttpStatusCode {
        localStorageMap[resourceType]?.remove(udpId)
        return HttpStatusCode.OK
    }

    override suspend fun deleteAllResources(): HttpStatusCode {
        localStorageMap.clear()
        return HttpStatusCode.OK
    }

    private fun accessIdentifiers(resource: Resource<*>): List<Identifier> {
        val field = resource::class.java.getDeclaredField("identifier")
        field.isAccessible = true
        val ids = field.get(resource) as List<Identifier>
        return ids
    }
}
