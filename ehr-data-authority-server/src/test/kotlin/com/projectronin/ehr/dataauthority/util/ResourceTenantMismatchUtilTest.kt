package com.projectronin.ehr.dataauthority.util

import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.resources.observation
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResourceTenantMismatchUtilTest {
    private val mockTenantId = "tenant"
    private val mismatchedTenantId = "mismatchedTenant"
    private val testTenantIdentifier =
        identifier {
            system of CodeSystem.RONIN_TENANT.uri
            value of mockTenantId
        }
    private val mismatchedTestTenantIdentifier =
        identifier {
            system of CodeSystem.RONIN_TENANT.uri
            value of mismatchedTenantId
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

    private val mismatchedTestPatient =
        patient {
            id of Id("$mismatchedTenantId-3")
            identifier of listOf(mismatchedTestTenantIdentifier)
        }

    private val mismatchedTestPatient2 =
        patient {
            id of Id("$mismatchedTenantId-4")
            identifier of listOf(mismatchedTestTenantIdentifier)
        }

    private val mismatchedTestObservation =
        observation {
            id of Id("$mismatchedTenantId-1")
            identifier of listOf(mismatchedTestTenantIdentifier)
        }

    private val testPatientWithIncorrectIdentifier =
        patient {
            id of Id("$mockTenantId-9")
            identifier of listOf(mismatchedTestTenantIdentifier)
        }

    private val singleResourceList = listOf(testPatient)
    private val multiResourceList =
        listOf(
            testPatient,
            testPatient2,
            testPatient3,
        )
    private val multiResourceListWithUniqueTypes =
        listOf(
            testPatient,
            testPatient2,
            testPatient3,
            testObservation,
        )
    private val singleMismatchedResourceList = listOf(mismatchedTestPatient)
    private val multiMismatchedResourceList =
        listOf(
            mismatchedTestPatient,
            mismatchedTestPatient2,
        )
    private val multiMismatchResourceListWithUniqueTypes =
        listOf(
            mismatchedTestPatient,
            mismatchedTestPatient2,
            mismatchedTestObservation,
        )
    private val singleResourceWithIncorrectIdentifier = listOf(testPatientWithIncorrectIdentifier)

    @Test
    fun `no mismatches are found with single resource`() {
        val result =
            ResourceTenantMismatchUtil.getMismatchResourceFailures(
                singleResourceList,
                mockTenantId,
            )
        assertEquals(0, result.size)
    }

    @Test
    fun `no mismatches are found with multiple resources of a single type`() {
        val result =
            ResourceTenantMismatchUtil.getMismatchResourceFailures(
                multiResourceList,
                mockTenantId,
            )
        assertEquals(0, result.size)
    }

    @Test
    fun `no mismatches are found with multiple resources of multiple types`() {
        val result =
            ResourceTenantMismatchUtil.getMismatchResourceFailures(
                multiResourceListWithUniqueTypes,
                mockTenantId,
            )
        assertEquals(0, result.size)
    }

    @Test
    fun `mismatch found with single resource`() {
        val result =
            ResourceTenantMismatchUtil.getMismatchResourceFailures(
                singleMismatchedResourceList,
                mockTenantId,
            )
        assertEquals(1, result.size)

        val failure = result[0]
        assertEquals("Patient", failure.resourceType)
        assertEquals("mismatchedTenant-3", failure.resourceId)
        assertEquals("Resource ID does not match given tenant tenant", failure.error)
    }

    @Test
    fun `mismatches found with multiple resources of a single type`() {
        val result =
            ResourceTenantMismatchUtil.getMismatchResourceFailures(
                multiMismatchedResourceList,
                mockTenantId,
            )
        assertEquals(2, result.size)

        val failure1 = result[0]
        assertEquals("Patient", failure1.resourceType)
        assertEquals("mismatchedTenant-3", failure1.resourceId)
        assertEquals("Resource ID does not match given tenant tenant", failure1.error)

        val failure2 = result[1]
        assertEquals("Patient", failure2.resourceType)
        assertEquals("mismatchedTenant-4", failure2.resourceId)
        assertEquals("Resource ID does not match given tenant tenant", failure2.error)
    }

    @Test
    fun `mismatches found with multiple resources of a multiple types`() {
        val result =
            ResourceTenantMismatchUtil.getMismatchResourceFailures(
                multiMismatchResourceListWithUniqueTypes,
                mockTenantId,
            )
        assertEquals(3, result.size)

        val failure1 = result[0]
        assertEquals("Patient", failure1.resourceType)
        assertEquals("mismatchedTenant-3", failure1.resourceId)
        assertEquals("Resource ID does not match given tenant tenant", failure1.error)

        val failure2 = result[1]
        assertEquals("Patient", failure2.resourceType)
        assertEquals("mismatchedTenant-4", failure2.resourceId)
        assertEquals("Resource ID does not match given tenant tenant", failure2.error)

        val failure3 = result[2]
        assertEquals("Observation", failure3.resourceType)
        assertEquals("mismatchedTenant-1", failure3.resourceId)
        assertEquals("Resource ID does not match given tenant tenant", failure3.error)
    }

    @Test
    fun `mismatch found with matched and mismatched resources of a single type`() {
        val result =
            ResourceTenantMismatchUtil.getMismatchResourceFailures(
                singleResourceList + singleMismatchedResourceList,
                mockTenantId,
            )
        assertEquals(1, result.size)

        val failure = result[0]
        assertEquals("Patient", failure.resourceType)
        assertEquals("mismatchedTenant-3", failure.resourceId)
        assertEquals("Resource ID does not match given tenant tenant", failure.error)
    }

    @Test
    fun `mismatch found with matched and mismatched resources of a multiple types`() {
        val result =
            ResourceTenantMismatchUtil.getMismatchResourceFailures(
                multiResourceListWithUniqueTypes + multiMismatchResourceListWithUniqueTypes,
                mockTenantId,
            )
        assertEquals(3, result.size)

        val failure1 = result[0]
        assertEquals("Patient", failure1.resourceType)
        assertEquals("mismatchedTenant-3", failure1.resourceId)
        assertEquals("Resource ID does not match given tenant tenant", failure1.error)

        val failure2 = result[1]
        assertEquals("Patient", failure2.resourceType)
        assertEquals("mismatchedTenant-4", failure2.resourceId)
        assertEquals("Resource ID does not match given tenant tenant", failure2.error)

        val failure3 = result[2]
        assertEquals("Observation", failure3.resourceType)
        assertEquals("mismatchedTenant-1", failure3.resourceId)
        assertEquals("Resource ID does not match given tenant tenant", failure3.error)
    }

    @Test
    fun `no mismatches found with empty resources list`() {
        val result = ResourceTenantMismatchUtil.getMismatchResourceFailures(emptyList(), mockTenantId)
        assertEquals(0, result.size)
    }

    @Test
    fun `odd identifier`() {
        val result =
            ResourceTenantMismatchUtil.getMismatchResourceFailures(
                singleResourceWithIncorrectIdentifier,
                mockTenantId,
            )
        assertEquals(1, result.size)

        val failure1 = result[0]
        assertEquals("Patient", failure1.resourceType)
        assertEquals("tenant-9", failure1.resourceId)
        assertEquals("Resource does not contain a tenant identifier for tenant", failure1.error)
    }
}
