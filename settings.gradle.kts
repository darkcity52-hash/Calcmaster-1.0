pluginManagement {
    repositories {
        maven { url = uri("https://maven.google.com") }
        maven { url = uri("https://repo.maven.apache.org/maven2/") }
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

rootProject.name = "CalcMaster"
include(":app")
