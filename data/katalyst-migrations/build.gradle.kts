plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    implementation(projects.katalystCore)
    implementation(projects.katalystDi)
    implementation(projects.katalystPersistence)

    implementation(kotlin("reflect"))

    implementation(libs.exposed.migration.jdbc)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.h2)
    // Captures log output so tests can assert the migrations feature never fails silently.
    testImplementation(libs.logback)
}
