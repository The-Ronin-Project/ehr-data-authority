import pl.allegro.tech.build.axion.release.domain.properties.TagProperties

plugins {
    id("pl.allegro.tech.build.axion-release")
    kotlin("jvm")
}

rootProject.apply {
    scmVersion {
        tag {
            initialVersion(TagProperties.InitialVersionSupplier { _, _ -> "1.0.0" })
            prefix.set("v")
        }
        versionCreator { versionFromTag, position ->
            val supportedHeads = setOf("HEAD", "master", "main")
            if (!supportedHeads.contains(position.branch)) {
                val jiraBranchRegex = Regex("(\\w+)-(\\d+)-(.+)")
                val match = jiraBranchRegex.matchEntire(position.branch)
                val branchExtension = match?.let {
                    val (project, number, _) = it.destructured
                    "$project$number"
                } ?: position.branch

                "$versionFromTag-$branchExtension"
            } else {
                versionFromTag
            }
        }
    }
}

allprojects {
    project.version = project.rootProject.scmVersion.version
}
