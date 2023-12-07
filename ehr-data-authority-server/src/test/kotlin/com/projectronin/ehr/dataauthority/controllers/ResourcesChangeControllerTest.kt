package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.change.ChangeDetectionService
import com.projectronin.ehr.dataauthority.change.model.ChangeStatus
import com.projectronin.ehr.dataauthority.models.ChangeType
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.resources.observation
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Patient
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class ResourcesChangeControllerTest {
    private val changeDetectionService = mockk<ChangeDetectionService>()

    private val resourcesChangeController =
        ResourcesChangeController(
            changeDetectionService,
        )

    private val mockTenantId = "tenant"
    private val testTenantIdentifier =
        identifier {
            system of CodeSystem.RONIN_TENANT.uri
            value of mockTenantId
        }

    // these should eventually be rcdm generators
    private val testPatient =
        patient {
            id of Id("$mockTenantId-1")
            identifier of listOf(testTenantIdentifier)
        }
    private val testPatient2 =
        patient {
            id of Id("$mockTenantId-2")
            identifier of listOf(testTenantIdentifier)
        }
    private val testPatient3 =
        patient {
            id of Id("$mockTenantId-3")
            identifier of listOf(testTenantIdentifier)
        }
    private val testObservation =
        observation {
            id of Id("$mockTenantId-2")
            identifier of listOf(testTenantIdentifier)
        }

    @Test
    fun `unchanged resource returns a success`() {
        val changeStatus = ChangeStatus("Patient", "tenant-1", ChangeType.UNCHANGED, UUID.randomUUID(), 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf("Patient:tenant-1" to testPatient),
            )
        } returns mapOf("Patient:tenant-1" to changeStatus)

        val response = resourcesChangeController.determineIfResourcesChanged(mockTenantId, listOf(testPatient))
        Assertions.assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        Assertions.assertEquals(1, resourceResponse.succeeded.size)
        Assertions.assertEquals(0, resourceResponse.failed.size)

        val success = resourceResponse.succeeded[0]
        Assertions.assertEquals("Patient", success.resourceType)
        Assertions.assertEquals("tenant-1", success.resourceId)
        Assertions.assertEquals(ChangeType.UNCHANGED, success.changeType)
    }

    @Test
    fun `changed resource returns a success`() {
        val changeStatus = ChangeStatus("Patient", "tenant-1", ChangeType.CHANGED, UUID.randomUUID(), 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf("Patient:tenant-1" to testPatient),
            )
        } returns mapOf("Patient:tenant-1" to changeStatus)

        val response = resourcesChangeController.determineIfResourcesChanged(mockTenantId, listOf(testPatient))
        Assertions.assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        Assertions.assertEquals(1, resourceResponse.succeeded.size)
        Assertions.assertEquals(0, resourceResponse.failed.size)

        val success = resourceResponse.succeeded[0]
        Assertions.assertEquals("Patient", success.resourceType)
        Assertions.assertEquals("tenant-1", success.resourceId)
        Assertions.assertEquals(ChangeType.CHANGED, success.changeType)
    }

    @Test
    fun `new resource returns a success`() {
        val changeStatus = ChangeStatus("Patient", "tenant-1", ChangeType.NEW, UUID.randomUUID(), 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf("Patient:tenant-1" to testPatient),
            )
        } returns mapOf("Patient:tenant-1" to changeStatus)

        val response = resourcesChangeController.determineIfResourcesChanged(mockTenantId, listOf(testPatient))
        Assertions.assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        Assertions.assertEquals(1, resourceResponse.succeeded.size)
        Assertions.assertEquals(0, resourceResponse.failed.size)

        val success = resourceResponse.succeeded[0]
        Assertions.assertEquals("Patient", success.resourceType)
        Assertions.assertEquals("tenant-1", success.resourceId)
        Assertions.assertEquals(ChangeType.NEW, success.changeType)
    }

    @Test
    fun `multiple resources returns a success`() {
        val changeStatus1 = ChangeStatus("Patient", "tenant-1", ChangeType.UNCHANGED, UUID.randomUUID(), 1234)
        val changeStatus2 = ChangeStatus("Patient", "tenant-2", ChangeType.CHANGED, UUID.randomUUID(), 1234)
        val changeStatus3 = ChangeStatus("Patient", "tenant-3", ChangeType.NEW, UUID.randomUUID(), 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf(
                    "Patient:tenant-1" to testPatient,
                    "Patient:tenant-2" to testPatient2,
                    "Patient:tenant-3" to testPatient3,
                ),
            )
        } returns
            mapOf(
                "Patient:tenant-1" to changeStatus1,
                "Patient:tenant-2" to changeStatus2,
                "Patient:tenant-3" to changeStatus3,
            )

        val response = resourcesChangeController.determineIfResourcesChanged(mockTenantId, listOf(testPatient, testPatient2, testPatient3))
        Assertions.assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        Assertions.assertEquals(3, resourceResponse.succeeded.size)
        Assertions.assertEquals(0, resourceResponse.failed.size)

        val success1 = resourceResponse.succeeded[0]
        val success2 = resourceResponse.succeeded[1]
        val success3 = resourceResponse.succeeded[2]

        Assertions.assertEquals("Patient", success1.resourceType)
        Assertions.assertEquals("tenant-1", success1.resourceId)
        Assertions.assertEquals(ChangeType.UNCHANGED, success1.changeType)

        Assertions.assertEquals("Patient", success2.resourceType)
        Assertions.assertEquals("tenant-2", success2.resourceId)
        Assertions.assertEquals(ChangeType.CHANGED, success2.changeType)

        Assertions.assertEquals("Patient", success3.resourceType)
        Assertions.assertEquals("tenant-3", success3.resourceId)
        Assertions.assertEquals(ChangeType.NEW, success3.changeType)
    }

    @Test
    fun `combo of resource types returns a success`() {
        val changeStatus1 = ChangeStatus("Patient", "tenant-1", ChangeType.UNCHANGED, UUID.randomUUID(), 1234)
        val changeStatus2 = ChangeStatus("Observation", "tenant-2", ChangeType.CHANGED, UUID.randomUUID(), 1234)

        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                mapOf(
                    "Patient:tenant-1" to testPatient,
                    "Observation:tenant-2" to testObservation,
                ),
            )
        } returns
            mapOf(
                "Patient:tenant-1" to changeStatus1,
                "Observation:tenant-2" to changeStatus2,
            )

        val response = resourcesChangeController.determineIfResourcesChanged(mockTenantId, listOf(testPatient, testObservation))
        Assertions.assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        Assertions.assertEquals(2, resourceResponse.succeeded.size)
        Assertions.assertEquals(0, resourceResponse.failed.size)

        val success1 = resourceResponse.succeeded[0]
        val success2 = resourceResponse.succeeded[1]

        Assertions.assertEquals("Patient", success1.resourceType)
        Assertions.assertEquals("tenant-1", success1.resourceId)
        Assertions.assertEquals(ChangeType.UNCHANGED, success1.changeType)

        Assertions.assertEquals("Observation", success2.resourceType)
        Assertions.assertEquals("tenant-2", success2.resourceId)
        Assertions.assertEquals(ChangeType.CHANGED, success2.changeType)
    }

    @Test
    fun `single mismatched resource fails`() {
        val badPatient =
            patient {
                id of Id("123")
            }
        val response = resourcesChangeController.determineIfResourcesChanged(mockTenantId, listOf(badPatient))

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        val resourceResponse = response.body!!
        Assertions.assertEquals(0, resourceResponse.succeeded.size)
        Assertions.assertEquals(1, resourceResponse.failed.size)

        val fail1 = resourceResponse.failed[0]
        Assertions.assertEquals("Patient", fail1.resourceType)
        Assertions.assertEquals("123", fail1.resourceId)
        Assertions.assertEquals("Resource ID does not match given tenant $mockTenantId", fail1.error)
    }

    @Test
    fun `multiple mismatched resources fail`() {
        val badPatient1 =
            patient {
                id of Id("123")
            }
        val badPatient2 =
            patient {
                id of Id("tenant-123")
            }

        val response = resourcesChangeController.determineIfResourcesChanged(mockTenantId, listOf(badPatient1, badPatient2))

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        val resourceResponse = response.body!!
        Assertions.assertEquals(0, resourceResponse.succeeded.size)
        Assertions.assertEquals(2, resourceResponse.failed.size)

        val fail1 = resourceResponse.failed[0]
        Assertions.assertEquals("Patient", fail1.resourceType)
        Assertions.assertEquals("123", fail1.resourceId)
        Assertions.assertEquals("Resource ID does not match given tenant $mockTenantId", fail1.error)

        val fail2 = resourceResponse.failed[1]
        Assertions.assertEquals("Patient", fail2.resourceType)
        Assertions.assertEquals("tenant-123", fail2.resourceId)
        Assertions.assertEquals("Resource does not contain a tenant identifier for $mockTenantId", fail2.error)
    }

    @Test
    fun `mismatch resource and matched resource of different types fails`() {
        val badPatient1 =
            patient {
                id of Id("123")
            }
        val badObservation =
            observation {
                id of Id("tenant-123")
            }

        val response = resourcesChangeController.determineIfResourcesChanged(mockTenantId, listOf(badPatient1, badObservation))

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        val resourceResponse = response.body!!
        Assertions.assertEquals(0, resourceResponse.succeeded.size)
        Assertions.assertEquals(2, resourceResponse.failed.size)

        val fail1 = resourceResponse.failed[0]
        Assertions.assertEquals("Patient", fail1.resourceType)
        Assertions.assertEquals("123", fail1.resourceId)
        Assertions.assertEquals("Resource ID does not match given tenant $mockTenantId", fail1.error)

        val fail2 = resourceResponse.failed[1]
        Assertions.assertEquals("Observation", fail2.resourceType)
        Assertions.assertEquals("tenant-123", fail2.resourceId)
        Assertions.assertEquals("Resource does not contain a tenant identifier for $mockTenantId", fail2.error)
    }

    @Test
    fun `mismatched resource with matched resource fails`() {
        val badPatient =
            patient {
                id of Id("123")
            }

        val response = resourcesChangeController.determineIfResourcesChanged(mockTenantId, listOf(badPatient, testPatient))

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        val resourceResponse = response.body!!
        Assertions.assertEquals(0, resourceResponse.succeeded.size)
        Assertions.assertEquals(1, resourceResponse.failed.size)

        val fail1 = resourceResponse.failed[0]
        Assertions.assertEquals("Patient", fail1.resourceType)
        Assertions.assertEquals("123", fail1.resourceId)
        Assertions.assertEquals("Resource ID does not match given tenant $mockTenantId", fail1.error)
    }

    @Test
    fun `mismatched resource with matched resource fails - reverse order`() {
        val badPatient =
            patient {
                id of Id("123")
            }

        val response = resourcesChangeController.determineIfResourcesChanged(mockTenantId, listOf(testPatient, badPatient))

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        val resourceResponse = response.body!!
        Assertions.assertEquals(0, resourceResponse.succeeded.size)
        Assertions.assertEquals(1, resourceResponse.failed.size)

        val fail1 = resourceResponse.failed[0]
        Assertions.assertEquals("Patient", fail1.resourceType)
        Assertions.assertEquals("123", fail1.resourceId)
        Assertions.assertEquals("Resource ID does not match given tenant $mockTenantId", fail1.error)
    }

    @Test
    fun `empty tenant returns error`() {
        val response = resourcesChangeController.determineIfResourcesChanged("", listOf(testPatient, testObservation))

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        val resourceResponse = response.body!!
        Assertions.assertEquals(0, resourceResponse.succeeded.size)
        Assertions.assertEquals(2, resourceResponse.failed.size)

        val fail1 = resourceResponse.failed[0]
        Assertions.assertEquals("Patient", fail1.resourceType)
        Assertions.assertEquals("tenant-1", fail1.resourceId)
        Assertions.assertEquals("Resource does not contain a tenant identifier for ", fail1.error)

        val fail2 = resourceResponse.failed[1]
        Assertions.assertEquals("Observation", fail2.resourceType)
        Assertions.assertEquals("tenant-2", fail2.resourceId)
        Assertions.assertEquals("Resource does not contain a tenant identifier for ", fail2.error)
    }

    @Test
    fun `empty resources return success`() {
        val resourcesByKey =
            emptyList<Patient>().associateBy {
                it.let {
                    "$it.resourceType:${it.id!!.value}"
                }
            }
        every {
            changeDetectionService.determineChangeStatuses(
                mockTenantId,
                resourcesByKey,
            )
        } returns mapOf()

        val response = resourcesChangeController.determineIfResourcesChanged(mockTenantId, listOf())

        Assertions.assertEquals(HttpStatus.OK, response.statusCode)

        val resourceResponse = response.body!!
        Assertions.assertEquals(0, resourceResponse.succeeded.size)
        Assertions.assertEquals(0, resourceResponse.failed.size)
    }
}
