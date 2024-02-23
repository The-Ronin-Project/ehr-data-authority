plugins {
    alias(libs.plugins.interop.spring.boot)
    alias(libs.plugins.interop.docker.integration)
}

dependencies {
    implementation(platform(libs.spring.boot.parent))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation(project(":ehr-data-authority-models"))
    implementation(libs.common.fhir.r4.models)
    implementation(libs.interop.common)
    implementation(libs.interop.commonHttp)
    implementation(libs.interop.commonJackson)
    implementation(libs.interop.commonKtorm)
    implementation(libs.interop.fhir)
    implementation(libs.interop.rcdm.validate)
    implementation(libs.interop.kafka)
    implementation(libs.interop.datalake)
    implementation(libs.springdoc.openapi.ui)

    implementation(libs.guava)
    implementation(libs.bundles.ktor)
    implementation(libs.ktorm.core)
    implementation(libs.ktorm.support.mysql)

    runtimeOnly(libs.liquibase.core)
    runtimeOnly(libs.mysql.connector.java)
    implementation(libs.interop.validation.client)

    // Needed to format logs for DataDog
    runtimeOnly(libs.logstash.logback.encoder)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.mockk)
    testImplementation(libs.interop.commonTestDb)
    testImplementation(libs.rider.core)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.bundles.data.generators)

    testRuntimeOnly("org.testcontainers:mysql")

    itImplementation(project)
    itImplementation(project(":ehr-data-authority-models"))
    itImplementation(project(":ehr-data-authority-client"))
    itImplementation(libs.bundles.ktor)
    itImplementation(libs.interop.fhir)
    itImplementation(libs.common.fhir.r4.models)
    itImplementation(libs.interop.ehr.fhir.roninGenerators)
    itImplementation(libs.interop.validation.client)
    itImplementation(libs.interop.kafka)
    itImplementation(libs.interop.kafka.testing.client)
    itImplementation(libs.kafka.clients)
    itImplementation(libs.ronin.kafka)
    itImplementation(libs.bundles.data.generators)
    itImplementation(libs.interop.commonJackson)
    itImplementation(libs.interop.commonHttp)
    itImplementation(libs.interop.commonKtorm)
    itImplementation(libs.ktorm.core)
    itImplementation(libs.kotlinx.coroutines.core)
    itImplementation(libs.kotlin.logging)
}

tasks.withType<Test> {
    maxHeapSize = "2g"
}

// We also test a local version, so use a similar setup as our IT docker to add Local testing.
val runDockerLocal =
    tasks.create("runDockerLocal") {
        dependsOn(tasks.named("runDocker"))

        doLast {
            exec {
                workingDir = file("./src/it/resources")
                commandLine(
                    "docker compose -f docker-compose-local.yaml -p resources-local up -d --wait --wait-timeout 600".split(
                        " ",
                    ),
                )
            }
        }
    }

tasks.named("it").get().dependsOn(runDockerLocal)

val stopDockerLocal =
    tasks.create("stopDockerLocal") {
        doLast {
            exec {
                workingDir = file("./src/it/resources")
                commandLine("docker compose -f docker-compose-local.yaml -p resources-local down".split(" "))
            }
        }
    }

tasks.named("stopDocker").get().finalizedBy(stopDockerLocal)
