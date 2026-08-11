plugins {
    kotlin("jvm") version "2.4.10" apply false
    id("io.ktor.plugin") version "3.5.2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
}
