rootProject.name = "ehr-data-authority"

include("ehr-data-authority-server")
include("ehr-data-authority-client")
include("ehr-data-authority-models")

for (project in rootProject.children) {
    project.buildFileName = "${project.name}.gradle.kts"
}

pluginManagement {
    plugins {
        id("com.projectronin.interop.gradle.base") version "3.1.0"
        id("com.projectronin.interop.gradle.spring-boot") version "3.1.0"
        id("com.projectronin.interop.gradle.docker-integration") version "3.1.0"
        id("com.projectronin.interop.gradle.junit") version "3.1.0"
        id("com.projectronin.interop.gradle.server-publish") version "3.1.0"
        id("com.projectronin.interop.gradle.server-version") version "3.1.0"
        id("com.projectronin.interop.gradle.spring") version "3.1.0"

        id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.7"
        id("org.openapi.generator") version "6.6.0"
        id("org.springframework.boot") version "2.7.5"
        kotlin("plugin.serialization") version "1.8.10"
    }

    repositories {
        maven {
            url = uri("https://repo.devops.projectronin.io/repository/maven-snapshots/")
            mavenContent {
                snapshotsOnly()
            }
        }
        maven {
            url = uri("https://repo.devops.projectronin.io/repository/maven-releases/")
            mavenContent {
                releasesOnly()
            }
        }
        maven {
            url = uri("https://repo.devops.projectronin.io/repository/maven-public/")
            mavenContent {
                releasesOnly()
            }
        }
        mavenLocal()
        gradlePluginPortal()
    }
}
