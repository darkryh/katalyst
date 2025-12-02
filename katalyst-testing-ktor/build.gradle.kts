plugins {
    id("com.ead.katalyst.conventions.testing")
}

dependencies {
    api(project(":katalyst-testing-core"))
    implementation(project(":katalyst-ktor"))
    api(libs.ktor.server.test.host)
}
