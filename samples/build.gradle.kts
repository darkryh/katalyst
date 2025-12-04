plugins {
    kotlin("jvm") version "2.2.21" apply false
    id("io.ktor.plugin") version "3.3.3" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
}
