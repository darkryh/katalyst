plugins {
    kotlin("jvm") version "2.2.20"
}

group = "com.ead.boshi"
version = "1.0-SNAPSHOT"
dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}