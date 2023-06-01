package com.projectronin.ehr.dataauthority.aidbox

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.projectronin.ehr.dataauthority.aidbox.auth.AidboxAuthenticationBroker
import com.projectronin.interop.common.http.exceptions.ClientAuthenticationException
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.common.http.exceptions.ServiceUnavailableException
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.Address
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Coding
import com.projectronin.interop.fhir.r4.datatype.ContactPoint
import com.projectronin.interop.fhir.r4.datatype.HumanName
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.Narrative
import com.projectronin.interop.fhir.r4.datatype.Period
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.DateTime
import com.projectronin.interop.fhir.r4.datatype.primitive.Decimal
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRBoolean
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.AvailableTime
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.LocationHoursOfOperation
import com.projectronin.interop.fhir.r4.resource.LocationPosition
import com.projectronin.interop.fhir.r4.resource.NotAvailable
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.resource.PractitionerRole
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.fhir.r4.valueset.ContactPointSystem
import com.projectronin.interop.fhir.r4.valueset.DayOfWeek
import com.projectronin.interop.fhir.r4.valueset.LocationMode
import com.projectronin.interop.fhir.r4.valueset.LocationStatus
import com.projectronin.interop.fhir.r4.valueset.NarrativeStatus
import com.projectronin.interop.fhir.util.asCode
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AidboxClientTest {
    private val urlRest = "http://localhost/8888"
    private val urlBatchUpsert = "$urlRest/fhir"
    private val practitioner1 = Practitioner(
        id = Id("cmjones"),
        identifier = listOf(
            Identifier(system = CodeSystem.NPI.uri, value = "third".asFHIR())
        ),
        name = listOf(HumanName(family = "Jones".asFHIR(), given = listOf("Cordelia", "May").asFHIR()))
    )
    private val practitioner2 = Practitioner(
        id = Id("rallyr"),
        identifier = listOf(
            Identifier(system = CodeSystem.NPI.uri, value = "second".asFHIR())
        ),
        name = listOf(HumanName(family = "Llyr".asFHIR(), given = listOf("Regan", "Anne").asFHIR()))
    )
    private val practitioner3 = Practitioner(
        id = Id("gwalsh"),
        identifier = listOf(
            Identifier(system = CodeSystem.NPI.uri, value = "first".asFHIR())
        ),
        name = listOf(HumanName(family = "Walsh".asFHIR(), given = listOf("Goneril").asFHIR()))
    )
    private val location1 = Location(
        id = Id("12345"),
        language = Code("en-US"),
        text = Narrative(status = NarrativeStatus.GENERATED.asCode(), div = "div".asFHIR()),
        identifier = listOf(
            Identifier(system = CodeSystem.NPI.uri, value = "id5".asFHIR())
        ),
        mode = LocationMode.INSTANCE.asCode(),
        status = LocationStatus.ACTIVE.asCode(),
        name = "My Office".asFHIR(),
        alias = listOf("Guest Room").asFHIR(),
        description = "Sun Room".asFHIR(),
        type = listOf(
            CodeableConcept(
                text = "Diagnostic".asFHIR(),
                coding = listOf(
                    Coding(
                        code = Code("DX"),
                        system = Uri(value = "http://terminology.hl7.org/ValueSet/v3-ServiceDeliveryLocationRoleType")
                    )
                )
            )
        ),
        telecom = listOf(ContactPoint(system = ContactPointSystem.PHONE.asCode(), value = "8675309".asFHIR())),
        address = Address(country = "USA".asFHIR()),
        physicalType = CodeableConcept(
            text = "Room".asFHIR(),
            coding = listOf(
                Coding(
                    code = Code("ro"),
                    system = Uri(value = "http://terminology.hl7.org/CodeSystem/location-physical-type")
                )
            )
        ),
        position = LocationPosition(longitude = Decimal(13.81531), latitude = Decimal(66.077132)),
        hoursOfOperation = listOf(
            LocationHoursOfOperation(
                daysOfWeek = listOf(
                    DayOfWeek.SATURDAY.asCode(),
                    DayOfWeek.SUNDAY.asCode()
                ),
                allDay = FHIRBoolean.TRUE
            )
        ),
        availabilityExceptions = "Call for details".asFHIR(),
        endpoint = listOf(Reference(reference = "Endpoint/4321".asFHIR()))
    )
    private val location2 = Location(
        id = Id("12346"),
        language = Code("en-US"),
        text = Narrative(status = NarrativeStatus.GENERATED.asCode(), div = "div".asFHIR()),
        identifier = listOf(
            Identifier(system = CodeSystem.NPI.uri, value = "id6".asFHIR())
        ),
        mode = LocationMode.INSTANCE.asCode(),
        status = LocationStatus.ACTIVE.asCode(),
        name = "Back Study".asFHIR(),
        alias = listOf("Studio").asFHIR(),
        description = "Game Room".asFHIR(),
        type = listOf(
            CodeableConcept(
                text = "Diagnostic".asFHIR(),
                coding = listOf(
                    Coding(
                        code = Code("DX"),
                        system = Uri(value = "http://terminology.hl7.org/ValueSet/v3-ServiceDeliveryLocationRoleType")
                    )
                )
            )
        ),
        telecom = listOf(ContactPoint(system = ContactPointSystem.PHONE.asCode(), value = "123-456-7890".asFHIR())),
        address = Address(country = "USA".asFHIR()),
        physicalType = CodeableConcept(
            text = "Room".asFHIR(),
            coding = listOf(
                Coding(
                    code = Code("ro"),
                    system = Uri(value = "http://terminology.hl7.org/CodeSystem/location-physical-type")
                )
            )
        ),
        hoursOfOperation = listOf(
            LocationHoursOfOperation(
                daysOfWeek = listOf(DayOfWeek.TUESDAY.asCode()),
                allDay = FHIRBoolean.TRUE
            )
        ),
        availabilityExceptions = "By appointment".asFHIR(),
        endpoint = listOf(Reference(reference = "Endpoint/4322".asFHIR()))
    )
    private val practitionerRole1 = PractitionerRole(
        id = Id("12347"),
        identifier = listOf(
            Identifier(system = CodeSystem.NPI.uri, value = "id3".asFHIR())
        ),
        active = FHIRBoolean.TRUE,
        period = Period(end = DateTime("2022")),
        practitioner = Reference(reference = "Practitioner/cmjones".asFHIR()),
        location = listOf(Reference(reference = "Location/12345".asFHIR())),
        healthcareService = listOf(Reference(reference = "HealthcareService/3456".asFHIR())),
        availableTime = listOf(AvailableTime(allDay = FHIRBoolean.FALSE)),
        notAvailable = listOf(NotAvailable(description = "Not available now".asFHIR())),
        availabilityExceptions = "exceptions".asFHIR(),
        endpoint = listOf(Reference(reference = "Endpoint/1357".asFHIR()))
    )
    private val practitionerRole2 = PractitionerRole(
        id = Id("12348"),
        identifier = listOf(
            Identifier(system = CodeSystem.NPI.uri, value = "id4".asFHIR())
        ),
        active = FHIRBoolean.TRUE,
        period = Period(end = DateTime("2022")),
        practitioner = Reference(reference = "Practitioner/rallyr".asFHIR()),
        location = listOf(Reference(reference = "Location/12346".asFHIR())),
        healthcareService = listOf(Reference(reference = "HealthcareService/3456".asFHIR())),
        availableTime = listOf(AvailableTime(allDay = FHIRBoolean.TRUE)),
        notAvailable = listOf(NotAvailable(description = "Available now".asFHIR())),
        availabilityExceptions = "No exceptions".asFHIR(),
        endpoint = listOf(
            Reference(reference = "Endpoint/1358".asFHIR()),
            Reference(reference = "Endpoint/1359".asFHIR())
        )
    )
    private val practitioners = listOf(practitioner1, practitioner2)
    private val locations = listOf(location1, location2)
    private val practitionerRoles = listOf(practitionerRole1, practitionerRole2)
    private val oneMissingTargetRoles: List<Resource<*>> = practitioners + listOf(location1) + practitionerRoles
    private val fullRoles: List<Resource<*>> = practitioners + locations + practitionerRoles
    private val unrelatedResourceInList: List<Resource<*>> =
        listOf(practitioner3) + practitioners + locations + practitionerRoles

    @Test
    fun `resource retrieve test`() {
        val aidboxClient = createClient("", "$urlRest/fhir/Patient/123")
        val actual: HttpResponse = runBlocking {
            aidboxClient.getResource("Patient", "123")
        }
        assertEquals(HttpStatusCode.OK, actual.status)
    }

    @Test
    fun `aidbox batch upsert of 2 Practitioner (RoninResource), response 200`() {
        val expectedResponseStatus = HttpStatusCode.OK
        val aidboxClient = createClient(expectedUrl = urlBatchUpsert, responseStatus = expectedResponseStatus)
        val actualResponse: HttpResponse = runBlocking {
            aidboxClient.batchUpsert(practitioners)
        }
        assertEquals(expectedResponseStatus, actualResponse.status)
    }

    @Test
    fun `aidbox batch upsert of 2 Location (R4Resource), response 200`() {
        val expectedResponseStatus = HttpStatusCode.OK
        val aidboxClient = createClient(expectedUrl = urlBatchUpsert, responseStatus = expectedResponseStatus)
        val actualResponse: HttpResponse = runBlocking {
            aidboxClient.batchUpsert(locations)
        }
        assertEquals(expectedResponseStatus, actualResponse.status)
    }

    @Test
    fun `aidbox batch upsert of all related FHIRResources (RoninResource and R4Resource), response 200`() {
        val expectedResponseStatus = HttpStatusCode.OK
        val aidboxClient = createClient(expectedUrl = urlBatchUpsert, responseStatus = expectedResponseStatus)
        val actualResponse: HttpResponse = runBlocking {
            aidboxClient.batchUpsert(fullRoles)
        }
        assertEquals(expectedResponseStatus, actualResponse.status)
    }

    @Test
    fun `aidbox batch upsert of mixed, some unrelated FHIRResources (RoninResource and R4Resource), response 200`() {
        val expectedResponseStatus = HttpStatusCode.OK
        val aidboxClient = createClient(expectedUrl = urlBatchUpsert, responseStatus = expectedResponseStatus)
        val actualResponse: HttpResponse = runBlocking {
            aidboxClient.batchUpsert(unrelatedResourceInList)
        }
        assertEquals(expectedResponseStatus, actualResponse.status)
    }

    @Test
    fun `aidbox batch upsert of PractitionerRole with all reference targets missing, response 422`() {
        val expectedResponseStatus = HttpStatusCode.UnprocessableEntity
        val aidboxClient = createClient(expectedUrl = urlBatchUpsert, responseStatus = expectedResponseStatus)
        assertThrows(ClientFailureException::class.java) {
            runBlocking {
                aidboxClient.batchUpsert(practitionerRoles)
            }
        }
    }

    @Test
    fun `aidbox batch upsert of PractitionerRole with only 1 reference target missing, response 422`() {
        val expectedResponseStatus = HttpStatusCode.UnprocessableEntity
        val aidboxClient = createClient(expectedUrl = urlBatchUpsert, responseStatus = expectedResponseStatus)
        assertThrows(ClientFailureException::class.java) {
            runBlocking {
                aidboxClient.batchUpsert(oneMissingTargetRoles)
            }
        }
    }

    @Test
    fun `aidbox batch upsert of 2 Practitioner resources, response 1xx`() {
        val expectedResponseStatus = HttpStatusCode.Continue
        val aidboxClient =
            createClient(expectedUrl = urlBatchUpsert, responseStatus = expectedResponseStatus)
        val actualResponse: HttpResponse = runBlocking {
            aidboxClient.batchUpsert(practitioners)
        }
        assertEquals(expectedResponseStatus, actualResponse.status)
    }

    @Test
    fun `aidbox batch upsert of 2 Practitioner resources, response 2xx`() {
        val expectedResponseStatus = HttpStatusCode.Accepted
        val aidboxClient = createClient(expectedUrl = urlBatchUpsert, responseStatus = expectedResponseStatus)
        val actualResponse: HttpResponse = runBlocking {
            aidboxClient.batchUpsert(practitioners)
        }
        assertEquals(expectedResponseStatus, actualResponse.status)
    }

    @Test
    fun `aidbox batch upsert of 2 Practitioner resources, response 4xx exception`() {
        val expectedResponseStatus = HttpStatusCode.Unauthorized
        val aidboxClient = createClient(expectedUrl = urlBatchUpsert, responseStatus = expectedResponseStatus)
        assertThrows(ClientAuthenticationException::class.java) {
            runBlocking {
                aidboxClient.batchUpsert(practitioners)
            }
        }
    }

    @Test
    fun `aidbox batch upsert of 2 Practitioner resources, response 5xx exception`() {
        val expectedResponseStatus = HttpStatusCode.ServiceUnavailable
        assertThrows(ServiceUnavailableException::class.java) {
            val aidboxClient = createClient(expectedUrl = urlBatchUpsert, responseStatus = expectedResponseStatus)
            runBlocking {
                aidboxClient.batchUpsert(practitioners)
            }
        }
    }

    @Test
    fun `search works for aidbox`() {
        val tenantIdentifier = "${CodeSystem.RONIN_TENANT.uri.value}|test".encodeURLPathPart()
        val searchToken = "system|value"
        val expectedUrl =
            "$urlRest/fhir/Patient?identifier=$tenantIdentifier&identifier=${searchToken.encodeURLPathPart()}"
        val aidboxClient = createClient("", expectedUrl)
        val actual: HttpResponse = runBlocking {
            aidboxClient.searchForResources("Patient", "test", searchToken)
        }
        assertEquals(HttpStatusCode.OK, actual.status)
    }

    @Test
    fun `delete works for aidbox`() {
        val resourceType = "Patient"
        val udpId = "tenant-123456"

        val aidboxClient =
            createClient(expectedUrl = "$urlRest/fhir/$resourceType/$udpId")
        val response = runBlocking {
            aidboxClient.deleteResource(resourceType, udpId)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun createClient(
        expectedBody: String = "",
        expectedUrl: String,
        baseUrl: String = urlRest,
        responseStatus: HttpStatusCode = HttpStatusCode.OK
    ): AidboxClient {
        val authenticationBroker = mockk<AidboxAuthenticationBroker> {
            every { getAuthentication() } returns mockk {
                every { tokenType } returns "Bearer"
                every { accessToken } returns "Auth-String"
            }
        }

        val mockEngine = MockEngine { request ->
            assertEquals(expectedUrl, request.url.toString())
            if (expectedBody != "") {
                assertEquals(expectedBody, String(request.body.toByteArray())) // see if this is a JSON string/stream
            }
            assertEquals("Bearer Auth-String", request.headers["Authorization"])
            respond(
                content = "",
                status = responseStatus,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                jackson {
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                }
            }
        }

        return AidboxClient(httpClient, baseUrl, authenticationBroker)
    }
}
