import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = "io.github.darkryh.katalyst"
// The released version comes from the `plugin-v*` git tag (set by the release workflow via
// PLUGIN_VERSION); falls back to a dev version for local builds.
version = (System.getenv("PLUGIN_VERSION") ?: "0.2.2").removePrefix("plugin-v").removePrefix("v")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// The plugin recognises Katalyst entrypoints in the editor (PSI) and consumes the
// katalyst-graph.json produced by katalyst-analysis for deeper, whole-app features. It targets
// IntelliJ IDEA Community and (because Android Studio ships the same platform + Kotlin plugin)
// Android Studio.
dependencies {
    intellijPlatform {
        // 2024.2 baseline keeps us compatible with current IDEA and recent Android Studio builds.
        intellijIdeaCommunity("2024.2")
        // Kotlin PSI + the unused-symbol inspection we hook into. Bundled in every IDEA/AS install.
        bundledPlugin("org.jetbrains.kotlin")
        // Java PSI (PsiClass/PsiMethod/PsiType) — used via Kotlin light classes/methods in
        // KatalystPsi. Both IntelliJ IDEA and Android Studio bundle it. Declared so the plugin
        // verifier is satisfied and the dependency resolves at runtime.
        bundledPlugin("com.intellij.java")

        testFramework(TestFrameworkType.Platform)
    }

    // The graph schema is intentionally vendored as plain Kotlin DTOs in this build (see
    // graph/GraphDocument.kt) to keep the plugin a self-contained composite build. It mirrors
    // io.github.darkryh.katalyst.analysis.export.GraphDocument.
}

intellijPlatform {
    pluginConfiguration {
        id = "io.github.darkryh.katalyst.idea"
        name = "Katalyst Support"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null } // do not pin an upper bound for 1.0
        }
    }

    // Plugin signing for CI releases. The certificate chain and (unencrypted) private key are read
    // from the environment (GitHub Actions secrets) as PEM *content* — nothing is committed. The key
    // is unencrypted, so no password is configured. See PUBLISHING.md and release-plugin.yml.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
    }

    // Publishing to the JetBrains Marketplace. The token is read from the environment only.
    // Use the "default" channel for stable releases; "beta"/"eap" for pre-releases.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.environmentVariable("PUBLISH_CHANNEL")
            .map { listOf(it) }
            .orElse(listOf("default"))
    }

    // `verifyPlugin` runs the IntelliJ Plugin Verifier against explicit, released IDE builds — this
    // catches K2/API-compatibility regressions. Pinned (not recommended()) so verification is
    // deterministic and never trips over an unreleased EAP build.
    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2")
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

// IntelliJ platform tests (BasePlatformTestCase) run on JUnit4, not the JUnit Platform.
tasks.test {
    useJUnit()
}
