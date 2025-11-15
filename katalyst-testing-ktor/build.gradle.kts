plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

dependencies {
    api(project(":katalyst-testing-core"))
    implementation(project(":katalyst-ktor"))
    api(libs.ktor.server.test.host)

    testImplementation(kotlin("test"))
}
