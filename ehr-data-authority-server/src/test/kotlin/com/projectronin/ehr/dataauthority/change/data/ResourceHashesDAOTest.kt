package com.projectronin.ehr.dataauthority.change.data

import com.github.database.rider.core.api.connection.ConnectionHolder
import com.github.database.rider.core.api.dataset.DataSet
import com.github.database.rider.core.api.dataset.ExpectedDataSet
import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import com.projectronin.interop.common.test.database.dbrider.DBRiderConnection
import com.projectronin.interop.common.test.database.ktorm.KtormHelper
import com.projectronin.interop.common.test.database.liquibase.LiquibaseTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
    @DataSet(value = ["/dbunit/hashes/NoHashes.yaml"], cleanAfter = true)
    fun `getHash when none exist`() {
        val dao = ResourceHashesDAO(KtormHelper.database())
        val hash = dao.getHash("tenant1", "Patient", "12345")
        assertNull(hash)
    }

    @Test
    @DataSet(value = ["/dbunit/hashes/SingleHash.yaml"], cleanAfter = true)
    fun `getHash when not found for tenant`() {
        val dao = ResourceHashesDAO(KtormHelper.database())
        val hash = dao.getHash("tenant2", "Patient", "12345")
        assertNull(hash)
    }

    @Test
    @DataSet(value = ["/dbunit/hashes/SingleHash.yaml"], cleanAfter = true)
    fun `getHash when not found for resource type`() {
        val dao = ResourceHashesDAO(KtormHelper.database())
        val hash = dao.getHash("tenant1", "Location", "12345")
        assertNull(hash)
    }

    @Test
    @DataSet(value = ["/dbunit/hashes/SingleHash.yaml"], cleanAfter = true)
    fun `getHash when not found for resource ID`() {
        val dao = ResourceHashesDAO(KtormHelper.database())
        val hash = dao.getHash("tenant1", "Patient", "67890")
        assertNull(hash)
    }

    @Test
    @DataSet(value = ["/dbunit/hashes/SingleHash.yaml"], cleanAfter = true)
    fun `getHash when found for tenant, type and ID`() {
        val dao = ResourceHashesDAO(KtormHelper.database())
        val hash = dao.getHash("tenant1", "Patient", "12345")

        hash!!
        assertEquals(UUID.fromString("b4e8e80a-297a-4b19-bd59-4b8072db9cc4"), hash.hashId)
        assertEquals("12345", hash.resourceId)
        assertEquals("Patient", hash.resourceType)
        assertEquals("tenant1", hash.tenantId)
        assertEquals(13579, hash.hash)
        assertEquals(OffsetDateTime.of(2022, 8, 1, 11, 18, 0, 0, ZoneOffset.UTC), hash.updateDateTime)
    }

    @Test
    @DataSet(value = ["/dbunit/hashes/NoHashes.yaml"], cleanAfter = true)
    @ExpectedDataSet(
        value = ["/dbunit/hashes/ExpectedHashesAfterInsert.yaml"],
        ignoreCols = ["hash_id", "update_dt_tm"]
    )
    fun `upsertHash adds the hash`() {
        val newHash = ResourceHashesDO {
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

        val updatedHash = ResourceHashesDO {
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
            hash.updateDateTime.isAfter(originalDateTime) &&
                (hash.updateDateTime.isBefore(now) || hash.equals(now))
        )
    }

    @Test
    @DataSet(value = ["/dbunit/hashes/NoHashes.yaml"], cleanAfter = true)
    @ExpectedDataSet(
        value = ["/dbunit/hashes/ExpectedHashesAfterInsert.yaml"],
        ignoreCols = ["hash_id", "update_dt_tm"]
    )
    fun `upsertHash ignores repeat calls with no ID`() {
        val newHash = ResourceHashesDO {
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
        orderBy = ["resource_type"]
    )
    fun `upsertHash adds the hash when the same tenant and resource ID but different resource type`() {
        val newHash = ResourceHashesDO {
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
}
