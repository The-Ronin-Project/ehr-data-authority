plugins {
    id("com.projectronin.interop.gradle.junit")
}

dependencies {
    implementation(libs.interop.fhir)

    testImplementation(libs.mockk)
    testImplementation(libs.interop.commonJackson)
}
