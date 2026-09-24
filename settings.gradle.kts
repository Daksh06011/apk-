// Google's mirror of Maven Central first (repo1 rate-limits heavily from CI/cloud hosts).
pluginManagement {
    repositories { maven("https://maven-central.storage-download.googleapis.com/maven2"); gradlePluginPortal(); mavenCentral() }
    plugins {
        kotlin("jvm") version "2.0.0"             // the app was built with Kotlin 2.0.0
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    }
}
dependencyResolutionManagement {
    repositories { maven("https://maven-central.storage-download.googleapis.com/maven2"); mavenCentral(); google() }
}
rootProject.name = "phonetemp"
include("feature", "e2e")
