package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.change.ChangeDetectionService
import com.projectronin.ehr.dataauthority.change.data.ResourceHashesDAO
import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import com.projectronin.ehr.dataauthority.change.model.ChangeStatus
import com.projectronin.ehr.dataauthority.change.model.ChangeType
import com.projectronin.ehr.dataauthority.model.ModificationType
import com.projectronin.interop.aidbox.AidboxPublishService
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.resource.Patient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class ResourcesControllerTest {
    private val aidboxPublishService = mockk<AidboxPublishService>()
    private val changeDetectionService = mockk<ChangeDetectionService>()
    private val resourceHashesDAO = mockk<ResourceHashesDAO>()

    private val resourcesController =
        ResourcesController(aidboxPublishService, changeDetectionService, resourceHashesDAO)

    private val mockPatient = mockk<Patient> {
        every { resourceType } returns "Patient"
        every { id!!.value } returns "1"
    }
    private val mockObservation = mockk<Observation> {
        every { resourceType } returns "Observation"
        every { id!!.value } returns "2"
    }

    @Test
    fun `unchanged resource returns a success`() {
        val changeStatus1 = ChangeStatus("Patient", "1", ChangeType.UNCHANGED, UUID.randomUUID(), 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                "tenant",
                mapOf("Patient:1" to mockPatient)
            )
        } returns mapOf("Patient:1" to changeStatus1)

        val response = resourcesController.addNewResources("tenant", listOf(mockPatient))
        assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        assertEquals(1, resourceResponse.succeeded.size)
        assertEquals(0, resourceResponse.failed.size)

        val success1 = resourceResponse.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("1", success1.resourceId)
        assertEquals(ModificationType.UNMODIFIED, success1.modificationType)
    }

    @Test
    fun `failed publication returns as failure`() {
        val changeStatus1 = ChangeStatus("Patient", "1", ChangeType.CHANGED, UUID.randomUUID(), 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                "tenant",
                mapOf("Patient:1" to mockPatient)
            )
        } returns mapOf("Patient:1" to changeStatus1)

        every { aidboxPublishService.publish(listOf(mockPatient)) } returns false

        val response = resourcesController.addNewResources("tenant", listOf(mockPatient))
        assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        assertEquals(0, resourceResponse.succeeded.size)
        assertEquals(1, resourceResponse.failed.size)

        val failure1 = resourceResponse.failed[0]
        assertEquals("Patient", failure1.resourceType)
        assertEquals("1", failure1.resourceId)
        assertEquals("Error publishing to data store.", failure1.error)
    }

    @Test
    fun `new resource adds hash after publication`() {
        val changeStatus1 = ChangeStatus("Patient", "1", ChangeType.NEW, null, 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                "tenant",
                mapOf("Patient:1" to mockPatient)
            )
        } returns mapOf("Patient:1" to changeStatus1)

        every { aidboxPublishService.publish(listOf(mockPatient)) } returns true

        val hashSlot = slot<ResourceHashesDO>()
        every { resourceHashesDAO.insertHash(capture(hashSlot)) } returns mockk()

        val response = resourcesController.addNewResources("tenant", listOf(mockPatient))
        assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        assertEquals(1, resourceResponse.succeeded.size)
        assertEquals(0, resourceResponse.failed.size)

        val success1 = resourceResponse.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("1", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val hashDO = hashSlot.captured
        assertEquals("1", hashDO.resourceId)
        assertEquals("Patient", hashDO.resourceType)
        assertEquals("tenant", hashDO.tenantId)
        assertEquals(1234, hashDO.hash)
    }

    @Test
    fun `changed resource updates hash after publication`() {
        val hashUuid = UUID.randomUUID()
        val changeStatus1 = ChangeStatus("Patient", "1", ChangeType.CHANGED, hashUuid, 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                "tenant",
                mapOf("Patient:1" to mockPatient)
            )
        } returns mapOf("Patient:1" to changeStatus1)

        every { aidboxPublishService.publish(listOf(mockPatient)) } returns true

        every { resourceHashesDAO.updateHash(hashUuid, 1234) } returns mockk()

        val response = resourcesController.addNewResources("tenant", listOf(mockPatient))
        assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        assertEquals(1, resourceResponse.succeeded.size)
        assertEquals(0, resourceResponse.failed.size)

        val success1 = resourceResponse.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("1", success1.resourceId)
        assertEquals(ModificationType.UPDATED, success1.modificationType)

        verify(exactly = 1) { resourceHashesDAO.updateHash(hashUuid, 1234) }
    }

    @Test
    fun `addNewResources should return a batch resource response for valid resources`() {
        val changeStatus1 = ChangeStatus("Patient", "1", ChangeType.CHANGED, UUID.randomUUID(), 1234)
        val changeStatus2 = ChangeStatus("Observation", "2", ChangeType.CHANGED, UUID.randomUUID(), 5678)

        every {
            changeDetectionService.determineChangeStatuses(
                "tenant",
                mapOf("Patient:1" to mockPatient, "Observation:2" to mockObservation)
            )
        } returns mapOf("Patient:1" to changeStatus1, "Observation:2" to changeStatus2)

        every { aidboxPublishService.publish(any()) } returns true
        every { resourceHashesDAO.updateHash(any(), any()) } returns mockk()

        val response = resourcesController.addNewResources("tenant", listOf(mockPatient, mockObservation))
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(2, body.succeeded.size)
        assertEquals(0, body.failed.size)
        assertEquals("Patient", body.succeeded[0].resourceType)
        assertEquals("1", body.succeeded[0].resourceId)
        assertEquals(ModificationType.UPDATED, body.succeeded[0].modificationType)
        assertEquals("Observation", body.succeeded[1].resourceType)
        assertEquals("2", body.succeeded[1].resourceId)
        assertEquals(ModificationType.UPDATED, body.succeeded[1].modificationType)

        verify(exactly = 2) { resourceHashesDAO.updateHash(any(), any()) }
    }

    @Test
    fun `addNewResources should return a batch resource response for a mix of valid and invalid resources`() {
        val changeStatus1 = ChangeStatus("Patient", "1", ChangeType.CHANGED, UUID.randomUUID(), 1234)
        val changeStatus2 = ChangeStatus("Observation", "2", ChangeType.CHANGED, UUID.randomUUID(), 5678)

        every {
            changeDetectionService.determineChangeStatuses(
                "tenant",
                mapOf("Patient:1" to mockPatient, "Observation:2" to mockObservation)
            )
        } returns mapOf("Patient:1" to changeStatus1, "Observation:2" to changeStatus2)

        every { aidboxPublishService.publish(any()) } returnsMany listOf(true, false)

        every { resourceHashesDAO.updateHash(any(), any()) } returns mockk()

        val response = resourcesController.addNewResources("tenant", listOf(mockPatient, mockObservation))
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(1, body.succeeded.size)
        assertEquals(1, body.failed.size)
        assertEquals("Patient", body.succeeded[0].resourceType)
        assertEquals("1", body.succeeded[0].resourceId)
        assertEquals(ModificationType.UPDATED, body.succeeded[0].modificationType)
        assertEquals("Observation", body.failed[0].resourceType)
        assertEquals("2", body.failed[0].resourceId)
        assertEquals("Error publishing to data store.", body.failed[0].error)

        verify(exactly = 1) { resourceHashesDAO.updateHash(any(), any()) }
    }
}
