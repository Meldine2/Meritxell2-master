// settings.gradle.kts

pluginManagement {
    repositories {
        google() // Add google repository here
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()  // Add google repository here
        mavenCentral()
    }
}

rootProject.name = "meritxell"
include(":app")
