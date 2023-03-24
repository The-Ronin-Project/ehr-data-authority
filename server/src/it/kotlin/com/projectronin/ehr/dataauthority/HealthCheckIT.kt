package com.projectronin.ehr.dataauthority

import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HealthCheckIT : BaseEHRDataAuthorityIT() {
    private val urlPart = "/actuator/health"

    @Test
    fun `health check returns status`() {
        val response = runBlocking {
            httpClient.get("$serverUrl$urlPart")
        }
        assertEquals(response.status, HttpStatusCode.OK)
        val body = runBlocking { response.body<String>() }
        val jsonObject = objectMapper.readTree(body)
        assertEquals(jsonObject["status"].textValue(), "UP")
        assertTrue(jsonObject.has("components"))
    }
}
