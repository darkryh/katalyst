plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.koin.ktor)
    implementation(libs.logback)
    implementation(libs.reflections)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit5)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.platform.launcher)

    // Domain modules for testing
    implementation(projects.katalystCore)
    implementation(projects.katalystPersistence)
    implementation(projects.katalystScheduler)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
