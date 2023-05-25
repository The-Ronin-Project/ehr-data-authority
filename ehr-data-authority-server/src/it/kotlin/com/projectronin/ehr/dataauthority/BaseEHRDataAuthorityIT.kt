package com.projectronin.ehr.dataauthority

import com.projectronin.ehr.dataauthority.client.EHRDataAuthorityClient
import com.projectronin.ehr.dataauthority.client.auth.EHRDataAuthorityAuthenticationService
import com.projectronin.ehr.dataauthority.testclients.DBClient
import com.projectronin.ehr.dataauthority.testclients.KafkaClient
import com.projectronin.ehr.dataauthority.testclients.ValidationClient
import com.projectronin.fhir.r4.Resource
import com.projectronin.interop.common.http.spring.HttpSpringConfig
import com.projectronin.interop.fhir.generators.datatypes.contactPoint
import com.projectronin.interop.fhir.generators.datatypes.extension
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.meta
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.PatientGenerator
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.CodeableConcepts
import com.projectronin.interop.fhir.r4.datatype.primitive.Canonical
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.valueset.ContactPointSystem
import com.projectronin.interop.fhir.r4.valueset.ContactPointUse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import kotlin.reflect.KClass

abstract class BaseEHRDataAuthorityIT {
    companion object {

        val docker =
            DockerComposeContainer(File(BaseEHRDataAuthorityIT::class.java.getResource("/docker-compose-it.yaml")!!.file)).withEnv(
                mapOf<String, String>(
                    "AIDBOX_LICENSE_ID" to System.getenv("AIDBOX_LICENSE_ID"),
                    "AIDBOX_LICENSE_KEY" to System.getenv("AIDBOX_LICENSE_KEY"),
                    "AIDBOX_PORT" to "8888",
                    "AIDBOX_CLIENT_ID" to "client",
                    "AIDBOX_ADMIN_ID" to "admin",
                    "AIDBOX_ADMIN_PASSWORD" to "secret",
                    "AIDBOX_CLIENT_SECRET" to "secret",
                    "AIDBOX_DEV_MODE" to "true",
                    "PGPORT" to "5432",
                    "PGHOSTPORT" to "5437",
                    "AIDBOX_FHIR_VERSION" to "4.0.0",
                    "PGHOST" to "database",
                    "PGUSER" to "postgres",
                    "POSTGRES_USER" to "postgres",
                    "POSTGRES_PASSWORD" to "postgres",
                    "POSTGRES_DB" to "devbox",
                    "PGPASSWORD" to "postgres",
                    "PGDATABASE" to "devbox",
                    "box_features_validation_skip_reference" to "true"
                )
            )
                .waitingFor("ehr-data-authority", Wait.forLogMessage(".*Started EHRDataAuthorityServerKt.*", 1))
                .start()
    }

    protected val serverUrl = "http://localhost:8080"
    protected val httpClient = HttpSpringConfig().getHttpClient()

    protected val authenticationService =
        EHRDataAuthorityAuthenticationService(
            httpClient,
            "http://localhost:8081/ehr/token",
            "https://ehr.dev.projectronin.io",
            "id",
            "secret",
            false
        )
    protected val client = EHRDataAuthorityClient(serverUrl, httpClient, authenticationService)

    open val resources = mapOf<String, KClass<out Resource>>()

    @BeforeEach
    fun setup() {
        resources.forEach { (topic, resource) ->
            KafkaClient.monitorResource(topic, resource)
        }
    }

    @AfterEach
    fun cleanup() {
        DBClient.purgeHashes()
        KafkaClient.reset()
        ValidationClient.clearAllResources()
    }

    // This should only be used until INT-1652 has been completed.
    protected fun roninPatient(fhirId: String, tenantId: String, block: PatientGenerator.() -> Unit = {}) =
        patient {
            block.invoke(this)

            id of Id(fhirId)
            meta of meta {
                profile of listOf(Canonical("http://projectronin.io/fhir/StructureDefinition/ronin-patient"))
                source of "ehrda-test-data"
            }
            identifier plus identifier {
                system of CodeSystem.RONIN_TENANT.uri
                value of tenantId
                type of CodeableConcepts.RONIN_TENANT
            } plus identifier {
                system of CodeSystem.RONIN_FHIR_ID.uri
                value of fhirId
                type of CodeableConcepts.RONIN_FHIR_ID
            } plus identifier {
                system of CodeSystem.RONIN_MRN.uri
                type of CodeableConcepts.RONIN_MRN
            } plus identifier {
                value of "EHR Data Authority"
                system of CodeSystem.RONIN_DATA_AUTHORITY.uri
                type of CodeableConcepts.RONIN_DATA_AUTHORITY_ID
            }
            name plus name {
                use of Code("official")
            }
            telecom of listOf(
                contactPoint {
                    system of Code(
                        ContactPointSystem.EMAIL.code,
                        extension = listOf(
                            extension {
                                url of Uri("http://projectronin.io/fhir/StructureDefinition/Extension/tenant-sourceTelecomSystem")
                            }
                        )
                    )
                    value of "josh@projectronin.com"
                    use of Code(
                        ContactPointUse.HOME.code,
                        extension = listOf(
                            extension {
                                url of Uri("http://projectronin.io/fhir/StructureDefinition/Extension/tenant-sourceTelecomUse")
                            }
                        )
                    )
                }
            )
        }
}
