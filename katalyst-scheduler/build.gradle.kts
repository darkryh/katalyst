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

testImplementation(kotlin("test"))
testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
