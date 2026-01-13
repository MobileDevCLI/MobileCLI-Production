/**
 * MobileCLI Production - Root Build Configuration
 *
 * Top-level build file where you can add configuration options
 * common to all sub-projects/modules.
 *
 * Plugin versions are specified here but applied in module build files.
 */

plugins {
    // Android Application Plugin
    id("com.android.application") version "8.2.0" apply false

    // Kotlin Android Plugin
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}
