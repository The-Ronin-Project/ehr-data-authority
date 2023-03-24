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

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    itImplementation(project)
    itImplementation(platform(libs.spring.boot.parent))
    itImplementation("org.springframework.boot:spring-boot-starter")
    itImplementation("org.springframework.boot:spring-boot-starter-web")
    itImplementation("org.springframework.boot:spring-boot-starter-actuator")
    itImplementation("org.springframework.boot:spring-boot-starter-test")
    itImplementation(platform(libs.testcontainers.bom))
    itImplementation("org.testcontainers:testcontainers")
    itImplementation(libs.interop.common.jackson)

    itImplementation(libs.interop.common.http)
}
