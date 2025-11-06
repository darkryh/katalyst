plugins {
    kotlin("jvm")
}

val exposed_version: String by rootProject
val koin_version: String by rootProject

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(projects.katalystCore)

    implementation(libs.logback)
    implementation(libs.kotlinx.coroutines.core)

    implementation("io.insert-koin:koin-core:$koin_version")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
}

kotlin {
    jvmToolchain(21)
}
