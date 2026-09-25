pluginManagement {
    repositories {
        google() // Google repository for Android plugins
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.library") version "9.2.0"
        id("com.android.application") version "9.2.0"
        id("org.jetbrains.kotlin.android") version "2.2.10"
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google() // Add this for Android dependencies
        mavenCentral()
        maven(url = "https://jitpack.io") // If using any dependencies hosted on Jitpack
    }
}

rootProject.name = "KotlinSampleApp"
include(":app")
