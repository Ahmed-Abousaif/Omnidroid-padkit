plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "gg.padkit.lib"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { enable.add("NewApi") }
}

dependencies {
    api(platform(libs.compose.bom))
    api("androidx.compose.runtime:runtime")
    api("androidx.compose.ui:ui")
    api(libs.collections.immutable)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${libs.versions.kotlin.get()}")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation(libs.robolectric)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

if (providers.gradleProperty("composeReports").orNull == "true") {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}

tasks.register("verifyAndroid7Compatibility") {
    group = "verification"
    description = "Build, lint for unsupported APIs, and run lifecycle/haptic tests including API 24."
    dependsOn("assembleRelease", "lintRelease", "testDebugUnitTest")
}
