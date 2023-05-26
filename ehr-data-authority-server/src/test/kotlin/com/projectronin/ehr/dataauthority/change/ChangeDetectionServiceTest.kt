package com.projectronin.ehr.dataauthority.change

import com.projectronin.ehr.dataauthority.change.data.ResourceHashesDAO
import com.projectronin.ehr.dataauthority.change.model.ChangeType
import com.projectronin.interop.aidbox.client.AidboxClient
import com.projectronin.interop.fhir.r4.datatype.HumanName
import com.projectronin.interop.fhir.r4.datatype.Meta
import com.projectronin.interop.fhir.r4.datatype.primitive.Canonical
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.client.call.body
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
        every { resourceHashesDAO.getHash("tenant", "Patient", "tenant-1234") } returns null

        val patient = Patient(id = Id("tenant-1234"))
        val statuses = service.determineChangeStatuses("tenant", mapOf(1 to patient))

        assertEquals(1, statuses.size)

        val status1 = statuses[1]!!
        assertEquals("Patient", status1.resourceType)
        assertEquals("tenant-1234", status1.resourceId)
        assertEquals(ChangeType.NEW, status1.type)
        assertNull(status1.hashId)
        assertEquals(patient.consistentHashCode(), status1.hash)

        verify(exactly = 1) { resourceHashesDAO.getHash("tenant", "Patient", "tenant-1234") }
        verify { aidboxClient wasNot Called }
    }

    @Test
    fun `resource with hash different than stored hash`() {
        val hashUuid = UUID.randomUUID()
        every { resourceHashesDAO.getHash("tenant", "Patient", "tenant-1234") } returns mockk {
            every { hashId } returns hashUuid
            every { hash } returns 1234456
        }

        val patient = Patient(id = Id("tenant-1234"))
        val statuses = service.determineChangeStatuses("tenant", mapOf(1 to patient))

        assertEquals(1, statuses.size)

        val status1 = statuses[1]!!
        assertEquals("Patient", status1.resourceType)
        assertEquals("tenant-1234", status1.resourceId)
        assertEquals(ChangeType.CHANGED, status1.type)
        assertEquals(hashUuid, status1.hashId)
        assertEquals(patient.consistentHashCode(), status1.hash)

        verify(exactly = 1) { resourceHashesDAO.getHash("tenant", "Patient", "tenant-1234") }
        verify { aidboxClient wasNot Called }
    }

    @Test
    fun `resource with matching hash and matching normalized form`() {
        val patient = Patient(
            id = Id("tenant-1234"),
            meta = Meta(profile = listOf(Canonical("profile1")))
        )

        val hashUuid = UUID.randomUUID()
        every { resourceHashesDAO.getHash("tenant", "Patient", "tenant-1234") } returns mockk {
            every { hashId } returns hashUuid
            every { hash } returns patient.consistentHashCode()
        }

        val aidboxPatient = Patient(
            id = Id("tenant-1234"),
            meta = Meta(profile = listOf(Canonical("profile1")))
        )
        coEvery { aidboxClient.getResource("Patient", "tenant-1234") } returns mockk {
            coEvery { body<Resource<*>>() } returns aidboxPatient
        }

        val statuses = service.determineChangeStatuses("tenant", mapOf(1 to patient))

        assertEquals(1, statuses.size)

        val status1 = statuses[1]!!
        assertEquals("Patient", status1.resourceType)
        assertEquals("tenant-1234", status1.resourceId)
        assertEquals(ChangeType.UNCHANGED, status1.type)
        assertEquals(hashUuid, status1.hashId)
        assertEquals(patient.consistentHashCode(), status1.hash)

        verify(exactly = 1) { resourceHashesDAO.getHash("tenant", "Patient", "tenant-1234") }
        coVerify(exactly = 1) { aidboxClient.getResource("Patient", "tenant-1234") }
    }

    @Test
    fun `resource with matching hash and non-matching normalized form`() {
        val patient = Patient(
            id = Id("tenant-1234"),
            meta = Meta(profile = listOf(Canonical("profile1")))
        )

        val hashUuid = UUID.randomUUID()
        every { resourceHashesDAO.getHash("tenant", "Patient", "tenant-1234") } returns mockk {
            every { hashId } returns hashUuid
            every { hash } returns patient.consistentHashCode()
        }

        val aidboxPatient = Patient(
            id = Id("tenant-1234"),
            meta = Meta(profile = listOf(Canonical("profile1"))),
            name = listOf(HumanName(text = "Patient Name".asFHIR()))
        )
        coEvery { aidboxClient.getResource("Patient", "tenant-1234") } returns mockk {
            coEvery { body<Resource<*>>() } returns aidboxPatient
        }

        val statuses = service.determineChangeStatuses("tenant", mapOf(1 to patient))

        assertEquals(1, statuses.size)

        val status1 = statuses[1]!!
        assertEquals("Patient", status1.resourceType)
        assertEquals("tenant-1234", status1.resourceId)
        assertEquals(ChangeType.CHANGED, status1.type)
        assertEquals(hashUuid, status1.hashId)
        assertEquals(patient.consistentHashCode(), status1.hash)

        verify(exactly = 1) { resourceHashesDAO.getHash("tenant", "Patient", "tenant-1234") }
        coVerify(exactly = 1) { aidboxClient.getResource("Patient", "tenant-1234") }
    }

    @Test
    fun `resource with matching hash and different profile is considered changes`() {
        val patient = Patient(
            id = Id("tenant-1234"),
            meta = Meta(profile = listOf(Canonical("profile2")))
        )

        val hashUuid = UUID.randomUUID()
        every { resourceHashesDAO.getHash("tenant", "Patient", "tenant-1234") } returns mockk {
            every { hashId } returns hashUuid
            every { hash } returns patient.consistentHashCode()
        }

        val aidboxPatient = Patient(
            id = Id("tenant-1234"),
            meta = Meta(profile = listOf(Canonical("profile1")))
        )
        coEvery { aidboxClient.getResource("Patient", "tenant-1234") } returns mockk {
            coEvery { body<Resource<*>>() } returns aidboxPatient
        }

        val statuses = service.determineChangeStatuses("tenant", mapOf(1 to patient))

        assertEquals(1, statuses.size)

        val status1 = statuses[1]!!
        assertEquals("Patient", status1.resourceType)
        assertEquals("tenant-1234", status1.resourceId)
        assertEquals(ChangeType.CHANGED, status1.type)
        assertEquals(hashUuid, status1.hashId)
        assertEquals(patient.consistentHashCode(), status1.hash)

        verify(exactly = 1) { resourceHashesDAO.getHash("tenant", "Patient", "tenant-1234") }
        coVerify(exactly = 1) { aidboxClient.getResource("Patient", "tenant-1234") }
    }

    @Test
    fun `multiple resources with different matching types`() {
        val patient1 = Patient(
            id = Id("tenant-1234"),
            meta = Meta(profile = listOf(Canonical("profile1")))
        )
        val patient2 = Patient(
            id = Id("tenant-5678"),
            meta = Meta(profile = listOf(Canonical("profile1")))
        )
        val patient3 = Patient(
            id = Id("tenant-9012"),
            meta = Meta(profile = listOf(Canonical("profile1")))
        )

        every { resourceHashesDAO.getHash("tenant", "Patient", "tenant-1234") } returns null

        val hashUuid2 = UUID.randomUUID()
        every { resourceHashesDAO.getHash("tenant", "Patient", "tenant-5678") } returns mockk {
            every { hashId } returns hashUuid2
            every { hash } returns 1234456
        }

        val hashUuid3 = UUID.randomUUID()
        every { resourceHashesDAO.getHash("tenant", "Patient", "tenant-9012") } returns mockk {
            every { hashId } returns hashUuid3
            every { hash } returns patient3.consistentHashCode()
        }

        val aidboxPatient = Patient(
            id = Id("tenant-9012"),
            meta = Meta(profile = listOf(Canonical("profile1")))
        )
        coEvery { aidboxClient.getResource("Patient", "tenant-9012") } returns mockk {
            coEvery { body<Resource<*>>() } returns aidboxPatient
        }

        val statuses = service.determineChangeStatuses("tenant", mapOf(1 to patient1, 2 to patient2, 3 to patient3))

        assertEquals(3, statuses.size)

        val status1 = statuses[1]!!
        assertEquals("Patient", status1.resourceType)
        assertEquals("tenant-1234", status1.resourceId)
        assertEquals(ChangeType.NEW, status1.type)
        assertNull(status1.hashId)
        assertEquals(patient1.consistentHashCode(), status1.hash)

        val status2 = statuses[2]!!
        assertEquals("Patient", status2.resourceType)
        assertEquals("tenant-5678", status2.resourceId)
        assertEquals(ChangeType.CHANGED, status2.type)
        assertEquals(hashUuid2, status2.hashId)
        assertEquals(patient2.consistentHashCode(), status2.hash)

        val status3 = statuses[3]!!
        assertEquals("Patient", status3.resourceType)
        assertEquals("tenant-9012", status3.resourceId)
        assertEquals(ChangeType.UNCHANGED, status3.type)
        assertEquals(hashUuid3, status3.hashId)
        assertEquals(patient3.consistentHashCode(), status3.hash)

        verify(exactly = 1) { resourceHashesDAO.getHash("tenant", "Patient", "tenant-1234") }
        verify(exactly = 1) { resourceHashesDAO.getHash("tenant", "Patient", "tenant-5678") }
        verify(exactly = 1) { resourceHashesDAO.getHash("tenant", "Patient", "tenant-9012") }
        coVerify(exactly = 1) { aidboxClient.getResource("Patient", "tenant-9012") }
    }
}
