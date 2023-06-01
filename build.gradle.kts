plugins {
    id("com.projectronin.interop.gradle.base")
    id("ehrda-publish") apply false
    id("ehrda-version")
}

subprojects {
    apply(plugin = "com.projectronin.interop.gradle.base")

    if (project.name != "ehr-data-authority-server") {
        apply(plugin = "ehrda-publish")
    }

    // Disable releases hub from running on the subprojects. Main project will handle it all.
    tasks.filter { it.group.equals("releases hub", ignoreCase = true) }.forEach { it.enabled = false }
}
