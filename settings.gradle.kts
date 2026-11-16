pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Omnidroid-padkit"
include(":lib")

// Keep device smoke testing available without configuring an application for every library build.
if (providers.gradleProperty("includeSample").orNull == "true") {
    include(":sample")
}
