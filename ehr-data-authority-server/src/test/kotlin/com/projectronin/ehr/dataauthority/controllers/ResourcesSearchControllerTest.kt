package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.aidbox.AidboxClient
import com.projectronin.ehr.dataauthority.change.data.services.StorageMode
import com.projectronin.ehr.dataauthority.models.Identifier
import com.projectronin.ehr.dataauthority.models.IdentifierSearchableResourceTypes
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.datalake.DatalakeRetrieveService
import com.projectronin.interop.fhir.r4.resource.Binary
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus

class ResourcesSearchControllerTest {
    private val aidboxClient = mockk<AidboxClient>()
    private val datalakeRetrieveService = mockk<DatalakeRetrieveService>()
    private val storageMode = StorageMode.AIDBOX
    private val resourcesSearchController =
        ResourcesSearchController(
            datalakeRetrieveService,
            aidboxClient,
            storageMode,
        )

    @Test
    fun `getResource works`() {
        val mockPatient =
            mockk<Patient> {
                every { resourceType } returns "Patient"
                every { id!!.value } returns "tenant-1"
            }
        coEvery {
            runBlocking { aidboxClient.getResource("Patient", "tenant-1") }
        } returns mockPatient

        val response = resourcesSearchController.getResource("tenant", "Patient", "tenant-1")

        assertEquals(mockPatient, response.body)
        val body = response.body!!
        assertEquals("tenant-1", body.id?.value)
    }

