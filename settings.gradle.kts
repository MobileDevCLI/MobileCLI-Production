/**
 * MobileCLI Production - Gradle Settings
 *
 * This file configures the Gradle build for MobileCLI.
 *
 * IMPORTANT: We use JitPack to pull Termux terminal libraries.
 * These are Apache 2.0 licensed and provide the core terminal emulation.
 */

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
        // JitPack provides Termux terminal libraries
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MobileCLI"
include(":app")
