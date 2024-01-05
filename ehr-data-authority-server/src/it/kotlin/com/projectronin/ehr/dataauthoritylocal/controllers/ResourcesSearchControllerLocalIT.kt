package com.projectronin.ehr.dataauthoritylocal.controllers

import com.projectronin.ehr.dataauthority.models.Identifier
import com.projectronin.ehr.dataauthority.models.IdentifierSearchableResourceTypes
import com.projectronin.ehr.dataauthoritylocal.BaseEHRDataAuthorityLocalIT
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.common.http.request
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Patient
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResourcesSearchControllerLocalIT : BaseEHRDataAuthorityLocalIT() {
    // ////////////////////////  LOCAL-STORAGE-CLIENT TESTS  //////////////////////////
    @Test
    fun `get works for local storage client`() {
        val patient =
            patient {
                id of Id(value = "ehrda-12345")
                identifier of
                    listOf(
                        identifier {
                            system of "system"
                            value of "value"
                        },
                        identifier {
                            system of CodeSystem.RONIN_TENANT.uri.value!!
                            value of "ehrda"
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
                id of Id(value = "ehrda-154321")
                identifier of
                    listOf(
                        identifier {
                            system of "system2"
                            value of "value"
                        },
                        identifier {
                            system of CodeSystem.RONIN_TENANT.uri.value!!
                            value of "ehrda"
                        },
                    )
            }

        runBlocking { client.addResources("ehrda", listOf(patient, patient2)) }
        val response = runBlocking { client.getResource("ehrda", "Patient", "ehrda-12345") }
        val patientResponse = response as Patient
        assertEquals("Lastnameski", patientResponse.name.first().family?.value)
        runBlocking { client.deleteResource("ehrda", "Patient", "ehrda-12345") }
        runBlocking { client.deleteResource("ehrda", "Patient", "ehrda-154321") }
    }

    @Test
    fun `getResource with local storage client returns a 404 if requested resource does not exist`() {
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
    fun `get fails for tenant mismatch with local client storage`() {
        val patient =
            patient {
                id of Id(value = "ehrda-12345")
                identifier of
                    listOf(
                        identifier {
                            system of "system"
                            value of "value"
                        },
                        identifier {
                            system of CodeSystem.RONIN_TENANT.uri.value!!
                            value of "ehrda"
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
                id of Id(value = "ehrda-154321")
                identifier of
                    listOf(
                        identifier {
                            system of "system2"
                            value of "value"
                        },
                        identifier {
                            system of CodeSystem.RONIN_TENANT.uri.value!!
                            value of "ehrda"
                        },
                    )
            }

        runBlocking { client.addResources("ehrda", listOf(patient, patient2)) }
        assertThrows<ClientFailureException> {
            runBlocking {
                client.getResource("DifferentTenant", "Patient", "ehrda-12345")
            }
        }
    }

    @Test
    fun `search works for local storage client`() {
        val patient =
            patient {
                id of Id(value = "ehrda-154321")
                identifier of
                    listOf(
                        identifier {
                            system of "system2"
                            value of "value"
                        },
                        identifier {
                            system of CodeSystem.RONIN_TENANT.uri.value!!
                            value of "ehrda"
                        },
                    )
            }
        val patient2 =
            patient {
                id of Id(value = "ehrda-12345")
                identifier of
                    listOf(
                        identifier {
                            system of "system1"
                            value of "value"
                        },
                        identifier {
                            system of CodeSystem.RONIN_MRN.uri
                            value of "value"
                        },
                        identifier {
                            system of CodeSystem.RONIN_TENANT.uri.value!!
                            value of "ehrda"
                        },
                    )
            }

        val patient3 =
            patient {
                id of Id(value = "ehrda-4567")
                identifier of
                    listOf(
                        identifier {
                            system of "system2"
                            value of "value"
                        },
                        identifier {
                            system of CodeSystem.RONIN_TENANT.uri.value!!
                            value of "ehrda"
                        },
                        identifier {
                            system of "cool-system"
                            value of "wow"
                        },
                    )
            }

        runBlocking { client.addResources("ehrda", listOf(patient, patient2, patient3)) }
        val response =
            runBlocking {
                client.getResourceIdentifiers(
                    "ehrda",
                    IdentifierSearchableResourceTypes.Patient,
                    listOf(
                        Identifier(CodeSystem.RONIN_MRN.uri.value!!, "value"),
                        Identifier("system2", "value"),
                    ),
                )
            }

        assertEquals(2, response.size)
        val searchResult1 = response[0]
        val searchResult2 = response[1]

        assertEquals(1, searchResult1.foundResources.size)
        assertEquals("ehrda-12345", searchResult1.foundResources.first().udpId)

        assertEquals(2, searchResult2.foundResources.size)
        assertEquals("ehrda-154321", searchResult2.foundResources[0].udpId)
        assertTrue(searchResult2.foundResources[1].identifiers.contains(Identifier("cool-system", "wow")))
        runBlocking { client.deleteResource("ehrda", "Patient", "ehrda-12345") }
        runBlocking { client.deleteResource("ehrda", "Patient", "ehrda-4567") }
        runBlocking { client.deleteResource("ehrda", "Patient", "ehrda-154321") }
    }
}
