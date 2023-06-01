package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.aidbox.AidboxClient
import com.projectronin.ehr.dataauthority.change.data.ResourceHashesDAO
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class ResourcesDeleteControllerTest {
    private val aidboxClient = mockk<AidboxClient>()
    private val resourceHashesDAO = mockk<ResourceHashesDAO>()
    private val controller = ResourcesDeleteController(aidboxClient, resourceHashesDAO)

    @Test
    fun `request for non-deletable tenant returns bad request`() {
        val response = controller.deleteResource("realten", "Patient", "realten-1234")
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        verify { aidboxClient wasNot Called }
        verify { resourceHashesDAO wasNot Called }
    }

    @Test
    fun `resource id with non-matching tenant returns bad request`() {
        val response = controller.deleteResource("ronintst", "Patient", "mdaoc-1234")
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        verify { aidboxClient wasNot Called }
        verify { resourceHashesDAO wasNot Called }
    }

    @Test
    fun `aidbox delete throws exception`() {
        coEvery { aidboxClient.deleteResource("Patient", "ronintst-1234") } throws IllegalStateException()

        val response = controller.deleteResource("ronintst", "Patient", "ronintst-1234")
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)

        coVerify(exactly = 1) { aidboxClient.deleteResource("Patient", "ronintst-1234") }
        verify { resourceHashesDAO wasNot Called }
    }

    @Test
    fun `hash deletion returns unsuccessful`() {
        coEvery { aidboxClient.deleteResource("Patient", "ronintst-1234") } returns mockk()
        every { resourceHashesDAO.deleteHash("ronintst", "Patient", "ronintst-1234") } returns false

        val response = controller.deleteResource("ronintst", "Patient", "ronintst-1234")
        assertEquals(HttpStatus.OK, response.statusCode)

        coVerify(exactly = 1) { aidboxClient.deleteResource("Patient", "ronintst-1234") }
        verify { resourceHashesDAO.deleteHash("ronintst", "Patient", "ronintst-1234") }
    }

    @Test
    fun `delete works`() {
        coEvery { aidboxClient.deleteResource("Patient", "ronintst-1234") } returns mockk()
        every { resourceHashesDAO.deleteHash("ronintst", "Patient", "ronintst-1234") } returns true

        val response = controller.deleteResource("ronintst", "Patient", "ronintst-1234")
        assertEquals(HttpStatus.OK, response.statusCode)

        coVerify(exactly = 1) { aidboxClient.deleteResource("Patient", "ronintst-1234") }
        verify { resourceHashesDAO.deleteHash("ronintst", "Patient", "ronintst-1234") }
    }
}
