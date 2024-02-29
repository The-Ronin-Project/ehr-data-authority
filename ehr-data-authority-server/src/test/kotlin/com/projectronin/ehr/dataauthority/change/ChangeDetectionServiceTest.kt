package com.projectronin.ehr.dataauthority.change

import com.projectronin.ehr.dataauthority.aidbox.AidboxClient
import com.projectronin.ehr.dataauthority.change.data.ResourceHashesDAO
import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import com.projectronin.ehr.dataauthority.change.data.services.ResourceId
import com.projectronin.ehr.dataauthority.models.ChangeType
import com.projectronin.interop.fhir.r4.datatype.HumanName
import com.projectronin.interop.fhir.r4.datatype.Meta
import com.projectronin.interop.fhir.r4.datatype.primitive.Canonical
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.util.asCode
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class ChangeDetectionServiceTest {
    private val aidboxClient = mockk<AidboxClient>()
    private val resourceHashesDAO = mockk<ResourceHashesDAO>()
    private val service = ChangeDetectionService(aidboxClient, resourceHashesDAO)

    @Test
    fun `resource with no stored hash`() {
        val resourceId = ResourceId("Patient", "tenant-1234")
        every { resourceHashesDAO.getHashes("tenant", listOf(resourceId)) } returns emptyMap()

        val patient = Patient(id = Id("tenant-1234"))
        val statuses = service.determineChangeStatuses("tenant", mapOf(1 to patient))

        assertEquals(1, statuses.size)

        val status1 = statuses[1]!!
        assertEquals("Patient", status1.resourceType)
        assertEquals("tenant-1234", status1.resourceId)
        assertEquals(ChangeType.NEW, status1.type)
        assertNull(status1.hashId)
        assertEquals(patient.consistentHashCode(), status1.hash)

        verify(exactly = 1) { resourceHashesDAO.getHashes("tenant", listOf(resourceId)) }
        verify { aidboxClient wasNot Called }
    }

    @Test
    fun `resource with hash different than stored hash`() {
        val hashUuid = UUID.randomUUID()
        val resourceId = ResourceId("Patient", "tenant-1234")
        val hashDO =
            mockk<ResourceHashesDO> {
                every { hashId } returns hashUuid
                every { hash } returns 1234456
            }
        every { resourceHashesDAO.getHashes("tenant", listOf(resourceId)) } returns mapOf(resourceId to hashDO)

        val patient = Patient(id = Id("tenant-1234"))
        val statuses = service.determineChangeStatuses("tenant", mapOf(1 to patient))

        assertEquals(1, statuses.size)

        val status1 = statuses[1]!!
        assertEquals("Patient", status1.resourceType)
        assertEquals("tenant-1234", status1.resourceId)
        assertEquals(ChangeType.CHANGED, status1.type)
        assertEquals(hashUuid, status1.hashId)
        assertEquals(patient.consistentHashCode(), status1.hash)

        verify(exactly = 1) { resourceHashesDAO.getHashes("tenant", listOf(resourceId)) }
        verify { aidboxClient wasNot Called }
    }

    @Test
    fun `resource with matching hash and matching normalized form`() {
        val patient =
            Patient(
                id = Id("tenant-1234"),
                meta = Meta(profile = listOf(Canonical("profile1"))),
            )
        val comparedPatient = patient.copy(meta = null)

        val hashUuid = UUID.randomUUID()
        val resourceId = ResourceId("Patient", "tenant-1234")
        val hashDO =
            mockk<ResourceHashesDO> {
                every { hashId } returns hashUuid
                every { hash } returns comparedPatient.consistentHashCode()
            }
        every { resourceHashesDAO.getHashes("tenant", listOf(resourceId)) } returns mapOf(resourceId to hashDO)

        val aidboxPatient =
            Patient(
                id = Id("tenant-1234"),
                meta = Meta(profile = listOf(Canonical("profile1"))),
            )
        coEvery {
            aidboxClient.getResources(
                "Patient",
                listOf("tenant-1234"),
            )
        } returns mapOf("tenant-1234" to aidboxPatient)

        val statuses = service.determineChangeStatuses("tenant", mapOf(1 to patient))

        assertEquals(1, statuses.size)

        val status1 = statuses[1]!!
        assertEquals("Patient", status1.resourceType)
        assertEquals("tenant-1234", status1.resourceId)
        assertEquals(ChangeType.UNCHANGED, status1.type)
        assertEquals(hashUuid, status1.hashId)
        assertEquals(comparedPatient.consistentHashCode(), status1.hash)

        verify(exactly = 1) { resourceHashesDAO.getHashes("tenant", listOf(resourceId)) }
        coVerify(exactly = 1) { aidboxClient.getResources("Patient", listOf("tenant-1234")) }
    }

    @Test
    fun `resource with matching hash and non-matching normalized form`() {
        val patient =
            Patient(
                id = Id("tenant-1234"),
                meta = Meta(profile = listOf(Canonical("profile1"))),
            )
        val comparedPatient = patient.copy(meta = null)

        val hashUuid = UUID.randomUUID()
        val resourceId = ResourceId("Patient", "tenant-1234")
        val hashDO =
            mockk<ResourceHashesDO> {
                every { hashId } returns hashUuid
                every { hash } returns comparedPatient.consistentHashCode()
            }
        every { resourceHashesDAO.getHashes("tenant", listOf(resourceId)) } returns mapOf(resourceId to hashDO)
        val aidboxPatient =
            Patient(
                id = Id("tenant-1234"),
                meta = Meta(profile = listOf(Canonical("profile1"))),
                name = listOf(HumanName(text = "Patient Name".asFHIR())),
            )
        coEvery {
            aidboxClient.getResources(
                "Patient",
                listOf("tenant-1234"),
            )
        } returns mapOf("tenant-1234" to aidboxPatient)

        val statuses = service.determineChangeStatuses("tenant", mapOf(1 to patient))

        assertEquals(1, statuses.size)

        val status1 = statuses[1]!!
        assertEquals("Patient", status1.resourceType)
        assertEquals("tenant-1234", status1.resourceId)
        assertEquals(ChangeType.CHANGED, status1.type)
        assertEquals(hashUuid, status1.hashId)
        assertEquals(comparedPatient.consistentHashCode(), status1.hash)

        verify(exactly = 1) { resourceHashesDAO.getHashes("tenant", listOf(resourceId)) }
        coVerify(exactly = 1) { aidboxClient.getResources("Patient", listOf("tenant-1234")) }
    }

    @Test
    fun `resource with matching hash and different profile is considered changed`() {
        val patient =
            Patient(
                id = Id("tenant-1234"),
                meta = Meta(profile = listOf(Canonical("profile2"))),
            )
        val comparedPatient = patient.copy(meta = null)

        val hashUuid = UUID.randomUUID()
        val resourceId = ResourceId("Patient", "tenant-1234")
        val hashDO =
            mockk<ResourceHashesDO> {
                every { hashId } returns hashUuid
                every { hash } returns comparedPatient.consistentHashCode()
            }
        every { resourceHashesDAO.getHashes("tenant", listOf(resourceId)) } returns mapOf(resourceId to hashDO)

        val aidboxPatient =
            Patient(
                id = Id("tenant-1234"),
                meta = Meta(profile = listOf(Canonical("profile1"))),
            )
        coEvery {
            aidboxClient.getResources(
                "Patient",
                listOf("tenant-1234"),
            )
        } returns mapOf("tenant-1234" to aidboxPatient)

        val statuses = service.determineChangeStatuses("tenant", mapOf(1 to patient))

        assertEquals(1, statuses.size)

        val status1 = statuses[1]!!
        assertEquals("Patient", status1.resourceType)
        assertEquals("tenant-1234", status1.resourceId)
        assertEquals(ChangeType.CHANGED, status1.type)
        assertEquals(hashUuid, status1.hashId)
        assertEquals(comparedPatient.consistentHashCode(), status1.hash)

        verify(exactly = 1) { resourceHashesDAO.getHashes("tenant", listOf(resourceId)) }
        coVerify(exactly = 1) { aidboxClient.getResources("Patient", listOf("tenant-1234")) }
    }

    @Test
    fun `multiple resources with different matching types`() {
        val patient1 =
            Patient(
                id = Id("tenant-1234"),
                meta = Meta(profile = listOf(Canonical("profile1"))),
            )
        val comparedPatient1 = patient1.copy(meta = null)
        val patient2 =
            Patient(
                id = Id("tenant-5678"),
                meta = Meta(profile = listOf(Canonical("profile1"))),
            )
        val comparedPatient2 = patient2.copy(meta = null)
        val patient3 =
            Patient(
                id = Id("tenant-9012"),
                meta = Meta(profile = listOf(Canonical("profile1"))),
            )
        val comparedPatient3 = patient3.copy(meta = null)
        val patient4 =
            Patient(
                id = Id("tenant-4321"),
                meta = Meta(profile = listOf(Canonical("profile1"))),
                gender = AdministrativeGender.MALE.asCode(),
            )
        val comparedPatient4 = patient4.copy(meta = null)

        val resourceId1 = ResourceId("Patient", "tenant-1234")
        val resourceId2 = ResourceId("Patient", "tenant-5678")
        val resourceId3 = ResourceId("Patient", "tenant-9012")
        val resourceId4 = ResourceId("Patient", "tenant-4321")

        val hashUuid2 = UUID.randomUUID()
        val hashDO2 =
            mockk<ResourceHashesDO> {
                every { hashId } returns hashUuid2
                every { hash } returns 1234456
            }

        val hashUuid3 = UUID.randomUUID()
        val hashDO3 =
            mockk<ResourceHashesDO> {
                every { hashId } returns hashUuid3
                every { hash } returns comparedPatient3.consistentHashCode()
            }

        val hashUuid4 = UUID.randomUUID()
        val hashDO4 =
            mockk<ResourceHashesDO> {
                every { hashId } returns hashUuid4
                every { hash } returns comparedPatient4.consistentHashCode()
            }
        every {
            resourceHashesDAO.getHashes(
                "tenant",
                listOf(resourceId1, resourceId2, resourceId3, resourceId4),
            )
        } returns
            mapOf(resourceId2 to hashDO2, resourceId3 to hashDO3, resourceId4 to hashDO4)

        val aidboxPatient3 =
            Patient(
                id = Id("tenant-9012"),
                meta = Meta(profile = listOf(Canonical("profile1"))),
            )
        val aidboxPatient4 =
            Patient(
                id = Id("tenant-9012"),
                meta = Meta(profile = listOf(Canonical("profile1"))),
            )
        coEvery {
            aidboxClient.getResources(
                "Patient",
                listOf("tenant-9012", "tenant-4321"),
            )
        } returns mapOf("tenant-9012" to aidboxPatient3, "tenant-4321" to aidboxPatient4)

        val statuses =
            service.determineChangeStatuses("tenant", mapOf(1 to patient1, 2 to patient2, 3 to patient3, 4 to patient4))

        assertEquals(4, statuses.size)

        val status1 = statuses[1]!!
        assertEquals("Patient", status1.resourceType)
        assertEquals("tenant-1234", status1.resourceId)
        assertEquals(ChangeType.NEW, status1.type)
        assertNull(status1.hashId)
        assertEquals(comparedPatient1.consistentHashCode(), status1.hash)

        val status2 = statuses[2]!!
        assertEquals("Patient", status2.resourceType)
        assertEquals("tenant-5678", status2.resourceId)
        assertEquals(ChangeType.CHANGED, status2.type)
        assertEquals(hashUuid2, status2.hashId)
        assertEquals(comparedPatient2.consistentHashCode(), status2.hash)

        val status3 = statuses[3]!!
        assertEquals("Patient", status3.resourceType)
        assertEquals("tenant-9012", status3.resourceId)
        assertEquals(ChangeType.UNCHANGED, status3.type)
        assertEquals(hashUuid3, status3.hashId)
        assertEquals(comparedPatient3.consistentHashCode(), status3.hash)

        val status4 = statuses[4]!!
        assertEquals("Patient", status4.resourceType)
        assertEquals("tenant-4321", status4.resourceId)
        assertEquals(ChangeType.CHANGED, status4.type)
        assertEquals(hashUuid4, status4.hashId)
        assertEquals(comparedPatient4.consistentHashCode(), status4.hash)

        verify(exactly = 1) {
            resourceHashesDAO.getHashes(
                "tenant",
                listOf(resourceId1, resourceId2, resourceId3, resourceId4),
            )
        }
        coVerify(exactly = 1) { aidboxClient.getResources("Patient", listOf("tenant-9012", "tenant-4321")) }
    }
}
