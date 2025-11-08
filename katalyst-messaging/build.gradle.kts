plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback)

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}
