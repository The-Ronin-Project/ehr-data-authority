package com.projectronin.ehr.dataauthority.change.data.services

import com.projectronin.interop.fhir.r4.resource.Bundle
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.http.HttpStatusCode

interface DataStorageService {

    /**
     * Publishes FHIR resources to Aidbox or LocalStorage by POSTing a FHIR transaction bundle. Expects an id value in each resource.
     * For an existing resource id, updates that resource with the new data. For a new id, adds the resource to Aidbox.
     * Order of resources in the bundle is not important to resolve references within the bundle. The only requirement
     * on references is that all the referenced resources are either in the bundle or already in Aidbox.
     * The transaction bundle is all-or-nothing: Every resource in the bundle must succeed to return a 200 response.
     * @param resourceCollection List of FHIR resources to publish. May be a mixed List with different resourceTypes.
     * @return [HttpStatusCode] from the Aidbox FHIR transaction bundle REST API.
     */
    suspend fun batchUpsert(resourceCollection: List<Resource<*>>): HttpStatusCode

    /**
     * Fetches a full FHIR resource from Aidbox based on the Fhir ID.
     * @param resourceType [String] the type of FHIR resource, i.e. "Patient" (case sensitive)
     * @param resourceFHIRID [String] the FHIR ID of the resource ("id" json element)
     * @return [Resource] containing the raw data from the server.
     */
    fun getResource(resourceType: String, resourceFHIRID: String): Resource<*>

    /**
     * Fetches a full FHIR resource from Aidbox based on the resource identifiers.
     * @param resourceType [String] the type of FHIR resource, i.e. "Patient" (case sensitive)
     * @param tenantId [String] the tenant mnemonic
     * @param identifierToken [String] system|value token to match system and value on any of the resource identifiers.
     * @return list of [Bundle] containing the raw data from the server.
     */
    suspend fun searchForResources(resourceType: String, tenantId: String, identifierToken: String): Bundle

    /**
     * Deletes the [resourceType] with [udpId] from Aidbox or LocalStorage.
     * @return [HttpStatusCode]
     */
    suspend fun deleteResource(resourceType: String, udpId: String): HttpStatusCode

    /**
     * Deletes all resources from LocalStorage
     * @return [HttpStatusCode] from LocalStorage - returns BadRequest from Aidbox call
     */
    suspend fun deleteAllResources(): HttpStatusCode
}
