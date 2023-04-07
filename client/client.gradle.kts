plugins {
    id("com.projectronin.interop.gradle.junit")
    kotlin("plugin.serialization") version "1.8.10"
}

dependencies {
    // We don't actually use Spring Boot, but this parent can ensure our client and server are on the same versions.
    implementation(platform(libs.spring.boot.parent))

    implementation(libs.bundles.jackson)
    implementation(libs.bundles.ktor)
    implementation(libs.interop.common)
    implementation(libs.interop.commonHttp)
    implementation(libs.interop.commonJackson)
    implementation(libs.interop.fhir)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")

    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockwebserver)
}
