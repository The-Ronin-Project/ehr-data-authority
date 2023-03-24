plugins {
    id("com.projectronin.interop.gradle.integration") apply false
    id("com.projectronin.interop.gradle.spring-boot") apply false

}

subprojects {
    apply(plugin = "com.projectronin.interop.gradle.base")
    apply(plugin = "com.projectronin.interop.gradle.integration")
    apply(plugin = "com.projectronin.interop.gradle.spring-boot")

    // Disable releases hub from running on the subprojects. Main project will handle it all.
    tasks.filter { it.group.equals("releases hub", ignoreCase = true) }.forEach { it.enabled = false }
}


