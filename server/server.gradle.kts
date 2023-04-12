plugins {
    id("com.projectronin.interop.gradle.spring-boot")
    id("com.projectronin.interop.gradle.integration")
    id("com.projectronin.interop.gradle.docker-integration")
}

dependencies {
    implementation(platform(libs.spring.boot.parent))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    implementation(libs.interop.aidbox)
    implementation(libs.interop.common)
    implementation(libs.interop.commonKtorm)
    implementation(libs.interop.fhir)

    implementation(libs.bundles.ktor)
    implementation(libs.ktorm.core)
    implementation(libs.ktorm.support.mysql)

    runtimeOnly(libs.liquibase.core)
    runtimeOnly(libs.mysql.connector.java)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.mockk)
    testImplementation(libs.interop.commonTestDb)
    testImplementation(libs.rider.core)

    testRuntimeOnly("org.testcontainers:mysql")

    itImplementation(project)
    itImplementation(platform(libs.testcontainers.bom))
    itImplementation("org.testcontainers:testcontainers")
    itImplementation(libs.bundles.ktor)
    itImplementation(libs.interop.fhir)
    itImplementation(libs.interop.aidbox)
    itImplementation(libs.bundles.data.generators)
    itImplementation(libs.interop.commonJackson)
    itImplementation(libs.interop.commonHttp)
    itImplementation(libs.ktorm.core)
}
