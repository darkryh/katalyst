plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

dependencies {
    implementation(projects.katalystDi)
    implementation(projects.katalystPersistence)

    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation(libs.koin.core)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.migration.core)
    implementation(libs.exposed.migration.jdbc)
    implementation(libs.logback)

    testImplementation(kotlin("test"))
    testImplementation(libs.h2)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(21)
}