    @Test
    fun `getResource fails with tenant mismatch`() {
        val mockPatient =
            mockk<Patient> {
                every { resourceType } returns "Patient"
                every { id!!.value } returns "1"
            }
        coEvery {
            aidboxClient.getResource("Patient", "1")
        } returns mockPatient

        val response = resourcesSearchController.getResource("tenant", "Patient", "1")
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `getResource returns 404 when receiving Not Found exception from Aidbox`() {
        coEvery {
            aidboxClient.getResource("Patient", "1")
        } throws ClientFailureException(HttpStatusCode.NotFound, "Aidbox")

        val response = resourcesSearchController.getResource("tenant", "Patient", "1")
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `getResource returns 404 when receiving Gone exception from Aidbox`() {
        coEvery {
            aidboxClient.getResource("Patient", "1")
        } throws ClientFailureException(HttpStatusCode.Gone, "Aidbox")

        val response = resourcesSearchController.getResource("tenant", "Patient", "1")
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `getResource returns 500 when receiving other exceptions from Aidbox`() {
        coEvery {
            aidboxClient.getResource("Patient", "1")
        } throws ClientFailureException(HttpStatusCode.ServiceUnavailable, "Aidbox")

        val response = resourcesSearchController.getResource("tenant", "Patient", "1")
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }

    @Test
    fun `search works for location`() {
        val mockLocation1 =
            mockk<Location> {
                every { id?.value } returns "1"
                every { identifier } returns
                    listOf(
                        mockk {
                            every { system?.value } returns "sys1"
                            every { value?.value } returns "val1"
                        },
                        mockk {
                            every { system?.value } returns "sys2"
                            every { value?.value } returns "val2"
                        },
                    )
            }
        val mockLocation2 =
            mockk<Location> {
                every { id?.value } returns "2"
                every { identifier } returns
                    listOf(
                        mockk {
                            every { system?.value } returns "sys3"
                            every { value?.value } returns "val3"
                        },
                        mockk {
                            every { system?.value } returns "sys4"
                            every { value?.value } returns "val4"
                        },
                    )
            }

        coEvery {
            aidboxClient.searchForResources("Location", "tenant", "sys1|ident1")
        } returns
            mockk {
                every { entry } returns
                    listOf(
                        mockk {
                            every { resource } returns mockLocation1
                        },
                    )
            }

        coEvery {
            aidboxClient.searchForResources("Location", "tenant", "sys2|ident1")
        } returns
            mockk {
                every { entry } returns
                    listOf(
                        mockk {
                            every { resource } returns mockLocation1
                        },
                        mockk {
                            every { resource } returns mockLocation2
                        },
                    )
            }

        val response =
            resourcesSearchController.getResourceIdentifiers(
                "tenant",
                IdentifierSearchableResourceTypes.Location,
                arrayOf(Identifier("sys1", "ident1"), Identifier("sys2", "ident1")),
            )
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!

        assertEquals(2, body.size)
        assertEquals(1, body[0].foundResources.size)
        assertEquals(2, body[1].foundResources.size)
        assertEquals("2", body[1].foundResources[1].udpId)
    }

    @Test
    fun `search throws error when called with malformed identifier parameter`() {
        val response =
            resourcesSearchController.getResourceIdentifiers(
                "tenant",
                IdentifierSearchableResourceTypes.Location,
                arrayOf(),
            )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `search works for practitioner`() {
        val mockPractitioner =
            mockk<Practitioner> {
                every { id?.value } returns "1"
                every { identifier } returns
                    listOf(
                        mockk {
                            every { system?.value } returns "sys1"
                            every { value?.value } returns "val1"
                        },
                        mockk {
                            every { system?.value } returns "sys2"
                            every { value?.value } returns "val2"
                        },
                    )
            }

        coEvery {
            aidboxClient.searchForResources("Practitioner", "tenant", "sys1|ident1")
        } returns
            mockk {
                every { entry } returns
                    listOf(
                        mockk {
                            every { resource } returns mockPractitioner
                        },
                    )
            }

        val response =
            resourcesSearchController.getResourceIdentifiers(
                "tenant",
                IdentifierSearchableResourceTypes.Practitioner,
                arrayOf(Identifier("sys1", "ident1")),
            )
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!

        assertEquals(1, body.size)
        assertEquals(1, body[0].foundResources.size)
    }

    @Test
    fun `search doesn't work for resource without a UDP ID`() {
        val mockPractitioner =
            mockk<Practitioner> {
                every { id } returns null
            }

        coEvery {
            aidboxClient.searchForResources("Practitioner", "tenant", "sys1|ident1")
        } returns
            mockk {
                every { entry } returns
                    listOf(
                        mockk {
                            every { resource } returns mockPractitioner
                        },
                    )
            }

        assertThrows<NullPointerException> {
            resourcesSearchController.getResourceIdentifiers(
                "tenant",
                IdentifierSearchableResourceTypes.Practitioner,
                arrayOf(Identifier("sys1", "ident1")),
            )
        }
    }

    @Test
    fun `search works for patient`() {
        val mockPatient1 =
            mockk<Patient> {
                every { id?.value } returns "1"
                every { identifier } returns
                    listOf(
                        mockk {
                            every { system?.value } returns "sys1"
                            every { value?.value } returns "val1"
                        },
                        mockk {
                            every { system?.value } returns "sys2"
                            every { value?.value } returns "val2"
                        },
                    )
            }

        coEvery {
            aidboxClient.searchForResources("Patient", "tenant", "sys1|ident1")
        } returns
            mockk {
                every { entry } returns
                    listOf(
                        mockk {
                            every { resource } returns mockPatient1
                        },
                    )
            }

        coEvery {
            aidboxClient.searchForResources("Patient", "tenant", "sys2|ident1")
        } returns
            mockk {
                every { entry } returns
                    listOf(
                        mockk {
                            every { resource } returns mockPatient1
                        },
                    )
            }

        val response =
            resourcesSearchController.getResourceIdentifiers(
                "tenant",
                IdentifierSearchableResourceTypes.Patient,
                arrayOf(Identifier("sys1", "ident1"), Identifier("sys2", "ident1")),
            )
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!

        assertEquals(2, body.size)
        assertEquals(1, body[0].foundResources.size)
    }

    @Test
    fun `search ignores identifiers missing system`() {
        val mockPatient1 =
            mockk<Patient> {
                every { id?.value } returns "1"
                every { identifier } returns
                    listOf(
                        mockk {
                            every { system } returns null
                            every { value?.value } returns "val1"
                        },
                        mockk {
                            every { system?.value } returns "sys2"
                            every { value?.value } returns "val2"
                        },
                    )
            }

        coEvery {
            aidboxClient.searchForResources("Patient", "tenant", "sys1|ident1")
        } returns
            mockk {
                every { entry } returns
                    listOf(
                        mockk {
                            every { resource } returns mockPatient1
                        },
                    )
            }

        coEvery {
            aidboxClient.searchForResources("Patient", "tenant", "sys2|ident1")
        } returns
            mockk {
                every { entry } returns
                    listOf(
                        mockk {
                            every { resource } returns mockPatient1
                        },
                    )
            }

        val response =
            resourcesSearchController.getResourceIdentifiers(
                "tenant",
                IdentifierSearchableResourceTypes.Patient,
                arrayOf(Identifier("sys1", "ident1"), Identifier("sys2", "ident1")),
            )
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!

        assertEquals(2, body.size)
        assertEquals(1, body[0].foundResources.size)
        assertEquals(listOf(Identifier("sys2", "val2")), body[0].foundResources[0].identifiers)
    }

    @Test
    fun `search ignores identifiers missing system value`() {
        val mockPatient1 =
            mockk<Patient> {
                every { id?.value } returns "1"
                every { identifier } returns
                    listOf(
                        mockk {
                            every { system?.value } returns null
                            every { value?.value } returns "val1"
                        },
                        mockk {
                            every { system?.value } returns "sys2"
                            every { value?.value } returns "val2"
                        },
                    )
            }

        coEvery {
            aidboxClient.searchForResources("Patient", "tenant", "sys1|ident1")
        } returns
            mockk {
                every { entry } returns
                    listOf(
                        mockk {
                            every { resource } returns mockPatient1
                        },
                    )
            }

        coEvery {
            aidboxClient.searchForResources("Patient", "tenant", "sys2|ident1")
        } returns
            mockk {
                every { entry } returns
                    listOf(
                        mockk {
                            every { resource } returns mockPatient1
                        },
                    )
            }

        val response =
            resourcesSearchController.getResourceIdentifiers(
                "tenant",
                IdentifierSearchableResourceTypes.Patient,
                arrayOf(Identifier("sys1", "ident1"), Identifier("sys2", "ident1")),
            )
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!

        assertEquals(2, body.size)
        assertEquals(1, body[0].foundResources.size)
        assertEquals(listOf(Identifier("sys2", "val2")), body[0].foundResources[0].identifiers)
    }

    @Test
    fun `search ignores identifiers missing value`() {
        val mockPatient1 =
            mockk<Patient> {
                every { id?.value } returns "1"
                every { identifier } returns
                    listOf(
                        mockk {
                            every { system?.value } returns "sys1"
                            every { value } returns null
                        },
                        mockk {
                            every { system?.value } returns "sys2"
                            every { value?.value } returns "val2"
                        },
                    )
            }

        coEvery {
            aidboxClient.searchForResources("Patient", "tenant", "sys1|ident1")
        } returns
            mockk {
                every { entry } returns
                    listOf(
                        mockk {
                            every { resource } returns mockPatient1
                        },
                    )
            }

        coEvery {
            aidboxClient.searchForResources("Patient", "tenant", "sys2|ident1")
        } returns
            mockk {
                every { entry } returns
                    listOf(
                        mockk {
                            every { resource } returns mockPatient1
                        },
                    )
            }

        val response =
            resourcesSearchController.getResourceIdentifiers(
                "tenant",
                IdentifierSearchableResourceTypes.Patient,
                arrayOf(Identifier("sys1", "ident1"), Identifier("sys2", "ident1")),
            )
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!

        assertEquals(2, body.size)
        assertEquals(1, body[0].foundResources.size)
        assertEquals(listOf(Identifier("sys2", "val2")), body[0].foundResources[0].identifiers)
    }

    @Test
    fun `search ignores identifiers missing value value`() {
        val mockPatient1 =
            mockk<Patient> {
                every { id?.value } returns "1"
                every { identifier } returns
                    listOf(
                        mockk {
                            every { system?.value } returns "sys1"
                            every { value?.value } returns null
                        },
                        mockk {
                            every { system?.value } returns "sys2"
                            every { value?.value } returns "val2"
                        },
                    )
            }

        coEvery {
            aidboxClient.searchForResources("Patient", "tenant", "sys1|ident1")
        } returns
            mockk {
                every { entry } returns
                    listOf(
                        mockk {
                            every { resource } returns mockPatient1
                        },
                    )
            }

        coEvery {
            aidboxClient.searchForResources("Patient", "tenant", "sys2|ident1")
        } returns
            mockk {
                every { entry } returns
                    listOf(
                        mockk {
                            every { resource } returns mockPatient1
                        },
                    )
            }

        val response =
            resourcesSearchController.getResourceIdentifiers(
                "tenant",
                IdentifierSearchableResourceTypes.Patient,
                arrayOf(Identifier("sys1", "ident1"), Identifier("sys2", "ident1")),
            )
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!

        assertEquals(2, body.size)
        assertEquals(1, body[0].foundResources.size)
        assertEquals(listOf(Identifier("sys2", "val2")), body[0].foundResources[0].identifiers)
    }

    @Test
    fun `getBinaryResource resource works`() {
        val tenantId = "tenant"
        val udpId = "tenant-1"
        val binary =
            mockk<Binary> {
                every { id!!.value } returns udpId
            }
        every {
            datalakeRetrieveService.retrieveBinaryData(tenantId, udpId)
        } returns binary

        val response = resourcesSearchController.getBinaryResource(tenantId, udpId)
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(udpId, body.id?.value)
    }

    @Test
    fun `getBinaryResource fails with tenant mismatch`() {
        val tenantId = "tenant"
        val udpId = "tenant-1"
        val binary =
            mockk<Binary> {
                every { id!!.value } returns "1"
            }
        every {
            datalakeRetrieveService.retrieveBinaryData(tenantId, udpId)
        } returns binary
        val response = resourcesSearchController.getBinaryResource(tenantId, udpId)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `getBinaryResource fails when binary is not found`() {
        val tenantId = "tenant"
        val udpId = "tenant-1"
        val binary = null
        every {
            datalakeRetrieveService.retrieveBinaryData(tenantId, udpId)
        } returns binary

        val response = resourcesSearchController.getBinaryResource(tenantId, udpId)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
