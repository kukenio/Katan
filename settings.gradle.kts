enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

rootProject.name = "katan-server"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include(
    "model",
    "application",
    "events-dispatcher",
    "crypto",
    "http:http-shared",
    "http:http-server",
    "http:http-test",
    "http:http-client",
    "services:auth-service",
    "services:id-service",
    "services:unit-service",
    "services:instance-service",
    "services:account-service",
    "services:database-service",
    "services:network-service",
    "services:cache-service",
    "services:fs-service",
    "services:host-fs-service",
    "services:blueprint-service",
)
