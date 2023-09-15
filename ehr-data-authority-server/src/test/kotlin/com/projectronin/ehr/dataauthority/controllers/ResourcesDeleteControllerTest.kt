package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.aidbox.AidboxClient
import com.projectronin.ehr.dataauthority.change.data.ResourceHashesDAO
import com.projectronin.ehr.dataauthority.change.data.services.StorageMode
import com.projectronin.ehr.dataauthority.local.LocalStorageClient
import com.projectronin.ehr.dataauthority.local.LocalStorageMapHashDAO
import io.ktor.http.HttpStatusCode
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
    private val localStorageClient = mockk<LocalStorageClient>()
    private val localStorageMapHashDAO = mockk<LocalStorageMapHashDAO>()
    private val localStorageController = ResourcesDeleteController(
        localStorageClient,
        localStorageMapHashDAO,
        StorageMode.LOCAL
    )
    private val controller = ResourcesDeleteController(aidboxClient, resourceHashesDAO, StorageMode.AIDBOX)

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

    @Test
    fun `delete all fails for aidbox`() {
        coEvery { aidboxClient.deleteAllResources() } throws IllegalStateException()

        val response = controller.deleteAllResources()
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        coVerify(exactly = 0) { aidboxClient.deleteAllResources() }
        verify { resourceHashesDAO wasNot Called }
    }

    @Test
    fun `delete all returns OK for local storage client`() {
        coEvery { localStorageClient.deleteAllResources() } returns HttpStatusCode.OK
        coEvery { localStorageMapHashDAO.deleteAllOfHash() } returns true

        val response = localStorageController.deleteAllResources()
        assertEquals(HttpStatus.OK, response.statusCode)

        verify { localStorageMapHashDAO.deleteAllOfHash() }
    }

    @Test
    fun `delete all fails`() {
        coEvery { localStorageClient.deleteAllResources() } throws IllegalStateException()
        coEvery { localStorageMapHashDAO.deleteAllOfHash() } returns false

        val response = localStorageController.deleteAllResources()
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }

    @Test
    fun `local storage delete all of hash returns unsuccessful`() {
        coEvery { localStorageClient.deleteAllResources() } returns HttpStatusCode.OK
        coEvery { localStorageMapHashDAO.deleteAllOfHash() } returns false

        val response = localStorageController.deleteAllResources()
        assertEquals(HttpStatus.OK, response.statusCode)
        verify { localStorageMapHashDAO.deleteAllOfHash() }
    }
}
