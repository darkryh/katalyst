plugins {
    kotlin("jvm") version "2.2.20" apply false
    kotlin("plugin.serialization") version "2.2.20" apply false
}

allprojects {
    group = "com.ead.boshi"
    version = "1.0.0"
}

subprojects {
    apply(plugin = "kotlin")
}
