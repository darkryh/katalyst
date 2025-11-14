plugins {
    kotlin("jvm") version "2.2.20" apply false
    id("io.ktor.plugin") version "3.3.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" apply false
    id("org.jetbrains.kotlinx.kover") version "0.8.1"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
}
