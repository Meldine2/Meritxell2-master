buildscript {
    dependencies {
        classpath(libs.google.services)
    }
}
// Project-level build.gradle.kts

plugins {
    // Apply the Android application plugin at the root level
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    // Define Google services plugin but do not apply it here
    id("com.google.gms.google-services") version "4.3.15" apply true
}
