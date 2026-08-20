package io.github.darkryh.katalyst.analysis

import io.github.darkryh.katalyst.analysis.export.KatalystGraphJson
import io.github.darkryh.katalyst.analysis.fixtures.app.GreetingService
import io.github.darkryh.katalyst.analysis.model.DiagnosticKind
import io.github.darkryh.katalyst.analysis.model.DiscoveryRule
import io.github.darkryh.katalyst.analysis.model.DiagnosticSeverity
import io.github.darkryh.katalyst.analysis.model.KatalystApplicationGraph
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KatalystAnalyzerTest {

    private val fixturePackage = "io.github.darkryh.katalyst.analysis.fixtures.app"

    private fun analyze(): KatalystApplicationGraph {
        // The compiled test classes directory is the classpath root we hand to the analyzer.
        val testClassesDir = File(
            GreetingService::class.java.protectionDomain.codeSource.location.toURI()
        )
        return KatalystAnalyzer().analyze(
            KatalystAnalysisConfig(
                scanPackages = listOf(fixturePackage),
                classpath = listOf(testClassesDir),
            )
        )
    }

    private fun simpleNames(fqNames: Collection<String>) = fqNames.map { it.substringAfterLast('.') }.toSet()

    @Test
    fun `discovers every kind of katalyst entrypoint`() {
        val graph = analyze()

        assertEquals(setOf("GreetingService"), simpleNames(graph.services.map { it.symbol.fqName }))
        assertEquals(setOf("GreetingValidator"), simpleNames(graph.components.map { it.symbol.fqName }))
        assertEquals(setOf("GreetingRepository"), simpleNames(graph.repositories.map { it.symbol.fqName }))
        assertEquals(setOf("GreetingsTable"), simpleNames(graph.tables.map { it.symbol.fqName }))
        assertEquals(setOf("GreetingCreatedHandler"), simpleNames(graph.eventHandlers.map { it.symbol.fqName }))
        assertEquals(setOf("V1AddGreeting"), simpleNames(graph.migrations.map { it.symbol.fqName }))
        // Both config discovery paths: the ConfigBinding interface and the @ConfigPrefix annotation.
        assertEquals(setOf("GreetingConfig", "MailConfig"), simpleNames(graph.configLoaders.map { it.symbol.fqName }))
        assertEquals(setOf("GreetingInitializer"), simpleNames(graph.initializers.map { it.symbol.fqName }))
    }

    @Test
    fun `classifies function entrypoints by the DSL they call`() {
        val graph = analyze()

        assertEquals(listOf("greetingRoutes"), graph.routes.map { it.symbol.simpleName })
        assertEquals(listOf("greetingMiddleware"), graph.middleware.map { it.symbol.simpleName })
        assertEquals(listOf("greetingWebSockets"), graph.websockets.map { it.symbol.simpleName })
        assertEquals(listOf("greetingExceptionHandlers"), graph.exceptionHandlers.map { it.symbol.simpleName })

        // The decoy must not be discovered as any function entrypoint.
        val routeFnNames = (graph.routes + graph.middleware + graph.websockets + graph.exceptionHandlers)
            .map { it.symbol.simpleName }
        assertFalse("forgottenRoutes" in routeFnNames)
    }

    @Test
    fun `resolves generic type arguments and scheduler methods`() {
        val graph = analyze()

        assertEquals("Greeting", graph.repositories.single().entityType?.substringAfterLast('.'))
        assertEquals("GreetingCreatedEvent", graph.eventHandlers.single().eventType?.substringAfterLast('.'))
        // @ConfigPrefix class is discovered via the annotation rule and registered by its own type.
        val mailConfig = graph.configLoaders.single { it.symbol.simpleName == "MailConfig" }
        assertEquals(DiscoveryRule.ANNOTATED_MARKER, mailConfig.reason.rule)
        assertEquals("MailConfig", mailConfig.configType?.substringAfterLast('.'))
        assertEquals(listOf("scheduleDigest"), graph.schedulers.map { it.symbol.simpleName })
    }

    @Test
    fun `wiring is valid and only the decoy raises a diagnostic`() {
        val graph = analyze()

        assertFalse(graph.hasErrors, "expected no error diagnostics but got: ${graph.diagnostics}")

        val invalidSignature = graph.diagnostics.filter { it.kind == DiagnosticKind.INVALID_DSL_SIGNATURE }
        assertEquals(1, invalidSignature.size)
        assertTrue(invalidSignature.single().symbolFqName!!.endsWith("#forgottenRoutes"))
        assertEquals(DiagnosticSeverity.WARNING, invalidSignature.single().severity)
    }

    @Test
    fun `every discovered symbol is marked used`() {
        val graph = analyze()
        // The whole point of the tooling: these symbols carry no static call site yet are "used".
        assertTrue("${fixturePackage}.RoutesKt#greetingRoutes" in graph.usedSymbolFqNames)
        assertTrue(graph.usedSymbolFqNames.any { it.endsWith("GreetingCreatedHandler") })
    }

    @Test
    fun `graph round-trips through json`() {
        val graph = analyze()
        val json = KatalystGraphJson.encode(graph)
        val document = KatalystGraphJson.decode(json)

        assertEquals(graph.allNodes.size, document.nodes.size)
        assertEquals(graph.scanPackages, document.scanPackages)
        assertTrue(document.nodes.any { it.kind == "ROUTE" && it.simpleName == "greetingRoutes" })
    }
}
