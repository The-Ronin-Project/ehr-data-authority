plugins {
    alias(libs.plugins.interop.docker.integration) apply false
    alias(libs.plugins.interop.junit) apply false
    alias(libs.plugins.interop.spring.boot) apply false
    alias(libs.plugins.interop.spring.framework) apply false
    alias(libs.plugins.interop.server.publish) apply false
    alias(libs.plugins.interop.server.version)
    alias(libs.plugins.interop.version.catalog)
    alias(libs.plugins.interop.sonarqube)
}

subprojects {
    if (project.name != "ehr-data-authority-server") {
        apply(plugin = "com.projectronin.interop.gradle.server-publish")
    }
}
