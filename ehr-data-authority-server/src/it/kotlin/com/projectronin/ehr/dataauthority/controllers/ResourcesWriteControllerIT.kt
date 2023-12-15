package com.projectronin.ehr.dataauthority.controllers

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectronin.ehr.dataauthority.BaseEHRDataAuthorityIT
import com.projectronin.ehr.dataauthority.models.ModificationType
import com.projectronin.ehr.dataauthority.testclients.AidboxClient
import com.projectronin.ehr.dataauthority.testclients.DBClient
import com.projectronin.ehr.dataauthority.testclients.KafkaClient
import com.projectronin.ehr.dataauthority.testclients.ValidationClient
import com.projectronin.fhir.r4.Patient
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.generators.datatypes.DynamicValues
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.quantity
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.CodeableConcepts
import com.projectronin.interop.fhir.r4.datatype.Meta
import com.projectronin.interop.fhir.r4.datatype.Quantity
import com.projectronin.interop.fhir.r4.datatype.primitive.Canonical
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.ronin.generators.resource.observation.rcdmObservation
import com.projectronin.interop.fhir.ronin.generators.resource.rcdmPatient
import com.projectronin.interop.fhir.util.asCode
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class ResourcesWriteControllerIT : BaseEHRDataAuthorityIT() {
    override val resources =
        mapOf(
            "patient" to Patient::class,
            "observation" to com.projectronin.fhir.r4.Observation::class
        )

    @Test
    fun `adds resource when new`() {
        val patient = rcdmPatient("tenant") {
            id of Id("tenant-12345")
        }

        val response = runBlocking { client.addResources("tenant", listOf(patient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-12345", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "tenant-12345")
        assertEquals("tenant-12345", aidboxP.id!!.value)
        AidboxClient.deleteResource("Patient", "tenant-12345")

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertEquals(patient.copy(meta = null).consistentHashCode(), hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(1, kafkaEvents.size)
        assertTrue(kafkaEvents[0].type.endsWith("patient.create"))

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)
    }

    @Test
    fun `updates resource when changed`() {
        val originalPatient = rcdmPatient("tenant") {
            id of Id("tenant-12345")
            gender of AdministrativeGender.FEMALE.asCode()
        }
        val addedPatient = AidboxClient.addResource(originalPatient)
        DBClient.setHashValue(
            "tenant",
            "Patient",
            "tenant-12345",
            originalPatient.copy(meta = null).consistentHashCode()
        )

        val updatedPatient = originalPatient.copy(gender = AdministrativeGender.MALE.asCode())

        val response = runBlocking { client.addResources("tenant", listOf(updatedPatient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-12345", success1.resourceId)
        assertEquals(ModificationType.UPDATED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "tenant-12345")
        assertEquals("tenant-12345", aidboxP.id!!.value)
        assertNotEquals(addedPatient.meta!!.lastUpdated!!, aidboxP.meta!!.lastUpdated!!)
        AidboxClient.deleteResource("Patient", "tenant-12345")

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertEquals(updatedPatient.copy(meta = null).consistentHashCode(), hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(1, kafkaEvents.size)
        assertTrue(kafkaEvents[0].type.endsWith("patient.update"))

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)
    }

    @Test
    fun `updates resource when same hash code, but changed`() {
        val originalPatient = rcdmPatient("tenant") {
            id of Id("tenant-12345")
            gender of AdministrativeGender.FEMALE.asCode()
        }
        val addedPatient = AidboxClient.addResource(originalPatient)

        val updatedPatient = originalPatient.copy(gender = AdministrativeGender.MALE.asCode())

        // Use the updated patient's hashCode.
        DBClient.setHashValue(
            "tenant",
            "Patient",
            "tenant-12345",
            updatedPatient.copy(meta = null).consistentHashCode()
        )

        val response = runBlocking { client.addResources("tenant", listOf(updatedPatient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-12345", success1.resourceId)
        assertEquals(ModificationType.UPDATED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "tenant-12345")
        assertEquals("tenant-12345", aidboxP.id!!.value)
        assertNotEquals(addedPatient.meta!!.lastUpdated!!, aidboxP.meta!!.lastUpdated!!)
        AidboxClient.deleteResource("Patient", "tenant-12345")

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertEquals(updatedPatient.copy(meta = null).consistentHashCode(), hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(1, kafkaEvents.size)
        assertTrue(kafkaEvents[0].type.endsWith("patient.update"))

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)
    }

    @Test
    fun `returns success when resource is unchanged`() {
        val originalPatient = rcdmPatient("tenant") {
            id of Id("tenant-12345")
            gender of AdministrativeGender.FEMALE.asCode()
        }
        val addedPatient = AidboxClient.addResource(originalPatient)
        DBClient.setHashValue(
            "tenant",
            "Patient",
            "tenant-12345",
            originalPatient.copy(meta = null).consistentHashCode()
        )

        val response = runBlocking { client.addResources("tenant", listOf(originalPatient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-12345", success1.resourceId)
        assertEquals(ModificationType.UNMODIFIED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "tenant-12345")
        assertEquals("tenant-12345", aidboxP.id!!.value)
        assertEquals(addedPatient.meta!!.lastUpdated!!, aidboxP.meta!!.lastUpdated!!)
        AidboxClient.deleteResource("Patient", "tenant-12345")

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertEquals(originalPatient.copy(meta = null).consistentHashCode(), hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(0, kafkaEvents.size)

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)
    }

    @Test
    fun `multi resource - pass`() {
        val patient1 = rcdmPatient("tenant") {
            id of Id("tenant-12345")
        }
        val patient2 = rcdmPatient("tenant") {
            id of Id("tenant-67890")
        }

        val response = runBlocking { client.addResources("tenant", listOf(patient1, patient2)) }
        assertEquals(2, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-12345", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val success2 = response.succeeded[1]
        assertEquals("Patient", success2.resourceType)
        assertEquals("tenant-67890", success2.resourceId)
        assertEquals(ModificationType.CREATED, success2.modificationType)

        val aidboxP1 = AidboxClient.getResource("Patient", "tenant-12345")
        val aidboxP2 = AidboxClient.getResource("Patient", "tenant-67890")
        assertEquals("tenant-12345", aidboxP1.id!!.value)
        assertEquals("tenant-67890", aidboxP2.id!!.value)
        AidboxClient.deleteResource("Patient", "tenant-12345")
        AidboxClient.deleteResource("Patient", "tenant-67890")

        val hashP1 = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertEquals(patient1.copy(meta = null).consistentHashCode(), hashP1)
        val hashP2 = DBClient.getStoredHashValue("tenant", "Patient", "tenant-67890")
        assertEquals(patient2.copy(meta = null).consistentHashCode(), hashP2)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(2, kafkaEvents.size)
        assertTrue(kafkaEvents[0].type.endsWith("patient.create"))
        assertTrue(kafkaEvents[1].type.endsWith("patient.create"))

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)
    }

    @Test
    fun `invalid resource returns failure and adds to validation service`() {
        // Not using the RCDM Patient to create an easier to fail resource.
        // We need to have the Meta or else we won't report it to the Validation Service.
        val patient = patient {
            id of Id("tenant-12345")
            meta of Meta(profile = listOf(Canonical("http://projectronin.io/fhir/StructureDefinition/ronin-patient")))
            identifier plus identifier {
                system of CodeSystem.RONIN_TENANT.uri
                value of "tenant"
                type of CodeableConcepts.RONIN_TENANT
            }
        }

        val response = runBlocking { client.addResources("tenant", listOf(patient)) }
        assertEquals(0, response.succeeded.size)
        assertEquals(1, response.failed.size)

        val failure1 = response.failed[0]
        assertEquals("Patient", failure1.resourceType)
        assertEquals("tenant-12345", failure1.resourceId)
        assertNotNull(failure1.error)

        val aidboxP = runCatching { AidboxClient.getResource("Patient", "tenant-12345") }.getOrNull()
        assertNull(aidboxP)

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertNull(hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(0, kafkaEvents.size)

        val validationResources = ValidationClient.getResources()
        assertEquals(1, validationResources.size)
        assertEquals("tenant", validationResources[0].organizationId)
        assertEquals("Patient", validationResources[0].resourceType)
    }

    @Test
    fun `tenant mismatch just fails`() {
        val patient = rcdmPatient("tenant") {
            id of Id("tenant-12345")
        }

        HttpStatusCode.BadRequest
        val response =
            assertThrows<ClientFailureException> { runBlocking { client.addResources("notTenant", listOf(patient)) } }
        assertTrue(response.message!!.startsWith("Received 400"))

        val aidboxP = runCatching { AidboxClient.getResource("Patient", "tenant-12345") }.getOrNull()
        assertNull(aidboxP)

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertNull(hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(0, kafkaEvents.size)

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)
    }

    @Test
    fun `repeat requests result in unmodified`() {
        // First we do a full new request.
        val patient = rcdmPatient("tenant") {
            id of Id("tenant-12345")
        }

        val response = runBlocking { client.addResources("tenant", listOf(patient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-12345", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "tenant-12345")
        assertEquals("tenant-12345", aidboxP.id!!.value)

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertEquals(patient.copy(meta = null).consistentHashCode(), hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(1, kafkaEvents.size)
        assertTrue(kafkaEvents[0].type.endsWith("patient.create"))

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)

        // Now we do it all again with the same request.

        val response2 = runBlocking { client.addResources("tenant", listOf(patient)) }
        assertEquals(1, response2.succeeded.size)
        assertEquals(0, response2.failed.size)

        val success2 = response2.succeeded[0]
        assertEquals("Patient", success2.resourceType)
        assertEquals("tenant-12345", success2.resourceId)
        assertEquals(ModificationType.UNMODIFIED, success2.modificationType)

        val kafkaEvents2 = KafkaClient.readEvents("patient")
        assertEquals(0, kafkaEvents2.size)

        val validationResources2 = ValidationClient.getResources()
        assertEquals(0, validationResources2.size)

        AidboxClient.deleteResource("Patient", "tenant-12345")
    }

    @Test
    fun `same resource with different source is considered unmodified`() {
        val patient = rcdmPatient("tenant") {
            id of Id("tenant-12345")
        }

        val response = runBlocking { client.addResources("tenant", listOf(patient)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Patient", success1.resourceType)
        assertEquals("tenant-12345", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Patient", "tenant-12345")
        assertEquals("tenant-12345", aidboxP.id!!.value)

        val hashP = DBClient.getStoredHashValue("tenant", "Patient", "tenant-12345")
        assertEquals(patient.copy(meta = null).consistentHashCode(), hashP)

        val kafkaEvents = KafkaClient.readEvents("patient")
        assertEquals(1, kafkaEvents.size)
        assertTrue(kafkaEvents[0].type.endsWith("patient.create"))

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)

        val patient2 = patient.copy(meta = patient.meta?.copy(source = Uri("some-other-source")))

        val response2 = runBlocking { client.addResources("tenant", listOf(patient2)) }
        assertEquals(1, response2.succeeded.size)
        assertEquals(0, response2.failed.size)

        val success2 = response2.succeeded[0]
        assertEquals("Patient", success2.resourceType)
        assertEquals("tenant-12345", success2.resourceId)
        assertEquals(ModificationType.UNMODIFIED, success2.modificationType)

        AidboxClient.deleteResource("Patient", "tenant-12345")
    }

    @Test
    fun `write maintains precision with trailing zeroes`() {
        val observation = rcdmObservation("test") {
            id of "test-12345"
            value of DynamicValues.quantity(
                quantity {
                    value of BigDecimal("0.40")
                }
            )
        }

        val response = runBlocking { client.addResources("test", listOf(observation)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Observation", success1.resourceType)
        assertEquals("test-12345", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Observation", "test-12345") as Observation
        assertEquals("test-12345", aidboxP.id!!.value)
        // TODO: Blocked by Aidbox issues
        // assertEquals("0.40", (aidboxP.value!!.value as Quantity).value!!.value!!.toString())

        val kafkaEvents = KafkaClient.readEvents("observation")
        assertEquals(1, kafkaEvents.size)
        assertTrue(kafkaEvents[0].type.endsWith("observation.create"))

        val eventedObservation = kafkaEvents[0].data as com.projectronin.fhir.r4.Observation
        assertEquals("0.40", eventedObservation.valueQuantity?.value?.toString())

        val hashP = DBClient.getStoredHashValue("test", "Observation", "test-12345")
        assertEquals(observation.copy(meta = null).consistentHashCode(), hashP)

        AidboxClient.deleteResource("Observation", "test-12345")
    }

    @Test
    fun `verify Observations are working properly`() {
        val observation = JacksonManager.objectMapper.readValue<Observation>(observationJson)

        val response = runBlocking { client.addResources("ggwadc8y", listOf(observation)) }
        assertEquals(1, response.succeeded.size)
        assertEquals(0, response.failed.size)

        val success1 = response.succeeded[0]
        assertEquals("Observation", success1.resourceType)
        assertEquals("ggwadc8y-L-197322448", success1.resourceId)
        assertEquals(ModificationType.CREATED, success1.modificationType)

        val aidboxP = AidboxClient.getResource("Observation", "ggwadc8y-L-197322448") as Observation
        assertEquals(observation.copy(meta = null), (aidboxP as Observation).copy(meta = null))
        assertEquals("ggwadc8y-L-197322448", aidboxP.id!!.value)
        assertEquals("60.0", (aidboxP.value!!.value as Quantity).value!!.value!!.toString())

        val hashP = DBClient.getStoredHashValue("ggwadc8y", "Observation", "ggwadc8y-L-197322448")
        assertEquals(observation.copy(meta = null).consistentHashCode(), hashP)

        val kafkaEvents = KafkaClient.readEvents("observation")
        assertEquals(1, kafkaEvents.size)
        assertTrue(kafkaEvents[0].type.endsWith("observation.create"))

        val validationResources = ValidationClient.getResources()
        assertEquals(0, validationResources.size)

        val response2 = runBlocking { client.addResources("ggwadc8y", listOf(observation)) }
        assertEquals(1, response2.succeeded.size)
        assertEquals(0, response2.failed.size)

        val success2 = response2.succeeded[0]
        assertEquals("Observation", success2.resourceType)
        assertEquals("ggwadc8y-L-197322448", success2.resourceId)
        assertEquals(ModificationType.UNMODIFIED, success2.modificationType)

        AidboxClient.deleteResource("Observation", "ggwadc8y-L-197322448")
    }

    private val observationJson = """
        {
          "resourceType": "Observation",
          "id": "ggwadc8y-L-197322448",
          "meta": {
            "versionId": "1",
            "lastUpdated": "2023-08-25T18:31:28.000Z",
            "source": "https://objectstorage.us-phoenix-1.oraclecloud.com/n/idoll6i6jmjd/b/stage-data-lake-bronze/o/raw_data_response/tenant_id=ggwadc8y/transaction_id/75b66d06-18e2-428d-afe9-c3c45aa4c0b4",
            "profile": [
              "http://projectronin.io/fhir/StructureDefinition/ronin-observationLaboratoryResult"
            ]
          },
          "text": {
            "status": "generated",
            "div": "<div xmlns=\"http://www.w3.org/1999/xhtml\"><p><b>Observation</b></p><p><b>Patient Id</b>: 12848132</p><p><b>Status</b>: Final</p><p><b>Categories</b>: Laboratory</p><p><b>Code</b>: Neutro Auto</p><p><b>Result</b>: 60.0 %</p><p><b>Interpretation</b>: Normal</p><p><b>Effective Date</b>: Aug 15, 2023  1:15 P.M. CDT</p><p><b>Reference Range</b>: 42.0-75.0 %</p></div>"
          },
          "extension": [
            {
              "url": "http://projectronin.io/fhir/StructureDefinition/Extension/tenant-sourceObservationCode",
              "valueCodeableConcept": {
                "coding": [
                  {
                    "system": "https://fhir.cerner.com/e8a84236-c258-4952-98b7-a6ff8a9c587a/codeSet/72",
                    "code": "20136435",
                    "display": "Neutro Auto",
                    "userSelected": true
                  },
                  {
                    "system": "http://loinc.org",
                    "code": "770-8",
                    "userSelected": false
                  }
                ],
                "text": "Neutro Auto"
              }
            }
          ],
          "identifier": [
            {
              "system": "https://fhir.cerner.com/ceuuid",
              "value": "CE87caf4b7-9397-4667-9897-702218017c9e-197322448-2023082518312800"
            },
            {
              "type": {
                "coding": [
                  {
                    "system": "http://projectronin.com/id/fhir",
                    "code": "FHIR ID",
                    "display": "FHIR Identifier"
                  }
                ],
                "text": "FHIR Identifier"
              },
              "system": "http://projectronin.com/id/fhir",
              "value": "L-197322448"
            },
            {
              "type": {
                "coding": [
                  {
                    "system": "http://projectronin.com/id/tenantId",
                    "code": "TID",
                    "display": "Ronin-specified Tenant Identifier"
                  }
                ],
                "text": "Ronin-specified Tenant Identifier"
              },
              "system": "http://projectronin.com/id/tenantId",
              "value": "ggwadc8y"
            },
            {
              "type": {
                "coding": [
                  {
                    "system": "http://projectronin.com/id/dataAuthorityId",
                    "code": "DAID",
                    "display": "Data Authority Identifier"
                  }
                ],
                "text": "Data Authority Identifier"
              },
              "system": "http://projectronin.com/id/dataAuthorityId",
              "value": "EHR Data Authority"
            }
          ],
          "basedOn": [
            {
              "reference": "ServiceRequest/ggwadc8y-310456463",
              "type": "ServiceRequest",
              "_type": {
                "extension": [
                  {
                    "url": "http://projectronin.io/fhir/StructureDefinition/Extension/ronin-dataAuthorityIdentifier",
                    "valueIdentifier": {
                      "type": {
                        "coding": [
                          {
                            "system": "http://projectronin.com/id/dataAuthorityId",
                            "code": "DAID",
                            "display": "Data Authority Identifier"
                          }
                        ],
                        "text": "Data Authority Identifier"
                      },
                      "system": "http://projectronin.com/id/dataAuthorityId",
                      "value": "EHR Data Authority"
                    }
                  }
                ]
              }
            }
          ],
          "status": "final",
          "category": [
            {
              "coding": [
                {
                  "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                  "code": "laboratory",
                  "display": "Laboratory",
                  "userSelected": false
                }
              ],
              "text": "Laboratory"
            }
          ],
          "code": {
            "coding": [
              {
                "system": "http://loinc.org",
                "version": "2.74",
                "code": "770-8",
                "display": "Neutrophils/100 leukocytes in Blood by Automated count"
              }
            ],
            "text": "Neutro Auto"
          },
          "subject": {
            "reference": "Patient/ggwadc8y-12848132",
            "type": "Patient",
            "_type": {
              "extension": [
                {
                  "url": "http://projectronin.io/fhir/StructureDefinition/Extension/ronin-dataAuthorityIdentifier",
                  "valueIdentifier": {
                    "type": {
                      "coding": [
                        {
                          "system": "http://projectronin.com/id/dataAuthorityId",
                          "code": "DAID",
                          "display": "Data Authority Identifier"
                        }
                      ],
                      "text": "Data Authority Identifier"
                    },
                    "system": "http://projectronin.com/id/dataAuthorityId",
                    "value": "EHR Data Authority"
                  }
                }
              ]
            }
          },
          "encounter": {
            "reference": "Encounter/ggwadc8y-97994060",
            "type": "Encounter",
            "_type": {
              "extension": [
                {
                  "url": "http://projectronin.io/fhir/StructureDefinition/Extension/ronin-dataAuthorityIdentifier",
                  "valueIdentifier": {
                    "type": {
                      "coding": [
                        {
                          "system": "http://projectronin.com/id/dataAuthorityId",
                          "code": "DAID",
                          "display": "Data Authority Identifier"
                        }
                      ],
                      "text": "Data Authority Identifier"
                    },
                    "system": "http://projectronin.com/id/dataAuthorityId",
                    "value": "EHR Data Authority"
                  }
                }
              ]
            }
          },
          "effectiveDateTime": "2023-08-15T18:15:01.000Z",
          "issued": "2023-08-25T18:31:28.000Z",
          "performer": [
            {
              "extension": [
                {
                  "url": "http://hl7.org/fhir/StructureDefinition/event-performerFunction",
                  "valueCodeableConcept": {
                    "coding": [
                      {
                        "system": "http://terminology.hl7.org/CodeSystem/v3-ParticipationType",
                        "code": "LA",
                        "display": "legal authenticator"
                      }
                    ],
                    "text": "legal authenticator"
                  }
                },
                {
                  "url": "http://hl7.org/fhir/StructureDefinition/event-performerFunction",
                  "valueCodeableConcept": {
                    "coding": [
                      {
                        "system": "http://terminology.hl7.org/CodeSystem/v3-ParticipationType",
                        "code": "PPRF",
                        "display": "primary performer"
                      }
                    ],
                    "text": "primary performer"
                  }
                }
              ],
              "reference": "Practitioner/ggwadc8y-12842135",
              "type": "Practitioner",
              "_type": {
                "extension": [
                  {
                    "url": "http://projectronin.io/fhir/StructureDefinition/Extension/ronin-dataAuthorityIdentifier",
                    "valueIdentifier": {
                      "type": {
                        "coding": [
                          {
                            "system": "http://projectronin.com/id/dataAuthorityId",
                            "code": "DAID",
                            "display": "Data Authority Identifier"
                          }
                        ],
                        "text": "Data Authority Identifier"
                      },
                      "system": "http://projectronin.com/id/dataAuthorityId",
                      "value": "EHR Data Authority"
                    }
                  }
                ]
              }
            },
            {
              "extension": [
                {
                  "url": "http://hl7.org/fhir/StructureDefinition/event-performerFunction",
                  "valueCodeableConcept": {
                    "coding": [
                      {
                        "system": "http://terminology.hl7.org/CodeSystem/v2-0912",
                        "code": "OP",
                        "display": "Ordering Provider"
                      }
                    ],
                    "text": "Ordering Provider"
                  }
                }
              ],
              "reference": "Practitioner/ggwadc8y-763923",
              "type": "Practitioner",
              "_type": {
                "extension": [
                  {
                    "url": "http://projectronin.io/fhir/StructureDefinition/Extension/ronin-dataAuthorityIdentifier",
                    "valueIdentifier": {
                      "type": {
                        "coding": [
                          {
                            "system": "http://projectronin.com/id/dataAuthorityId",
                            "code": "DAID",
                            "display": "Data Authority Identifier"
                          }
                        ],
                        "text": "Data Authority Identifier"
                      },
                      "system": "http://projectronin.com/id/dataAuthorityId",
                      "value": "EHR Data Authority"
                    }
                  }
                ]
              }
            }
          ],
          "valueQuantity": {
            "value": 60.0,
            "unit": "%",
            "system": "http://unitsofmeasure.org",
            "code": "%"
          },
          "interpretation": [
            {
              "coding": [
                {
                  "system": "https://fhir.cerner.com/e8a84236-c258-4952-98b7-a6ff8a9c587a/codeSet/52",
                  "code": "214",
                  "userSelected": true
                },
                {
                  "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                  "code": "N",
                  "display": "Normal",
                  "userSelected": false
                }
              ],
              "text": "Normal"
            }
          ],
          "referenceRange": [
            {
              "low": {
                "value": 42.0,
                "unit": "%",
                "system": "http://unitsofmeasure.org",
                "code": "%"
              },
              "high": {
                "value": 75.0,
                "unit": "%",
                "system": "http://unitsofmeasure.org",
                "code": "%"
              },
              "type": {
                "coding": [
                  {
                    "system": "http://terminology.hl7.org/CodeSystem/referencerange-meaning",
                    "code": "normal",
                    "display": "Normal Range"
                  }
                ],
                "text": "Normal Range"
              }
            }
          ]
        }
    """.trimIndent()
}
