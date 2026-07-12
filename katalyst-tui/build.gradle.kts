import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    kotlin("jvm")
    // Dispatch runs on the Jetpack Compose runtime, so the Compose compiler plugin is required to
    // compile @Composable functions in this module.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
    // @Serializable NavKey routes need the kotlinx.serialization compiler plugin to generate
    // serializers so the Dispatch back stack can save/restore and content-key each route.
    kotlin("plugin.serialization") version "2.4.0"
    application
    // Published so `katalyst-starter-core` can carry the embedded inspector as a default
    // runtimeOnly dependency — external (Central-only) consumers need the artifact to resolve.
    id("com.vanniktech.maven.publish")
}

// Explicit coordinates (the convention plugin is not applied here) so composite builds can
// substitute `io.github.darkryh.katalyst:katalyst-tui` with this project — that is how a backend
// gets the embedded inspector on its runtime classpath.
group = "io.github.darkryh.katalyst"
version = providers.gradleProperty("katalystVersion").get()

kotlin {
    jvmToolchain(21)
}

val moduleName = project.name

mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = true))
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(groupId = group.toString(), artifactId = moduleName, version = version.toString())

    pom {
        name.set("Katalyst - $moduleName")
        description.set("Katalyst module: $moduleName")
        url.set("https://github.com/darkryh/katalyst")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        scm {
            url.set("https://github.com/darkryh/katalyst")
            connection.set("scm:git:git://github.com/darkryh/katalyst.git")
            developerConnection.set("scm:git:ssh://github.com/darkryh/katalyst.git")
        }

        developers {
            developer {
                id.set("Darkryh")
                name.set("Xavier Alexander Torres Calderón")
                email.set("alex_torres-xc@hotmail.com")
            }
        }
    }
}

application {
    mainClass = "io.github.darkryh.katalyst.tui.MainKt"
}

dependencies {
    // Shared telemetry wire contract (model serializers are generated in -model).
    implementation(projects.katalystTelemetryModel)

    // KatalystFeature contract for EmbeddedTuiFeature: when a backend puts katalyst-tui on its
    // classpath, KatalystApplicationBuilder reflectively loads the feature and the TUI becomes the
    // console (real TTY) or logs a how-to warning (IDE Run window / service). katalyst-di has no
    // compile edge back to this module, so there is no cycle.
    implementation(projects.katalystDi)
    implementation(libs.slf4j.api)

    // Dispatch terminal UI (io.github.darkryh:dispatch-*, consumed from mavenLocal).
    // dispatch-core holds the DispatchApplication entry point; widgets pulls dispatch-runtime.
    implementation(libs.dispatch.core)
    implementation(libs.dispatch.widgets)
    implementation(libs.dispatch.navigation)
    implementation(libs.dispatch.viewmodel)
    implementation(libs.dispatch.layout)
    implementation(libs.dispatch.lifecycle)

    // Attach client: read GET /snapshot and WS /stream from a running backend's loopback transport.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
