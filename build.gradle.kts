plugins {
    id("com.projectronin.interop.gradle.server-publish") apply false
    id("com.projectronin.interop.gradle.server-version")
}

subprojects {
    apply(plugin = "com.projectronin.interop.gradle.base")

    if (project.name != "ehr-data-authority-server") {
        apply(plugin = "com.projectronin.interop.gradle.server-publish")
    }

    // Disable releases hub from running on the subprojects. Main project will handle it all.
    tasks.filter { it.group.equals("releases hub", ignoreCase = true) }.forEach { it.enabled = false }
}
