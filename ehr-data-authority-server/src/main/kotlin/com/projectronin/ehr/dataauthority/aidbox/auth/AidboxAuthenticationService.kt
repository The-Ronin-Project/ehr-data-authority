package com.projectronin.ehr.dataauthority.aidbox.auth

import com.projectronin.interop.common.auth.Authentication
import com.projectronin.interop.common.http.request
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value

/**
 * Service providing Authentication capabilities for an Aidbox instance located at [aidboxBaseUrl] with the provided [aidboxCredentials].
 */

class AidboxAuthenticationService(
    private val httpClient: HttpClient,
    @Value("\${aidbox.url}")
    private val aidboxBaseUrl: String,
    private val aidboxCredentials: AidboxCredentials,
) {
    private val logger = KotlinLogging.logger { }
    private val authPath = "/auth/token"
    private val deleteTokenPath = "/Session"

    /**
     * Retrieves an Authentication.
     */
    fun getAuthentication(): Authentication {
        return runBlocking {
            val authUrl = aidboxBaseUrl + authPath
            logger.debug { "Retrieving authorization from $authUrl" }
            val httpResponse: HttpResponse =
                httpClient.request("Aidbox", authUrl) { url ->
                    post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(aidboxCredentials)
                    }
                }

            httpResponse.body<AidboxAuthentication>()
        }
    }

    /**
     * Deletes session related to token that has expired.
     */
    fun deleteAuthenticationTokenSession(authenticationToken: String): HttpStatusCode {
        return runBlocking {
            val deleteUrl = aidboxBaseUrl + deleteTokenPath
            logger.debug { "Deleting session for expired token $authenticationToken" }
            val httpResponse: HttpResponse =
                httpClient.request("Aidbox", deleteUrl) { url ->
                    delete(url) {
                        headers {
                            append(HttpHeaders.Authorization, "Bearer $authenticationToken")
                        }
                    }
                }
            httpResponse.status
        }
    }
}
