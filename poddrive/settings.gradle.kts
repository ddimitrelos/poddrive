pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "poddrive"
include(":shared")
include(":androidApp")

// Redirect build output to a local path outside OneDrive to prevent file-locking
// conflicts when OneDrive syncs the project while a Gradle build is in progress.
gradle.allprojects {
    layout.buildDirectory.set(File("C:/gradle-build-cache/poddrive/${project.name}"))
}
