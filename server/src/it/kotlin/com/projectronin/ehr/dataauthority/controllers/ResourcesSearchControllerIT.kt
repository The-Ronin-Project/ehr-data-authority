package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.BaseEHRDataAuthorityIT
import com.projectronin.ehr.dataauthority.model.Identifier
import com.projectronin.ehr.dataauthority.testclients.AidboxClient
import com.projectronin.ehr.dataauthority.testclients.EHRDAClient
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Patient
import io.ktor.client.plugins.ClientRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResourcesSearchControllerIT : BaseEHRDataAuthorityIT() {
    @Test
    fun `get works`() {
        val patient = patient {
            id of Id(value = "Test-12345")
            identifier of listOf(
                identifier {
                    system of "system"
                    value of "value"
                },
                identifier {
                    system of CodeSystem.RONIN_TENANT.uri.value!!
                    value of "Test"
                }
            )
            name of listOf(
                name {
                    family of "Lastnameski"
                }
            )
        }
        val patient2 = patient {
            id of Id(value = "Test-154321")
            identifier of listOf(
                identifier {
                    system of "system2"
                    value of "value"
                },
                identifier {
                    system of CodeSystem.RONIN_TENANT.uri.value!!
                    value of "Test"
                }
            )
        }

        AidboxClient.addResource(patient)
        AidboxClient.addResource(patient2)
        val response = EHRDAClient.getResource("Test", "Patient", "Test-12345")
        AidboxClient.deleteResource("Patient", patient.id?.value!!)
        AidboxClient.deleteResource("Patient", patient2.id?.value!!)
        val patientResponse = response as Patient
        assertEquals("Lastnameski", patientResponse.name.first().family?.value)
    }

    @Test
    fun `get fails for tenant mismatch`() {
        val patient = patient {
            id of Id(value = "Test-12345")
            identifier of listOf(
                identifier {
                    system of "system"
                    value of "value"
                },
                identifier {
                    system of CodeSystem.RONIN_TENANT.uri.value!!
                    value of "Test"
                }
            )
            name of listOf(
                name {
                    family of "Lastnameski"
                }
            )
        }
        val patient2 = patient {
            id of Id(value = "Test-154321")
            identifier of listOf(
                identifier {
                    system of "system2"
                    value of "value"
                },
                identifier {
                    system of CodeSystem.RONIN_TENANT.uri.value!!
                    value of "Test"
                }
            )
        }

        AidboxClient.addResource(patient)
        AidboxClient.addResource(patient2)
        assertThrows<ClientRequestException> {
            EHRDAClient.getResource("DifferentTenant", "Patient", "Test-12345")
        }
        AidboxClient.deleteResource("Patient", patient.id?.value!!)
        AidboxClient.deleteResource("Patient", patient2.id?.value!!)
    }

    @Test
    fun `search works`() {
        val patient = patient {
            id of Id(value = "12345")
            identifier of listOf(
                identifier {
                    system of "system"
                    value of "value"
                },
                identifier {
                    system of CodeSystem.RONIN_TENANT.uri.value!!
                    value of "Test"
                }
            )
        }
        val patient2 = patient {
            id of Id(value = "154321")
            identifier of listOf(
                identifier {
                    system of "system2"
                    value of "value"
                },
                identifier {
                    system of CodeSystem.RONIN_TENANT.uri.value!!
                    value of "Test"
                }
            )
        }

        val patient3 = patient {
            id of Id(value = "3")
            identifier of listOf(
                identifier {
                    system of "system2"
                    value of "value"
                }
            )
        }

        val patient4 = patient {
            id of Id(value = "4567")
            identifier of listOf(
                identifier {
                    system of "system2"
                    value of "value"
                },
                identifier {
                    system of CodeSystem.RONIN_TENANT.uri.value!!
                    value of "Test"
                },
                identifier {
                    system of "cool-system"
                    value of "wow"
                }
            )
        }
        AidboxClient.addResource(patient)
        AidboxClient.addResource(patient2)
        AidboxClient.addResource(patient3)
        AidboxClient.addResource(patient4)
        val response = EHRDAClient.searchResourceIdentifiers(

            "Test",
            "Patient",
            listOf(
                Identifier("system", "value"),
                Identifier("system2", "value")
            )
        )
        AidboxClient.deleteResource("Patient", patient.id?.value!!)
        AidboxClient.deleteResource("Patient", patient2.id?.value!!)
        AidboxClient.deleteResource("Patient", patient3.id?.value!!)
        AidboxClient.deleteResource("Patient", patient4.id?.value!!)

        assertEquals(2, response.size)
        val searchResult1 = response[0]
        val searchResult2 = response[1]

        assertEquals(1, searchResult1.foundResources.size)
        assertEquals("12345", searchResult1.foundResources.first().udpId)

        assertEquals(2, searchResult2.foundResources.size)
        assertEquals("154321", searchResult2.foundResources[0].udpId)
        assertTrue(searchResult2.foundResources[1].identifiers.contains(Identifier("cool-system", "wow")))
    }
}
