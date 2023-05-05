plugins {
    id("com.projectronin.interop.gradle.version")
}

subprojects {
    // Disable releases hub from running on the subprojects. Main project will handle it all.
    tasks.filter { it.group.equals("releases hub", ignoreCase = true) }.forEach { it.enabled = false }
}
