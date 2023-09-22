plugins {
    alias(libs.plugins.interop.junit)
    alias(libs.plugins.interop.spring.framework)
}

dependencies {
    implementation(libs.interop.fhir)
    implementation(libs.common.fhir.r4.models)
    implementation(libs.interop.kafka)
    implementation(libs.guava)
    testImplementation(libs.mockk)
    testImplementation(libs.interop.commonJackson)
}
