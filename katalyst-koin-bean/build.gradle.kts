plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    implementation(projects.katalystCore)
    implementation(projects.katalystDi)
    api(libs.koin.core)

    testImplementation(libs.koin.test)
}
