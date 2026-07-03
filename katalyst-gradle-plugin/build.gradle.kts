plugins {
    `java-gradle-plugin`
    id("io.github.darkryh.katalyst.conventions.base")
}

// Consumer-facing Gradle plugin. Applying `id("io.github.darkryh.katalyst")` in an application
// build applies the Kotlin JVM plugin, the kotlinx.serialization plugin and the `application`
// plugin, so a Katalyst app never has to wire Ktor/Exposed/serialization plugins itself — those
// libraries arrive transitively through the katalyst-* starters.
//
// Reuses the base convention plugin for identical group/version, signing, POM and Maven Central
// publishing as every other module. `java-gradle-plugin` adds the plugin marker publication, which
// vanniktech publishes alongside the main artifact.

dependencies {
    // Bundled so KatalystPlugin can apply these plugins by id in the consumer build. They live on
    // the plugin's runtime classpath; the consumer never declares them.
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
}

gradlePlugin {
    plugins {
        create("katalyst") {
            id = "io.github.darkryh.katalyst"
            implementationClass = "io.github.darkryh.katalyst.gradle.KatalystPlugin"
            displayName = "Katalyst"
            description = "Applies the Kotlin, kotlinx.serialization and application plugins a " +
                "Katalyst application needs, so consumer builds never wire Ktor/Exposed/" +
                "serialization plugins themselves."
        }
    }
}
