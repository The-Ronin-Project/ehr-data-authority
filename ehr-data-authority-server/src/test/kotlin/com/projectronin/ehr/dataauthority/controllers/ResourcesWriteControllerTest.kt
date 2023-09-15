package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.change.ChangeDetectionService
import com.projectronin.ehr.dataauthority.change.data.ResourceHashesDAO
import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import com.projectronin.ehr.dataauthority.change.data.services.StorageMode
import com.projectronin.ehr.dataauthority.change.model.ChangeStatus
import com.projectronin.ehr.dataauthority.kafka.KafkaPublisher
import com.projectronin.ehr.dataauthority.local.LocalStorageMapHashDAO
import com.projectronin.ehr.dataauthority.models.ChangeType
import com.projectronin.ehr.dataauthority.models.ModificationType
import com.projectronin.ehr.dataauthority.publish.PublishService
import com.projectronin.ehr.dataauthority.validation.FailedValidation
import com.projectronin.ehr.dataauthority.validation.PassedValidation
import com.projectronin.ehr.dataauthority.validation.ValidationManager
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.resources.observation
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Binary
import com.projectronin.interop.fhir.r4.resource.Bundle
import io.mockk.Called
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class ResourcesWriteControllerTest {
    private val resourceHashesDAO = mockk<ResourceHashesDAO>()
    private val changeDetectionService = mockk<ChangeDetectionService>()
    private val kafkaPublisher = mockk<KafkaPublisher>()
    private val validationManager = mockk<ValidationManager>()
    private val publishService = mockk<PublishService>()

    private val resourcesWriteController =
        ResourcesWriteController(
            resourceHashesDAO,
            changeDetectionService,
            kafkaPublisher,
            validationManager,
            publishService,
            StorageMode.AIDBOX
        )
    private val mockTenantId = "tenant"
    private val testTenantIdentifier = identifier {
        system of CodeSystem.RONIN_TENANT.uri
        value of mockTenantId
    }

    // these should eventually be rcdm generators
    private val testPatient = patient {
        id of Id("$mockTenantId-1")
        identifier of listOf(testTenantIdentifier)
    }
    private val testObservation = observation {
        id of Id("$mockTenantId-2")
        identifier of listOf(testTenantIdentifier)
    }

    @Test
    fun `unchanged resource returns a success`() {
        val changeStatus1 = ChangeStatus("Patient", "tenant-1", ChangeType.UNCHANGED, UUID.randomUUID(), 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf("Patient:tenant-1" to testPatient)
            )
        } returns mapOf("Patient:tenant-1" to changeStatus1)

        val response = resourcesWriteController.addNewResources(mockTenantId, listOf(testPatient))
        assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        assertEquals(1, resourceResponse.succeeded.size)
        assertEquals(0, resourceResponse.failed.size)

        val success1 = resourceResponse.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-1", success1.resourceId)
        assertEquals(ModificationType.UNMODIFIED, success1.modificationType)

        verify { kafkaPublisher wasNot Called }
    }

    @Test
    fun `failed validation returns as failure`() {
        val changeStatus1 = ChangeStatus("Patient", "tenant-1", ChangeType.CHANGED, UUID.randomUUID(), 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf("Patient:tenant-1" to testPatient)
            )
        } returns mapOf("Patient:tenant-1" to changeStatus1)

        every {
            validationManager.validateResource(
                testPatient,
                mockTenantId
            )
        } returns FailedValidation("Failed validation!")

        val response = resourcesWriteController.addNewResources(mockTenantId, listOf(testPatient))
        assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        assertEquals(0, resourceResponse.succeeded.size)
        assertEquals(1, resourceResponse.failed.size)

        val failure1 = resourceResponse.failed[0]
        assertEquals("Patient", failure1.resourceType)
        assertEquals("tenant-1", failure1.resourceId)
        assertEquals("Failed validation!", failure1.error)

        verify { publishService wasNot Called }
        verify { kafkaPublisher wasNot Called }
    }

    @Test
    fun `failed aidbox publication returns as failure`() {
        val changeStatus1 = ChangeStatus("Patient", "tenant-1", ChangeType.CHANGED, UUID.randomUUID(), 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf("Patient:tenant-1" to testPatient)
            )
        } returns mapOf("Patient:tenant-1" to changeStatus1)

        every { validationManager.validateResource(testPatient, mockTenantId) } returns PassedValidation

        every { publishService.publish(listOf(testPatient)) } returns false

        val response = resourcesWriteController.addNewResources(mockTenantId, listOf(testPatient))
        assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        assertEquals(0, resourceResponse.succeeded.size)
        assertEquals(1, resourceResponse.failed.size)

        val failure1 = resourceResponse.failed[0]
        assertEquals("Patient", failure1.resourceType)
        assertEquals("tenant-1", failure1.resourceId)
        assertEquals("Error publishing to data store.", failure1.error)

        verify { kafkaPublisher wasNot Called }
    }

    @Test
    fun `failed kafka publication returns as failure`() {
        val changeStatus1 = ChangeStatus("Patient", "tenant-1", ChangeType.CHANGED, UUID.randomUUID(), 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf("Patient:tenant-1" to testPatient)
            )
        } returns mapOf("Patient:tenant-1" to changeStatus1)

        every { validationManager.validateResource(testPatient, mockTenantId) } returns PassedValidation

        every { publishService.publish(listOf(testPatient)) } returns true

        every {
            kafkaPublisher.publishResource(
                testPatient,
                ChangeType.CHANGED
            )
        } throws IllegalStateException("FAILURE")

        val response = resourcesWriteController.addNewResources(mockTenantId, listOf(testPatient))
        assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        assertEquals(0, resourceResponse.succeeded.size)
        assertEquals(1, resourceResponse.failed.size)

        val failure1 = resourceResponse.failed[0]
        assertEquals("Patient", failure1.resourceType)
        assertEquals("tenant-1", failure1.resourceId)
        assertEquals("Failed to publish to Kafka: FAILURE", failure1.error)

        verify(exactly = 1) { kafkaPublisher.publishResource(testPatient, ChangeType.CHANGED) }
    }

    @Test
    fun `new resource adds hash after publication`() {
        val changeStatus1 = ChangeStatus("Patient", "tenant-1", ChangeType.NEW, null, 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf("Patient:tenant-1" to testPatient)
            )
        } returns mapOf("Patient:tenant-1" to changeStatus1)

        every { validationManager.validateResource(testPatient, mockTenantId) } returns PassedValidation

        every { publishService.publish(listOf(testPatient)) } returns true

        every { kafkaPublisher.publishResource(testPatient, ChangeType.NEW) } just Runs

        val hashSlot = slot<ResourceHashesDO>()
        every { resourceHashesDAO.upsertHash(capture(hashSlot)) } returns mockk()

        val response = resourcesWriteController.addNewResources(mockTenantId, listOf(testPatient))
        assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        assertEquals(1, resourceResponse.succeeded.size)
        assertEquals(0, resourceResponse.failed.size)

        val success1 = resourceResponse.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-1", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val hashDO = hashSlot.captured
        assertNull(hashDO.hashId)
        assertEquals("tenant-1", hashDO.resourceId)
        assertEquals("Patient", hashDO.resourceType)
        assertEquals(mockTenantId, hashDO.tenantId)
        assertEquals(1234, hashDO.hash)

        verify(exactly = 1) { kafkaPublisher.publishResource(testPatient, ChangeType.NEW) }
    }

    @Test
    fun `new resource fails hash creation after publication`() {
        val changeStatus1 = ChangeStatus("Patient", "tenant-1", ChangeType.NEW, null, 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf("Patient:tenant-1" to testPatient)
            )
        } returns mapOf("Patient:tenant-1" to changeStatus1)

        every { validationManager.validateResource(testPatient, mockTenantId) } returns PassedValidation

        every { publishService.publish(listOf(testPatient)) } returns true

        every { kafkaPublisher.publishResource(testPatient, ChangeType.NEW) } just Runs

        every { resourceHashesDAO.upsertHash(any()) } throws IllegalStateException("Exception")

        val response = resourcesWriteController.addNewResources(mockTenantId, listOf(testPatient))
        assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        assertEquals(0, resourceResponse.succeeded.size)
        assertEquals(1, resourceResponse.failed.size)

        val failed1 = resourceResponse.failed[0]
        assertEquals("Patient", failed1.resourceType)
        assertEquals("tenant-1", failed1.resourceId)
        assertEquals("Error updating the hash store", failed1.error)

        verify(exactly = 1) { kafkaPublisher.publishResource(testPatient, ChangeType.NEW) }
    }

    @Test
    fun `changed resource updates hash after publication`() {
        val hashUuid = UUID.randomUUID()
        val changeStatus1 = ChangeStatus("Patient", "tenant-1", ChangeType.CHANGED, hashUuid, 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf("Patient:tenant-1" to testPatient)
            )
        } returns mapOf("Patient:tenant-1" to changeStatus1)

        every { validationManager.validateResource(testPatient, mockTenantId) } returns PassedValidation

        every { publishService.publish(listOf(testPatient)) } returns true

        every { kafkaPublisher.publishResource(testPatient, ChangeType.CHANGED) } just Runs

        val hashSlot = slot<ResourceHashesDO>()
        every { resourceHashesDAO.upsertHash(capture(hashSlot)) } returns mockk()

        val response = resourcesWriteController.addNewResources(mockTenantId, listOf(testPatient))
        assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        assertEquals(1, resourceResponse.succeeded.size)
        assertEquals(0, resourceResponse.failed.size)

        val success1 = resourceResponse.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-1", success1.resourceId)
        assertEquals(ModificationType.UPDATED, success1.modificationType)

        val hashDO = hashSlot.captured
        assertEquals(hashUuid, hashDO.hashId)
        assertEquals("tenant-1", hashDO.resourceId)
        assertEquals("Patient", hashDO.resourceType)
        assertEquals(mockTenantId, hashDO.tenantId)
        assertEquals(1234, hashDO.hash)

        verify(exactly = 1) { kafkaPublisher.publishResource(testPatient, ChangeType.CHANGED) }
    }

    @Test
    fun `changed resource fails hash update after publication`() {
        val hashUuid = UUID.randomUUID()
        val changeStatus1 = ChangeStatus("Patient", "tenant-1", ChangeType.CHANGED, hashUuid, 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf("Patient:tenant-1" to testPatient)
            )
        } returns mapOf("Patient:tenant-1" to changeStatus1)

        every { validationManager.validateResource(testPatient, mockTenantId) } returns PassedValidation

        every { publishService.publish(listOf(testPatient)) } returns true

        every { kafkaPublisher.publishResource(testPatient, ChangeType.CHANGED) } just Runs

        every { resourceHashesDAO.upsertHash(any()) } throws IllegalStateException("Exception")

        val response = resourcesWriteController.addNewResources(mockTenantId, listOf(testPatient))
        assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        assertEquals(0, resourceResponse.succeeded.size)
        assertEquals(1, resourceResponse.failed.size)

        val failed1 = resourceResponse.failed[0]
        assertEquals("Patient", failed1.resourceType)
        assertEquals("tenant-1", failed1.resourceId)
        assertEquals("Error updating the hash store", failed1.error)

        verify(exactly = 1) { kafkaPublisher.publishResource(testPatient, ChangeType.CHANGED) }
    }

    @Test
    fun `addNewResources should return a batch resource response for valid resources`() {
        val changeStatus1 = ChangeStatus("Patient", "tenant-1", ChangeType.CHANGED, UUID.randomUUID(), 1234)
        val changeStatus2 = ChangeStatus("Observation", "tenant-2", ChangeType.CHANGED, UUID.randomUUID(), 5678)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf("Patient:tenant-1" to testPatient, "Observation:tenant-2" to testObservation)
            )
        } returns mapOf("Patient:tenant-1" to changeStatus1, "Observation:tenant-2" to changeStatus2)

        every { validationManager.validateResource(testPatient, mockTenantId) } returns PassedValidation
        every { validationManager.validateResource(testObservation, mockTenantId) } returns PassedValidation

        every { publishService.publish(any()) } returns true
        every { kafkaPublisher.publishResource(any(), any()) } just Runs
        every { resourceHashesDAO.upsertHash(any()) } returns mockk()

        val response = resourcesWriteController.addNewResources(mockTenantId, listOf(testPatient, testObservation))
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(2, body.succeeded.size)
        assertEquals(0, body.failed.size)
        assertEquals("Patient", body.succeeded[0].resourceType)
        assertEquals("tenant-1", body.succeeded[0].resourceId)
        assertEquals(ModificationType.UPDATED, body.succeeded[0].modificationType)
        assertEquals("Observation", body.succeeded[1].resourceType)
        assertEquals("tenant-2", body.succeeded[1].resourceId)
        assertEquals(ModificationType.UPDATED, body.succeeded[1].modificationType)

        verify(exactly = 2) { kafkaPublisher.publishResource(any(), any()) }
        verify(exactly = 2) { resourceHashesDAO.upsertHash(any()) }
    }

    @Test
    fun `addNewResources should return a batch resource response for a mix of valid and invalid resources`() {
        val changeStatus1 = ChangeStatus("Patient", "tenant-1", ChangeType.CHANGED, UUID.randomUUID(), 1234)
        val changeStatus2 = ChangeStatus("Observation", "tenant-2", ChangeType.CHANGED, UUID.randomUUID(), 5678)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf("Patient:tenant-1" to testPatient, "Observation:tenant-2" to testObservation)
            )
        } returns mapOf("Patient:tenant-1" to changeStatus1, "Observation:tenant-2" to changeStatus2)

        every { validationManager.validateResource(testPatient, mockTenantId) } returns PassedValidation
        every { validationManager.validateResource(testObservation, mockTenantId) } returns PassedValidation

        every { publishService.publish(any()) } returnsMany listOf(true, false)

        every { kafkaPublisher.publishResource(any(), any()) } just Runs

        every { resourceHashesDAO.upsertHash(any()) } returns mockk()

        val response = resourcesWriteController.addNewResources(mockTenantId, listOf(testPatient, testObservation))
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(1, body.succeeded.size)
        assertEquals(1, body.failed.size)
        assertEquals("Patient", body.succeeded[0].resourceType)
        assertEquals("tenant-1", body.succeeded[0].resourceId)
        assertEquals(ModificationType.UPDATED, body.succeeded[0].modificationType)
        assertEquals("Observation", body.failed[0].resourceType)
        assertEquals("tenant-2", body.failed[0].resourceId)
        assertEquals("Error publishing to data store.", body.failed[0].error)

        verify(exactly = 1) { kafkaPublisher.publishResource(any(), any()) }
        verify(exactly = 1) { resourceHashesDAO.upsertHash(any()) }
    }

    @Test
    fun `mismatched resources fail`() {
        val badPatient1 = patient {
            id of Id("123")
        }
        val badPatient2 = patient {
            id of Id("tenant-123")
        }
        val response = resourcesWriteController.addNewResources(mockTenantId, listOf(badPatient1, badPatient2))
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        val resourceResponse = response.body!!
        assertEquals(0, resourceResponse.succeeded.size)
        assertEquals(2, resourceResponse.failed.size)

        val fail1 = resourceResponse.failed[0]
        assertEquals("Patient", fail1.resourceType)
        assertEquals("123", fail1.resourceId)
        assertEquals("Resource ID does not match given tenant $mockTenantId", fail1.error)

        val fail2 = resourceResponse.failed[1]
        assertEquals("Patient", fail2.resourceType)
        assertEquals("tenant-123", fail2.resourceId)
        assertEquals("Resource does not contain a tenant identifier for $mockTenantId", fail2.error)

        verify { kafkaPublisher wasNot Called }
    }

    @Test
    fun `resources without identifiers pass early validation`() {
        // these are unrealistic resources right now, but they don't have identifier property
        val testBinary = Binary(id = Id("tenant-1"), contentType = Code(value = "test"))
        // this has an identifier property, but it's not a list, so we can't inject the tenant identifier
        val testBundle = Bundle(id = Id("tenant-1"), type = Code(value = "test"))

        val changeStatus1 = ChangeStatus("Binary", "tenant-1", ChangeType.UNCHANGED, UUID.randomUUID(), 1234)
        val changeStatus2 = ChangeStatus("Bundle", "tenant-1", ChangeType.UNCHANGED, UUID.randomUUID(), 1234)

        // using unchanged just so we stop there and don't hit actual interop-fhir validation errors
        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf(
                    "Binary:tenant-1" to testBinary,
                    "Bundle:tenant-1" to testBundle
                )
            )
        } returns mapOf(
            "Binary:tenant-1" to changeStatus1,
            "Bundle:tenant-1" to changeStatus2
        )

        val response = resourcesWriteController.addNewResources(mockTenantId, listOf(testBinary, testBundle))
        assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        assertEquals(2, resourceResponse.succeeded.size)
        assertEquals(0, resourceResponse.failed.size)

        val succeeded1 = resourceResponse.succeeded[0]
        assertEquals("Binary", succeeded1.resourceType)
        assertEquals("tenant-1", succeeded1.resourceId)

        val succeeded2 = resourceResponse.succeeded[1]
        assertEquals("Bundle", succeeded2.resourceType)
        assertEquals("tenant-1", succeeded2.resourceId)

        verify { kafkaPublisher wasNot Called }
    }

    @Test
    fun `publish skips validation and kafka if local storage client in use`() {
        val localStorageHash = mockk<LocalStorageMapHashDAO>()
        val localController = ResourcesWriteController(
            resourceHashesDAO,
            changeDetectionService,
            kafkaPublisher,
            validationManager,
            publishService,
            StorageMode.LOCAL
        )
        val changeStatus1 = ChangeStatus("Patient", "tenant-1", ChangeType.CHANGED, UUID.randomUUID(), 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf("Patient:tenant-1" to testPatient)
            )
        } returns mapOf("Patient:tenant-1" to changeStatus1)
        every { publishService.publish(any()) } returns true
        every { localStorageHash.upsertHash(any()) } returns mockk()

        val response = localController.addNewResources(mockTenantId, listOf(testPatient))
        assertEquals(HttpStatus.OK, response.statusCode)
        verify { validationManager wasNot Called }
        verify { kafkaPublisher wasNot Called }
    }
}
