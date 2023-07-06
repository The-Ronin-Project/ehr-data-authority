plugins {
    alias(libs.plugins.interop.junit)
}

dependencies {
    implementation(libs.interop.fhir)

    testImplementation(libs.mockk)
    testImplementation(libs.interop.commonJackson)
}
