plugins {
    id("io.github.darkryh.katalyst.conventions.persistence")
}

dependencies {
    implementation(kotlin("reflect"))

    // Internal modules
    implementation(projects.katalystCore)
    implementation(projects.katalystEvents)
    implementation(projects.katalystTransactions)
    implementation(libs.exposed.java.time)

    testImplementation(projects.katalystTestingCore)
    testImplementation(libs.kotlinx.coroutines.test)
}
