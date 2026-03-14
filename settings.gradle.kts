pluginManagement {
    repositories {
        maven(url = "https://maven.google.com")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("com.android.")) {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://maven.google.com")
        google()
        mavenCentral()
    }
}

rootProject.name = "WearOS_ClassingTimeTable"
include(":app")
include(":mobile")
include(":shared")
