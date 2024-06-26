plugins {
    alias(libs.plugins.interop.junit)
    alias(libs.plugins.interop.spring.framework)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // We don't actually use Spring Boot, but this parent can ensure our client and server are on the same versions.
    implementation(platform(libs.spring.boot.parent))

    api(project(":ehr-data-authority-models"))
    implementation(libs.dd.trace.api)
    implementation(libs.bundles.jackson)
    implementation(libs.bundles.ktor)
    implementation(libs.interop.common)
    implementation(libs.interop.commonHttp)
    implementation(libs.interop.commonJackson)
    implementation(libs.interop.fhir)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockwebserver)
    testImplementation("org.springframework:spring-test")
}
