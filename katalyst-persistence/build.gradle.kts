plugins {
    id("io.github.darkryh.katalyst.conventions.persistence")
}

dependencies {
    implementation(kotlin("reflect"))

    // Internal modules
    implementation(projects.katalystCore)
    implementation(projects.katalystEvents)
    implementation(projects.katalystTransactions)

    testImplementation(projects.katalystTestingCore)
    testImplementation(libs.kotlinx.coroutines.test)
}
