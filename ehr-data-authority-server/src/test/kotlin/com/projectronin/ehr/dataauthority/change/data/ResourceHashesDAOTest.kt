package com.projectronin.ehr.dataauthority.change.data

import com.github.database.rider.core.api.connection.ConnectionHolder
import com.github.database.rider.core.api.dataset.DataSet
import com.github.database.rider.core.api.dataset.ExpectedDataSet
import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import com.projectronin.ehr.dataauthority.change.data.services.ResourceId
import com.projectronin.interop.common.test.database.dbrider.DBRiderConnection
import com.projectronin.interop.common.test.database.ktorm.KtormHelper
import com.projectronin.interop.common.test.database.liquibase.LiquibaseTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@LiquibaseTest(changeLog = "dataauthority/db/changelog/dataauthority.db.changelog-master.yaml")
class ResourceHashesDAOTest {
    @DBRiderConnection
    lateinit var connectionHolder: ConnectionHolder

    @Test
    @DataSet(value = ["/dbunit/hashes/MultipleHashes.yaml"], cleanAfter = true)
    fun `getHashes when none are found`() {
        val dao = ResourceHashesDAO(KtormHelper.database())
        val resourceId1 = ResourceId("Patient", "banana")
        val resourceId2 = ResourceId("Patient", "apple")
        val hashes = dao.getHashes("tenant1", listOf(resourceId1, resourceId2))

        assertEquals(0, hashes.size)
    }

    @Test
    @DataSet(value = ["/dbunit/hashes/MultipleHashes.yaml"], cleanAfter = true)
    fun `getHashes when some are found`() {
        val dao = ResourceHashesDAO(KtormHelper.database())
        val resourceId1 = ResourceId("Patient", "tenant1-12345")
        val resourceId2 = ResourceId("Patient", "apple")
        val hashes = dao.getHashes("tenant1", listOf(resourceId1, resourceId2))

        assertEquals(1, hashes.size)

        val hash1 = hashes[resourceId1]!!
        assertEquals(UUID.fromString("b4e8e80a-297a-4b19-bd59-4b8072db9cc4"), hash1.hashId)
        assertEquals("tenant1-12345", hash1.resourceId)
        assertEquals("Patient", hash1.resourceType)
        assertEquals("tenant1", hash1.tenantId)
        assertEquals(13579, hash1.hash)
        assertEquals(OffsetDateTime.of(2022, 8, 1, 11, 18, 0, 0, ZoneOffset.UTC), hash1.updateDateTime)
    }

    @Test
    @DataSet(value = ["/dbunit/hashes/MultipleHashes.yaml"], cleanAfter = true)
    fun `getHashes when all are found`() {
        val dao = ResourceHashesDAO(KtormHelper.database())
        val resourceId1 = ResourceId("Patient", "tenant1-12345")
        val resourceId2 = ResourceId("Patient", "tenant1-67890")
        val hashes = dao.getHashes("tenant1", listOf(resourceId1, resourceId2))

        assertEquals(2, hashes.size)

        val hash1 = hashes[resourceId1]!!
        assertEquals(UUID.fromString("b4e8e80a-297a-4b19-bd59-4b8072db9cc4"), hash1.hashId)
        assertEquals("tenant1-12345", hash1.resourceId)
        assertEquals("Patient", hash1.resourceType)
        assertEquals("tenant1", hash1.tenantId)
        assertEquals(13579, hash1.hash)
        assertEquals(OffsetDateTime.of(2022, 8, 1, 11, 18, 0, 0, ZoneOffset.UTC), hash1.updateDateTime)

        val hash2 = hashes[resourceId2]!!
        assertEquals(UUID.fromString("9234c30c-d4b5-469b-9a02-59425df9c702"), hash2.hashId)
        assertEquals("tenant1-67890", hash2.resourceId)
        assertEquals("Patient", hash2.resourceType)
        assertEquals("tenant1", hash2.tenantId)
        assertEquals(24680, hash2.hash)
        assertEquals(OffsetDateTime.of(2023, 5, 23, 9, 32, 0, 0, ZoneOffset.UTC), hash2.updateDateTime)
    }

