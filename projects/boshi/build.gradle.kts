plugins {
    // Declare plugins to make them available to subprojects
    // Client multiplatform compose plugins
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
}

// Shared configuration for all projects
allprojects {
    group = "com.ead.boshi"
    version = "1.0.0"

    // Declare plugin versions for server modules that use kotlin("jvm") notation
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        // JVM plugin is applied - nothing extra needed
    }
}
