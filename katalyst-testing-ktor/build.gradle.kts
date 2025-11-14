plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":katalyst-testing-core"))
    implementation(project(":katalyst-ktor"))
    api(libs.ktor.server.test.host)

    testImplementation(kotlin("test"))
}
