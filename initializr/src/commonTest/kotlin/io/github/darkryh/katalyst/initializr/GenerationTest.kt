package io.github.darkryh.katalyst.initializr

import io.github.darkryh.katalyst.initializr.generate.ProjectGenerator
import io.github.darkryh.katalyst.initializr.model.Engine
import io.github.darkryh.katalyst.initializr.model.Feature
import io.github.darkryh.katalyst.initializr.model.FeatureSelection
import io.github.darkryh.katalyst.initializr.model.ProjectConfig
import io.github.darkryh.katalyst.initializr.model.ProjectConfigValidator
import io.github.darkryh.katalyst.initializr.template.StarterTemplate
import io.github.darkryh.katalyst.initializr.template.TemplateFile
import io.github.darkryh.katalyst.initializr.zip.Base64
import io.github.darkryh.katalyst.initializr.zip.Crc32
import io.github.darkryh.katalyst.initializr.zip.ZipArchive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerationTest {
    private val base =
        ProjectConfig(
            projectName = "Acme Service",
            groupId = "com.acme",
            artifactId = "acme-service",
            packageName = "com.acme.service",
            appVersion = "0.2.0",
            selection = FeatureSelection.FULL,
        )

    private val appPath = "src/main/kotlin/com/acme/service/Application.kt"

    private fun gen(config: ProjectConfig) = ProjectGenerator.generate(config)

    private fun content(config: ProjectConfig, path: String): String =
        gen(config).single { it.path == path }.content

    private fun paths(config: ProjectConfig) = gen(config).map { it.path }

    private fun with(vararg features: Feature, engine: Engine = Engine.NETTY) =
        base.copy(selection = FeatureSelection(engine = engine, features = features.toSet()))

    @Test
    fun defaultConfigIsValid() {
        assertTrue(ProjectConfigValidator.isValid(ProjectConfig.DEFAULT))
    }

    @Test
    fun substitutesEveryPlaceholderForEverySelection() {
        val selections =
            listOf(FeatureSelection.FULL, FeatureSelection.MINIMAL, FeatureSelection.STANDARD)
        for (sel in selections) {
            for (file in gen(base.copy(selection = sel))) {
                assertFalse(file.path.contains("{{"), "unsubstituted token in path: ${file.path}")
                assertFalse(file.content.contains("{{"), "unsubstituted token in ${file.path}")
            }
        }
    }

    @Test
    fun placesSourcesUnderPackagePath() {
        val p = paths(base)
        assertTrue(appPath in p, "Application.kt not at package path: $p")
        assertTrue("src/main/kotlin/com/acme/service/routes/HealthCheckRoutes.kt" in p)
    }

    @Test
    fun buildFileTracksSelectedStarters() {
        val full = content(base, "build.gradle.kts")
        assertTrue(full.contains("katalyst-bom:${StarterTemplate.KATALYST_VERSION}"))
        assertTrue(full.contains("katalyst-starter-web"))
        assertTrue(full.contains("katalyst-starter-engine-netty"))
        assertTrue(full.contains("katalyst-starter-persistence"))
        assertTrue(full.contains("katalyst-starter-scheduler"))
        assertTrue(full.contains("katalyst-starter-observability"))
        assertTrue(full.contains("katalyst-starter-test"))

        val minimal = content(base.copy(selection = FeatureSelection.MINIMAL), "build.gradle.kts")
        assertTrue(minimal.contains("katalyst-starter-web"))
        assertTrue(minimal.contains("katalyst-starter-engine-netty"))
        assertFalse(minimal.contains("katalyst-starter-persistence"))
        assertFalse(minimal.contains("katalyst-starter-scheduler"))
        assertFalse(minimal.contains("katalyst-starter-observability"))
    }

    @Test
    fun engineSelectionDrivesStarterAndBootstrap() {
        val jetty = with(engine = Engine.JETTY)
        assertTrue(content(jetty, "build.gradle.kts").contains("katalyst-starter-engine-jetty"))
        assertFalse(content(jetty, "build.gradle.kts").contains("katalyst-starter-engine-netty"))
        val app = content(jetty, appPath)
        assertTrue(app.contains("engine(JettyServer)"))
        assertTrue(app.contains("import io.github.darkryh.katalyst.ktor.engine.jetty.JettyServer"))
    }

    @Test
    fun applicationDslMatchesFeatures() {
        val app = content(base, appPath)
        assertTrue(app.contains("enableScheduler()"))
        assertTrue(app.contains("enableWebSockets()"))
        assertTrue(app.contains("enableMigrations()"))
        assertTrue(app.contains("database { fromConfiguration() }"))
        assertTrue(app.contains("schema { createMissing() }"))
        assertTrue(app.contains("scanPackages(\"com.acme.service\")"))

        val minimal = content(base.copy(selection = FeatureSelection.MINIMAL), appPath)
        assertTrue(minimal.contains("enableYamlConfiguration()"))
        assertFalse(minimal.contains("enableScheduler()"))
        assertFalse(minimal.contains("enableMigrations()"))
        assertFalse(minimal.contains("database {"))
        assertFalse(minimal.contains("schema {"))
    }

    @Test
    fun persistenceDemonstratorGatedOnPersistence() {
        val p = paths(base)
        assertTrue(p.any { it.endsWith("infra/database/tables/NotesTable.kt") })
        assertTrue(p.any { it.endsWith("infra/database/repositories/NoteRepository.kt") })
        assertTrue(p.any { it.endsWith("routes/NoteRoutes.kt") })

        val minimal = paths(base.copy(selection = FeatureSelection.MINIMAL))
        assertFalse(minimal.any { it.contains("NotesTable") })
        assertFalse(minimal.any { it.contains("NoteRoutes") })
    }

    @Test
    fun migrationsRequirePersistence() {
        // migrations selected but persistence NOT -> effectively off in files, deps and DSL.
        val cfg = with(Feature.MIGRATIONS)
        assertFalse(paths(cfg).any { it.contains("V1CreateNotes") })
        assertFalse(content(cfg, "build.gradle.kts").contains("katalyst-starter-migrations"))
        assertFalse(content(cfg, appPath).contains("enableMigrations()"))

        // with persistence, migrations come through.
        val ok = with(Feature.PERSISTENCE, Feature.MIGRATIONS)
        assertTrue(paths(ok).any { it.endsWith("migrations/V1CreateNotes.kt") })
        assertTrue(content(ok, appPath).contains("enableMigrations()"))
    }

    @Test
    fun runShIsGatedOnObservabilityAndExecutable() {
        val runSh = gen(base).single { it.path == "run.sh" }
        assertTrue(runSh.executable, "run.sh should be marked executable")
        assertTrue(runSh.content.startsWith("#!/usr/bin/env bash"), "run.sh missing shebang")
        assertTrue(runSh.content.contains("installDist"), "run.sh should build via installDist")
        assertTrue(runSh.content.contains("APP_NAME=\"acme-service\""), "run.sh should bind APP_NAME")
        assertTrue(runSh.content.contains("KATALYST_TUI_ENABLED=false"), "run.sh should document the opt-out")
        // {{D}} restored to a literal '$'
        assertTrue(runSh.content.contains("\$SCRIPT_DIR"), "run.sh should carry real bash \$ variables")
        assertFalse(runSh.content.contains("{{"), "run.sh has unsubstituted tokens")

        // no observability -> no run.sh at all.
        val noObs = with(Feature.PERSISTENCE, Feature.SCHEDULER, Feature.WEBSOCKETS)
        assertFalse(paths(noObs).contains("run.sh"))
    }

    @Test
    fun yamlAndLogbackCarryLiteralDollar() {
        val yaml = content(base, "src/main/resources/application.yaml")
        assertTrue(yaml.contains("\${SERVER_PORT:8080}"))
        assertTrue(yaml.contains("database:"))
        val logback = content(base, "src/main/resources/logback.xml")
        assertTrue(logback.contains("\${ROOT_LOG_LEVEL"))

        val minimalYaml =
            content(base.copy(selection = FeatureSelection.MINIMAL), "src/main/resources/application.yaml")
        assertFalse(minimalYaml.contains("database:"))
    }

    @Test
    fun settingsUsesArtifactId() {
        assertTrue(content(base, "settings.gradle.kts").contains("rootProject.name = \"acme-service\""))
    }

    @Test
    fun noLeftoverDollarMarker() {
        for (file in gen(base)) {
            assertFalse(file.content.contains("{{D}}"), "leftover {{D}} in ${file.path}")
        }
    }

    @Test
    fun zipRecordsUnixExecutableModeForRunSh() {
        val zip = ZipArchive.create(gen(base))
        val cdSig = byteArrayOf(0x50, 0x4B, 0x01, 0x02)
        var seenExecutable = false
        var i = 0
        while (i <= zip.size - 4) {
            if (zip[i] == cdSig[0] && zip[i + 1] == cdSig[1] && zip[i + 2] == cdSig[2] && zip[i + 3] == cdSig[3]) {
                val nameLen = (zip[i + 28].toInt() and 0xFF) or ((zip[i + 29].toInt() and 0xFF) shl 8)
                val mode = (zip[i + 40].toInt() and 0xFF) or ((zip[i + 41].toInt() and 0xFF) shl 8)
                val name = zip.copyOfRange(i + 46, i + 46 + nameLen).decodeToString()
                if (name == "run.sh") {
                    assertEquals(0x81ED, mode, "run.sh should be mode 0o100755")
                    seenExecutable = true
                } else {
                    assertEquals(0x81A4, mode, "$name should be mode 0o100644")
                }
            }
            i++
        }
        assertTrue(seenExecutable, "run.sh entry not found in central directory")
    }

    @Test
    fun zipHasValidSignatureAndEntryCount() {
        val files = gen(base)
        val zip = ZipArchive.create(files)
        assertEquals(0x50, zip[0].toInt() and 0xFF)
        assertEquals(0x4B, zip[1].toInt() and 0xFF)
        assertEquals(0x03, zip[2].toInt() and 0xFF)
        assertEquals(0x04, zip[3].toInt() and 0xFF)

        val eocdSig = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
        val eocd = lastIndexOf(zip, eocdSig)
        assertTrue(eocd >= 0, "no EOCD record found")
        val totalEntries = (zip[eocd + 10].toInt() and 0xFF) or ((zip[eocd + 11].toInt() and 0xFF) shl 8)
        assertEquals(files.size, totalEntries)
    }

    @Test
    fun crc32MatchesKnownVector() {
        assertEquals(0xCBF43926L, Crc32.compute("123456789".encodeToByteArray()))
    }

    @Test
    fun base64MatchesKnownVectors() {
        assertEquals("TWFu", Base64.encode("Man".encodeToByteArray()))
        assertEquals("TWE=", Base64.encode("Ma".encodeToByteArray()))
        assertEquals("TQ==", Base64.encode("M".encodeToByteArray()))
    }

    @Test
    fun zipNameFromArtifact() {
        assertEquals("acme-service.zip", ProjectGenerator.zipName(base))
    }

    private fun lastIndexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in haystack.size - needle.size downTo 0) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    // A tiny guard that the demonstrator files never accidentally embed a raw triple-quote (which
    // would not survive being authored inside StarterTemplate's own Kotlin strings).
    @Test
    fun noTripleQuoteInGeneratedKotlin() {
        for (file: TemplateFile in gen(base)) {
            if (file.path.endsWith(".kt")) {
                assertFalse(file.content.contains("\"\"\""), "triple-quote leaked into ${file.path}")
            }
        }
    }
}
