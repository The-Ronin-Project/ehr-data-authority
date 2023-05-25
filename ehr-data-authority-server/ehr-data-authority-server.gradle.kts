plugins {
    id("com.projectronin.interop.gradle.spring-boot")
    id("com.projectronin.interop.gradle.docker-integration")
}

dependencies {
    implementation(platform(libs.spring.boot.parent))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    implementation(project(":ehr-data-authority-models"))
    implementation(libs.common.fhir.r4.models)
    implementation(libs.interop.common)
    implementation(libs.interop.commonHttp)
    implementation(libs.interop.commonJackson)
    implementation(libs.interop.commonKtorm)
    implementation(libs.interop.fhir)
    implementation(libs.interop.ehr.api)
    implementation(libs.interop.ehr.fhir.ronin)
    implementation(libs.interop.publishers.aidbox)
    implementation(libs.interop.publishers.kafka)

    implementation(libs.guava)
    implementation(libs.bundles.ktor)
    implementation(libs.ktorm.core)
    implementation(libs.ktorm.support.mysql)

    runtimeOnly(libs.liquibase.core)
    runtimeOnly(libs.mysql.connector.java)
    runtimeOnly(libs.interop.publishers.datalake)
    runtimeOnly(libs.interop.validation.client)

    // Needed to format logs for DataDog
    runtimeOnly(libs.logstash.logback.encoder)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.mockk)
    testImplementation(libs.interop.commonTestDb)
    testImplementation(libs.rider.core)
    testImplementation(libs.bundles.data.generators)

    testRuntimeOnly("org.testcontainers:mysql")

    itImplementation(project)
    itImplementation(project(":ehr-data-authority-models"))
    itImplementation(project(":ehr-data-authority-client"))
    itImplementation(platform(libs.testcontainers.bom))
    itImplementation("org.testcontainers:testcontainers")
    itImplementation(libs.bundles.ktor)
    itImplementation(libs.interop.fhir)
    itImplementation(libs.common.fhir.r4.models)
    itImplementation(libs.interop.validation.client)
    itImplementation(libs.interop.publishers.aidbox)
    itImplementation(libs.interop.publishers.kafka)
    itImplementation(libs.kafka.clients)
    itImplementation(libs.ronin.kafka)
    itImplementation(libs.bundles.data.generators)
    itImplementation(libs.interop.commonJackson)
    itImplementation(libs.interop.commonHttp)
    itImplementation(libs.ktorm.core)
    itImplementation(libs.kotlinx.coroutines.core)
    itImplementation(libs.kotlin.logging)
}