    @Test
    @DataSet(value = ["/dbunit/hashes/MultipleHashes.yaml"], cleanAfter = true)
    fun `getHashes works across resource types`() {
        val dao = ResourceHashesDAO(KtormHelper.database())
        val resourceId1 = ResourceId("Patient", "tenant1-12345")
        val resourceId2 = ResourceId("Practitioner", "tenant1-67890")
        val hashes = dao.getHashes("tenant1", listOf(resourceId1, resourceId2))

        assertEquals(2, hashes.size)

        val hash1 = hashes[resourceId1]!!
        assertEquals(UUID.fromString("b4e8e80a-297a-4b19-bd59-4b8072db9cc4"), hash1.hashId)
        assertEquals("tenant1-12345", hash1.resourceId)
        assertEquals("Patient", hash1.resourceType)
        assertEquals("tenant1", hash1.tenantId)
        assertEquals(13579, hash1.hash)
        assertEquals(OffsetDateTime.of(2022, 8, 1, 11, 18, 0, 0, ZoneOffset.UTC), hash1.updateDateTime)

        val hash2 = hashes[resourceId2]!!
        assertEquals(UUID.fromString("c739fb2b-edaa-460d-a22e-354ec381ac5a"), hash2.hashId)
        assertEquals("tenant1-67890", hash2.resourceId)
        assertEquals("Practitioner", hash2.resourceType)
        assertEquals("tenant1", hash2.tenantId)
        assertEquals(123456789, hash2.hash)
        assertEquals(OffsetDateTime.of(2024, 2, 28, 14, 43, 0, 0, ZoneOffset.UTC), hash2.updateDateTime)
    }

    @Test
    @DataSet(value = ["/dbunit/hashes/NoHashes.yaml"], cleanAfter = true)
    @ExpectedDataSet(
        value = ["/dbunit/hashes/ExpectedHashesAfterInsert.yaml"],
        ignoreCols = ["hash_id", "update_dt_tm"],
    )
    fun `upsertHash adds the hash`() {
        val newHash =
            ResourceHashesDO {
                resourceId = "67890"
                resourceType = "Location"
                tenantId = "tenant2"
                hash = 1470258
                updateDateTime = OffsetDateTime.of(2023, 4, 10, 15, 23, 0, 0, ZoneOffset.UTC)
            }

        val dao = ResourceHashesDAO(KtormHelper.database())
        val hash = dao.upsertHash(newHash)

        assertNotNull(hash.hashId)
        assertEquals("67890", hash.resourceId)
        assertEquals("Location", hash.resourceType)
        assertEquals("tenant2", hash.tenantId)
        assertEquals(1470258, hash.hash)
        assertNotNull(hash.updateDateTime)
    }

    @Test
    @DataSet(value = ["/dbunit/hashes/SingleHash.yaml"], cleanAfter = true)
    @ExpectedDataSet(value = ["/dbunit/hashes/ExpectedHashesAfterUpdate.yaml"], ignoreCols = ["update_dt_tm"])
    fun `upsertHash updates the hash value`() {
        val dao = ResourceHashesDAO(KtormHelper.database())

        val updatedHash =
            ResourceHashesDO {
                hashId = UUID.fromString("b4e8e80a-297a-4b19-bd59-4b8072db9cc4")
                resourceId = "12345"
                resourceType = "Patient"
                tenantId = "tenant1"
                hash = 1234567890
                updateDateTime = OffsetDateTime.now(ZoneOffset.UTC)
            }

        val hash = dao.upsertHash(updatedHash)
        assertEquals(UUID.fromString("b4e8e80a-297a-4b19-bd59-4b8072db9cc4"), hash.hashId)
        assertEquals("12345", hash.resourceId)
        assertEquals("Patient", hash.resourceType)
        assertEquals("tenant1", hash.tenantId)
        assertEquals(1234567890, hash.hash)

        val originalDateTime = OffsetDateTime.of(2022, 8, 1, 11, 18, 0, 0, ZoneOffset.UTC)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        assertTrue(
            hash.updateDateTime!!.isAfter(originalDateTime) &&
                (hash.updateDateTime!!.isBefore(now) || hash.equals(now)),
        )
    }

