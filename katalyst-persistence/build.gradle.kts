plugins {
    id("com.ead.katalyst.conventions.persistence")
}

dependencies {
    implementation(kotlin("reflect"))

    // Internal modules
    implementation(projects.katalystCore)
    implementation(projects.katalystEvents)
    implementation(projects.katalystTransactions)  // NEW: For DatabaseTransactionManager

    testImplementation(projects.katalystTestingCore)
    testImplementation(libs.kotlinx.coroutines.test)
}
