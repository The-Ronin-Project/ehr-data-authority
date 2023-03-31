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
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    implementation(libs.ktorm.core)
    implementation(libs.ktorm.support.mysql)
    runtimeOnly(libs.liquibase.core)
    runtimeOnly(libs.mysql.connector.java)

    testImplementation(platform(libs.spring.boot.parent))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation("org.testcontainers:mysql")
    testImplementation(libs.liquibase.core)
    testImplementation(libs.mysql.connector.java)

    itImplementation(platform(libs.testcontainers.bom))
    itImplementation("org.testcontainers:testcontainers")
    itImplementation(libs.interop.common.jackson)
    itImplementation(libs.interop.common.http)
}
