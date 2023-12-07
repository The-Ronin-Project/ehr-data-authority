package com.projectronin.ehr.dataauthority.local

import com.projectronin.ehr.dataauthority.change.data.model.ResourceHashesDO
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class LocalStorageMapHashTest {
    private val localStorageHash = LocalStorageMapHashDAO()
    val practitionerHash1 =
        ResourceHashesDO {
            resourceId = "1234"
            resourceType = "Practitioner"
            tenantId = "test1"
            hash = 1470258
            updateDateTime = OffsetDateTime.of(2023, 4, 10, 15, 23, 0, 0, ZoneOffset.UTC)
        }
    private val practitionerHash2 =
        ResourceHashesDO {
            resourceId = "5678"
            resourceType = "Practitioner"
            tenantId = "test2"
            hash = 1470258
            updateDateTime = OffsetDateTime.of(2023, 4, 10, 15, 23, 0, 0, ZoneOffset.UTC)
        }
    private val practitionerHash3 =
        ResourceHashesDO {
            resourceId = "9101"
            resourceType = "Practitioner"
            tenantId = "test3"
            hash = 1470258
            updateDateTime = OffsetDateTime.of(2023, 4, 10, 15, 23, 0, 0, ZoneOffset.UTC)
        }

    @BeforeEach
    fun `add data to localStorageMap`() {
        localStorageHash.upsertHash(practitionerHash1)
        localStorageHash.upsertHash(practitionerHash2)
        localStorageHash.upsertHash(practitionerHash3)
    }

    @AfterEach
    fun `remove data from localStorageMap`() {
        localStorageHash.deleteAllOfHash()
    }

    @Test
    fun `getHash works when tenant, type, and id are found`() {
        val hash = localStorageHash.getHash("test1", "Practitioner", "1234")

        hash!!
        assertNotNull(hash.hash)
        assertEquals(hash.tenantId, "test1")
        assertEquals(hash.resourceType, "Practitioner")
        assertEquals(hash.resourceId, "1234")
    }

    @Test
    fun `upsertHash adds the hash`() {
        val newHash =
            ResourceHashesDO {
                resourceId = "67890"
                resourceType = "Location"
                tenantId = "tenant2"
                hash = 1470258
                updateDateTime = OffsetDateTime.of(2023, 4, 10, 15, 23, 0, 0, ZoneOffset.UTC)
            }
        localStorageHash.upsertHash(newHash)
        val hash = localStorageHash.getHash("tenant2", "Location", "67890")

        assertNotNull(hash?.hashId)
        assertEquals("67890", hash?.resourceId)
        assertEquals("Location", hash?.resourceType)
        assertEquals("tenant2", hash?.tenantId)
        assertEquals(1470258, hash?.hash)
        assertNotNull(hash?.updateDateTime)
    }

    @Test
    fun `getHash returns ResourceHashesDO if found in localStorageHash map`() {
        val hash = localStorageHash.getHash("test1", "Practitioner", "1234")

        assertNotNull(hash?.hashId)
        assertEquals("1234", hash?.resourceId)
        assertEquals("Practitioner", hash?.resourceType)
        assertEquals("test1", hash?.tenantId)
        assertNotNull(hash?.hash)
    }

    @Test
    fun `upsertHash ignores repeat calls with no ID`() {
        localStorageHash.deleteAllOfHash() // clear hash map before hand
        val newHash =
            ResourceHashesDO {
                resourceId = "1234"
                resourceType = "Practitioner"
                tenantId = "test1"
                hash = 1470258
                updateDateTime = OffsetDateTime.of(2023, 4, 10, 15, 23, 0, 0, ZoneOffset.UTC)
            }

        val hash = localStorageHash.upsertHash(newHash) // try to add hash to map again even though it already exists

        assertEquals("1234", hash.resourceId)
        assertEquals("Practitioner", hash.resourceType)
        assertEquals("test1", hash.tenantId)
        assertEquals(1470258, hash.hash)
        assertNotNull(hash.updateDateTime)

        val hash2 = localStorageHash.upsertHash(newHash)
        assertEquals(hash.hashId, hash2.hashId)
        assertEquals(hash.resourceId, hash2.resourceId)
        assertEquals(hash.resourceType, hash2.resourceType)
        assertEquals(hash.tenantId, hash2.tenantId)
        assertEquals(hash.hash, hash2.hash)
    }

    @Test
    fun `deleteHash returns false if no records deleted`() {
        val deleted = localStorageHash.deleteHash("fakeTenant", "Practitioner", "fakeTenant-12345678")
        assertFalse(deleted)
    }

    @Test
    fun `deleteHash returns true if records deleted`() {
        val deleted = localStorageHash.deleteHash("test1", "Practitioner", "1234")
        assertTrue(deleted)
    }

    @Test
    fun `deleteHash all returns true if records deleted`() {
        val deleted = localStorageHash.deleteAllOfHash()
        assertTrue(deleted)
    }
}
