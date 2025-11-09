plugins {
    kotlin("jvm")
}

dependencies {
    // Engine abstraction
    implementation(projects.katalystKtorEngine)

    // Katalyst modules
    implementation(projects.katalystCore)

    // Ktor with Netty engine
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)

    // Dependency injection
    implementation(libs.koin.core)

    // Kotlin
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
