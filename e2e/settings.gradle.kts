// Google's mirror of Maven Central first (repo1 rate-limits heavily from CI/cloud hosts).
val central = "https://maven-central.storage-download.googleapis.com/maven2"
pluginManagement {
    repositories { maven("https://maven-central.storage-download.googleapis.com/maven2"); gradlePluginPortal(); mavenCentral() }
}
dependencyResolutionManagement { repositories { maven(central); mavenCentral(); google() } }
rootProject.name = "phonetemp-e2e"
