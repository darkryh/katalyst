plugins {
    id("io.github.darkryh.katalyst.conventions.testing")
}

dependencies {
    api(project(":katalyst-di"))
    api(project(":katalyst-core"))
    api(project(":katalyst-config-provider"))
    api(project(":katalyst-ktor"))
    api(project(":katalyst-persistence"))
    api(kotlin("test"))

    implementation(project(":katalyst-config-yaml"))
    implementation(project(":katalyst-events-bus"))
    implementation(project(":katalyst-migrations"))
    implementation(project(":katalyst-scheduler"))
    implementation(project(":katalyst-websockets"))
    implementation(project(":katalyst-events"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    runtimeOnly(libs.h2)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(project(":katalyst-koin-bean"))

    testFixturesImplementation(kotlin("test"))
    testFixturesImplementation(libs.ktor.server.core)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
