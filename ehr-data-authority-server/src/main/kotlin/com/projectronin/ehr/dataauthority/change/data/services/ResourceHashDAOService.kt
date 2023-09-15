package com.projectronin.ehr.dataauthority.change.data.services

import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO

interface ResourceHashDAOService {

    /**
     * Retrieves the [ResourceHashesDO] associated with [resourceType], [resourceId] and [tenantId].
     */
    fun getHash(tenantId: String, resourceType: String, resourceId: String): ResourceHashesDO?

    /**
     * Inserts or updates the [resourceHashesDO] and returns the current view from the data store.
     */
    fun upsertHash(resourceHashesDO: ResourceHashesDO): ResourceHashesDO

    /**
     * Deletes the hash associated to the [resourceType] and [resourceId] for [tenantId]
     */
    fun deleteHash(tenantId: String, resourceType: String, resourceId: String): Boolean

    /**
     * Only used for local storage hash (Map). Clears local storage hash when local storage map is cleared, return bad request for db call
     */
    fun deleteAllOfHash(): Boolean
}
