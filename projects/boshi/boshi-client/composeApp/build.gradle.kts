import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.reload.gradle.ComposeHotRun

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.material3.navigation3)


            implementation(libs.compose.ui.util)
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("org.jetbrains.androidx.navigationevent:navigationevent-compose:1.0.0-beta02")



            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.navigation3)

            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            // HTML sanitization
            implementation("org.jsoup:jsoup:1.17.2")
            implementation(libs.navigation3.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}


compose.desktop {
    application {
        mainClass = "com.ead.boshi_client.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.ead.boshi_client"
            packageVersion = "1.0.0"
        }
    }
}
