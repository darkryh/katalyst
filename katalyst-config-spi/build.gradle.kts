plugins {
    id("io.github.darkryh.katalyst.conventions.base")
}

dependencies {
    api(libs.ktor.server.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.platform.launcher)
}