    @Test
    @DataSet(value = ["/dbunit/hashes/NoHashes.yaml"], cleanAfter = true)
    @ExpectedDataSet(
        value = ["/dbunit/hashes/ExpectedHashesAfterInsert.yaml"],
        ignoreCols = ["hash_id", "update_dt_tm"],
    )
    fun `upsertHash ignores repeat calls with no ID`() {
        val newHash =
            ResourceHashesDO {
                resourceId = "67890"
                resourceType = "Location"
                tenantId = "tenant2"
                hash = 1470258
                updateDateTime = OffsetDateTime.of(2023, 4, 10, 15, 23, 0, 0, ZoneOffset.UTC)
            }

        val dao = ResourceHashesDAO(KtormHelper.database())
        val hash = dao.upsertHash(newHash)

        assertNotNull(hash.hashId)
        assertEquals("67890", hash.resourceId)
        assertEquals("Location", hash.resourceType)
        assertEquals("tenant2", hash.tenantId)
        assertEquals(1470258, hash.hash)
        assertNotNull(hash.updateDateTime)

        val hash2 = dao.upsertHash(newHash)
        assertEquals(hash.hashId, hash2.hashId)
        assertEquals(hash.resourceId, hash2.resourceId)
        assertEquals(hash.resourceType, hash2.resourceType)
        assertEquals(hash.tenantId, hash2.tenantId)
        assertEquals(hash.hash, hash2.hash)
    }

    @Test
    @DataSet(value = ["/dbunit/hashes/SingleHash.yaml"], cleanAfter = true)
    @ExpectedDataSet(
        value = ["/dbunit/hashes/ExpectedHashesAfterInsertWithSimilarResource.yaml"],
        ignoreCols = ["hash_id", "update_dt_tm"],
        orderBy = ["resource_type"],
    )
    fun `upsertHash adds the hash when the same tenant and resource ID but different resource type`() {
        val newHash =
            ResourceHashesDO {
                resourceId = "12345"
                resourceType = "Practitioner"
                tenantId = "tenant1"
                hash = 1470258
                updateDateTime = OffsetDateTime.of(2023, 4, 10, 15, 23, 0, 0, ZoneOffset.UTC)
            }

        val dao = ResourceHashesDAO(KtormHelper.database())
        val hash = dao.upsertHash(newHash)

        assertNotNull(hash.hashId)
        assertEquals("12345", hash.resourceId)
        assertEquals("Practitioner", hash.resourceType)
        assertEquals("tenant1", hash.tenantId)
        assertEquals(1470258, hash.hash)
        assertNotNull(hash.updateDateTime)
    }

    @Test
    @DataSet(value = ["/dbunit/hashes/MultipleHashes.yaml"], cleanAfter = true)
    @ExpectedDataSet(value = ["/dbunit/hashes/MultipleHashes.yaml"], orderBy = ["hash_id"])
    fun `deleteHash returns false if no records deleted`() {
        val dao = ResourceHashesDAO(KtormHelper.database())

        val deleted = dao.deleteHash("tenant1", "Patient", "tenant1-13579")
        assertFalse(deleted)
    }

    @Test
    @DataSet(value = ["/dbunit/hashes/MultipleHashes.yaml"], cleanAfter = true)
    @ExpectedDataSet(value = ["/dbunit/hashes/ExpectedHashesAfterDelete.yaml"])
    fun `deleteHash returns true if records deleted`() {
        val dao = ResourceHashesDAO(KtormHelper.database())

        val deleted = dao.deleteHash("tenant1", "Patient", "tenant1-67890")
        assertTrue(deleted)
    }

    @Test
    fun `deleteAll returns false`() {
        val dao = ResourceHashesDAO(KtormHelper.database())
        val deleted = dao.deleteAllOfHash()
        assertFalse(deleted)
    }
}
