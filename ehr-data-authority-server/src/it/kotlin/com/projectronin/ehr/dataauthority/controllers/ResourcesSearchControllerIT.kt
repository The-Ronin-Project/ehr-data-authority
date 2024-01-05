package com.projectronin.ehr.dataauthority.controllers

import com.projectronin.ehr.dataauthority.BaseEHRDataAuthorityIT
import com.projectronin.ehr.dataauthority.models.Identifier
import com.projectronin.ehr.dataauthority.models.IdentifierSearchableResourceTypes
import com.projectronin.ehr.dataauthority.testclients.AidboxClient
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.common.http.request
import com.projectronin.interop.fhir.generators.datatypes.DynamicValues.quantity
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.quantity
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Binary
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.generators.resource.observation.rcdmObservation
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class ResourcesSearchControllerIT : BaseEHRDataAuthorityIT() {
    @Test
    fun `get works`() {
        val patient =
            patient {
                id of Id(value = "Test-12345")
                identifier of
                    listOf(
                        identifier {
                            system of "system"
                            value of "value"
                        },
                        identifier {
                            system of CodeSystem.RONIN_TENANT.uri.value!!
                            value of "Test"
                        },
                    )
                name of
                    listOf(
                        name {
                            family of "Lastnameski"
                        },
                    )
            }
        val patient2 =
            patient {
                id of Id(value = "Test-154321")
                identifier of
                    listOf(
                        identifier {
                            system of "system2"
                            value of "value"
                        },
                        identifier {
                            system of CodeSystem.RONIN_TENANT.uri.value!!
                            value of "Test"
                        },
                    )
            }

        AidboxClient.addResource(patient)
        AidboxClient.addResource(patient2)
        val response = runBlocking { client.getResource("Test", "Patient", "Test-12345") }
        AidboxClient.deleteResource("Patient", patient.id?.value!!)
        AidboxClient.deleteResource("Patient", patient2.id?.value!!)
        val patientResponse = response as Patient
        assertEquals("Lastnameski", patientResponse.name.first().family?.value)
    }

    @Test
    fun `get returns a 404 if requested resource does not exist`() {
        val exception =
            assertThrows<ClientFailureException> {
                val resourceUrl = "$serverUrl/tenants/Test/resources/Patient/Test-fake-not-real-patient"
                val authentication = authenticationService.getAuthentication()

                runBlocking {
                    val response: HttpResponse =
                        httpClient.request("test", resourceUrl) { url ->
                            get(url) {
                                headers {
                                    append(HttpHeaders.Authorization, "Bearer ${authentication.accessToken}")
                                }
                                accept(ContentType.Application.Json)
                                contentType(ContentType.Application.Json)
                            }
                        }
                    response.body()
                }
            }
        assertEquals(HttpStatusCode.NotFound, exception.status)
    }

    @Test
    fun `get fails for tenant mismatch`() {
        val patient =
            patient {
                id of Id(value = "Test-12345")
                identifier of
                    listOf(
                        identifier {
                            system of "system"
                            value of "value"
                        },
                        identifier {
                            system of CodeSystem.RONIN_TENANT.uri.value!!
                            value of "Test"
                        },
                    )
                name of
                    listOf(
                        name {
                            family of "Lastnameski"
                        },
                    )
            }
        val patient2 =
            patient {
                id of Id(value = "Test-154321")
                identifier of
                    listOf(
                        identifier {
                            system of "system2"
                            value of "value"
                        },
                        identifier {
                            system of CodeSystem.RONIN_TENANT.uri.value!!
                            value of "Test"
                        },
                    )
            }

        AidboxClient.addResource(patient)
        AidboxClient.addResource(patient2)
        assertThrows<ClientFailureException> {
            runBlocking {
                client.getResource("DifferentTenant", "Patient", "Test-12345")
            }
        }
        AidboxClient.deleteResource("Patient", patient.id?.value!!)
        AidboxClient.deleteResource("Patient", patient2.id?.value!!)
    }

    @Test
    fun `search works`() {
        val patient =
            patient {
                id of Id(value = "12345")
                identifier of
                    listOf(
                        identifier {
                            system of CodeSystem.RONIN_MRN.uri.value!!
                            value of "value"
                        },
                        identifier {
                            system of CodeSystem.RONIN_TENANT.uri.value!!
                            value of "Test"
                        },
                    )
            }
        val patient2 =
            patient {
                id of Id(value = "154321")
                identifier of
                    listOf(
                        identifier {
                            system of "system2"
                            value of "value"
                        },
                        identifier {
                            system of CodeSystem.RONIN_TENANT.uri.value!!
                            value of "Test"
                        },
                    )
            }

        val patient3 =
            patient {
                id of Id(value = "3")
                identifier of
                    listOf(
                        identifier {
                            system of "system2"
                            value of "value"
                        },
                    )
            }

        val patient4 =
            patient {
                id of Id(value = "4567")
                identifier of
                    listOf(
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
                        },
                    )
            }
        AidboxClient.addResource(patient)
        AidboxClient.addResource(patient2)
        AidboxClient.addResource(patient3)
        AidboxClient.addResource(patient4)
        val response =
            runBlocking {
                client.getResourceIdentifiers(
                    "Test",
                    IdentifierSearchableResourceTypes.Patient,
                    listOf(
                        Identifier(CodeSystem.RONIN_MRN.uri.value!!, "value"),
                        Identifier("system2", "value"),
                    ),
                )
            }
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

    @Test
    fun `search finds no resources`() {
        val searchIdentifier = Identifier(CodeSystem.RONIN_MRN.uri.value!!, "value")
        val response =
            runBlocking {
                client.getResourceIdentifiers(
                    "Test",
                    IdentifierSearchableResourceTypes.Patient,
                    listOf(searchIdentifier),
                )
            }

        assertEquals(1, response.size)
        val searchResult1 = response[0]

        assertEquals(searchIdentifier, searchResult1.searchedIdentifier)
        assertEquals(0, searchResult1.foundResources.size)
    }

    @Test
    fun `ehr da support retrieval of binary`() {
        val response =
            runBlocking {
                client.getResource("Test", "Binary", "Test-7788")
            }
        val binary = response as Binary
        assertEquals("Test-7788", binary.id?.value)
        assertEquals("VGhpcyB3b3Jrcw==", binary.data?.value)
    }

    @Test
    fun `retrieval maintains precision with decimal zero`() {
        val observation =
            rcdmObservation("test") {
                id of "test-12345"
                value of
                    quantity(
                        quantity {
                            value of BigDecimal("60.0")
                        },
                    )
            }
        AidboxClient.addResource(observation)

        val response =
            runBlocking {
                client.getResource("test", "Observation", "test-12345")
            }
        val actualObservation = response as Observation
        assertEquals("test-12345", actualObservation.id!!.value)
        assertEquals(
            "60.0",
            (actualObservation.value!!.value as com.projectronin.interop.fhir.r4.datatype.Quantity).value!!.value!!.toString(),
        )

        AidboxClient.deleteResource("Observation", "test-12345")
    }

    @Disabled("Aidbox precision issue prevents this from working currently")
    @Test
    fun `retrieval maintains precision with trailing zeroes`() {
        val observation =
            rcdmObservation("test") {
                id of "test-12345"
                value of
                    quantity(
                        quantity {
                            value of BigDecimal("0.40")
                        },
                    )
            }
        AidboxClient.addResource(observation)

        val response =
            runBlocking {
                client.getResource("test", "Observation", "test-12345")
            }
        val actualObservation = response as Observation
        assertEquals("test-12345", actualObservation.id!!.value)
        assertEquals(
            "0.40",
            (actualObservation.value!!.value as com.projectronin.interop.fhir.r4.datatype.Quantity).value!!.value!!.toString(),
        )

        AidboxClient.deleteResource("Observation", "test-12345")
    }
}
