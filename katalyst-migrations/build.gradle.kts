plugins {
    id("io.github.darkryh.katalyst.conventions.persistence")
}

dependencies {
    implementation(projects.katalystDi)
    implementation(projects.katalystPersistence)

    implementation(kotlin("reflect"))

    implementation(libs.exposed.migration.core)
    implementation(libs.exposed.migration.jdbc)

    testImplementation(libs.kotlinx.coroutines.test)
}
