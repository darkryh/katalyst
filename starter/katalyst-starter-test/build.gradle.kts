plugins {
    id("io.github.darkryh.katalyst.conventions.base")
}

dependencies {
    api(projects.katalystTestingCore)
    api(projects.katalystTestingKtor)
    api(libs.kotlin.test.junit5)
    api(libs.ktor.client.websockets)
    api(libs.testcontainers.postgresql)
    api(libs.postgresql)

    runtimeOnly(libs.junit.jupiter.engine)
    runtimeOnly(libs.junit.platform.launcher)
}
