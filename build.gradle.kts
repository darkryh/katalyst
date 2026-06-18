plugins {
    kotlin("jvm") version "2.4.0" apply false
    id("io.ktor.plugin") version "3.5.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
}
