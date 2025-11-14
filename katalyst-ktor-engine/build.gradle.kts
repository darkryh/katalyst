plugins {
    kotlin("jvm")
}

dependencies {
    // Katalyst modules
    implementation(projects.katalystCore)

    // Ktor core for server abstractions
    implementation(libs.ktor.server.core)

    // Kotlin
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
