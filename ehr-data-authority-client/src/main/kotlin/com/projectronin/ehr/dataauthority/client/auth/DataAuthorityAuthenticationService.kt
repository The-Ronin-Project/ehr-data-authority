package com.projectronin.ehr.dataauthority.client.auth

import com.projectronin.interop.common.auth.Authentication

/**
 * Service responsible for retrieving [Authentication] to call the Data Ingestion Validation Error Management Server.
 */
interface DataAuthorityAuthenticationService {
    /**
     * Retrieves an [Authentication] to call the Data Ingestion Validation Error Management Server.
     */
    fun getAuthentication(): Authentication
}
