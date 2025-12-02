plugins {
    id("com.ead.katalyst.conventions.scheduler")
}

dependencies {
    // Core modules
    implementation(projects.katalystCore)
    implementation(projects.katalystDi)

    // Bytecode analysis for scheduler method discovery
    implementation(libs.asm)

    // Testing
    testImplementation(libs.kotlinx.coroutines.test)
}
