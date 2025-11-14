plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":katalyst-di"))
    api(project(":katalyst-core"))
    api(project(":katalyst-config-provider"))
    api(project(":katalyst-ktor"))
    api(project(":katalyst-persistence"))
    api(libs.koin.core)

    implementation(project(":katalyst-config-yaml"))
    implementation(project(":katalyst-events-client"))
    implementation(project(":katalyst-migrations"))
    implementation(project(":katalyst-scheduler"))
    implementation(project(":katalyst-websockets"))
    implementation(project(":katalyst-events"))

    testImplementation(kotlin("test"))
}
