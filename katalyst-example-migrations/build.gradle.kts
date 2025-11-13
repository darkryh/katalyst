plugins {
    kotlin("jvm")
    application
}

group = "com.ead.katalyst.example"
version = "0.0.1"

dependencies {
    implementation(projects.katalystMigrations)
    implementation(projects.katalystPersistence)
    implementation(projects.katalystDi)
    implementation(libs.koin.core)

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.logback)
    implementation(libs.h2)

    testImplementation(kotlin("test"))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.postgresql)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "com.ead.katalyst.example.migrations.cli.TodoMigrationCliKt"
}
