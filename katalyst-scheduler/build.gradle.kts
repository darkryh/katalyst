plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(projects.katalystCore)

    implementation(libs.koin.core)
    implementation(libs.koin.core.jvm)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback)
}

kotlin {
    jvmToolchain(21)
}