plugins {
    kotlin("jvm")
}

val ktor_version: String by project
val exposed_version: String by project
val h2_version: String by project

group = "com.ead.katalyst"
version = "0.0.1"

dependencies {
    // Core modules that this DI orchestrates
    implementation(projects.katalystCore)
    implementation(projects.katalystPersistence)
    implementation(projects.katalystScheduler)
    implementation(projects.katalystScanner)
    implementation(projects.katalystKtor)

    // Exposed (needed for table discovery and creation)
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")

    // Koin DI Framework
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)

    // Ktor (for application integration)
    implementation(libs.ktor.server.core)
    implementation("io.ktor:ktor-server-host-common:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")

    // Kotlin and Coroutines
    implementation(kotlin("stdlib"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(kotlin("reflect"))

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.koin.test)
    testImplementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    testImplementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    testImplementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    testImplementation("com.h2database:h2:$h2_version")

    implementation(libs.reflections)
}

kotlin {
    jvmToolchain(21)
}
