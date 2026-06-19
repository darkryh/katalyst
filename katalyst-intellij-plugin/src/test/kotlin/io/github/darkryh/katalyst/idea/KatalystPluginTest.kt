package io.github.darkryh.katalyst.idea

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.darkryh.katalyst.idea.inspection.KatalystDslSignatureInspection
import io.github.darkryh.katalyst.idea.psi.EntrypointKind
import io.github.darkryh.katalyst.idea.psi.KatalystPsi
import io.github.darkryh.katalyst.idea.usage.KatalystImplicitUsageProvider
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Regression tests for the Katalyst plugin's recognition logic. These pin the behaviours that were
 * each broken at some point during 0.1.x:
 *  - expression-body DSL detection (`= katalystRouting { }`),
 *  - the headline "never used" suppression, which the Kotlin inspection drives through the *light*
 *    PsiMethod (not the KtNamedFunction) — the bug that made gutters work but suppression fail,
 *  - DSL classification into route/middleware/websocket/exception-handler,
 *  - the signature inspection only firing when a DSL call is genuinely missing.
 *
 * The recognition uses only PSI (no symbol resolution), so these snippets need no Ktor/Katalyst on
 * the classpath — the receiver reference and the DSL call text are enough.
 */
class KatalystPluginTest : BasePlatformTestCase() {

    private fun functionNamed(code: String, name: String): KtNamedFunction {
        val file = myFixture.configureByText("Sample.kt", code) as KtFile
        return PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java).first { it.name == name }
    }

    // --- DSL detection (expression-body regression) ---

    fun testExpressionBodyRouteIsDetected() {
        val fn = functionNamed("""fun Route.authRoutes() = katalystRouting { route("/x") { } }""", "authRoutes")
        assertTrue("katalystRouting" in KatalystPsi.dslCalls(fn))
        assertEquals(EntrypointKind.ROUTE, KatalystPsi.functionKind(fn))
    }

    fun testBlockBodyRouteIsDetected() {
        val fn = functionNamed("fun Route.userRoutes() { katalystRouting { } }", "userRoutes")
        assertEquals(EntrypointKind.ROUTE, KatalystPsi.functionKind(fn))
    }

    fun testMiddlewareWebsocketExceptionClassification() {
        assertEquals(
            EntrypointKind.MIDDLEWARE,
            KatalystPsi.functionKind(functionNamed("fun Application.mw() = katalystMiddleware { }", "mw")),
        )
        assertEquals(
            EntrypointKind.WEBSOCKET,
            KatalystPsi.functionKind(functionNamed("fun Route.ws() = katalystWebSockets { }", "ws")),
        )
        assertEquals(
            EntrypointKind.EXCEPTION_HANDLER,
            KatalystPsi.functionKind(functionNamed("fun Application.eh() = katalystExceptionHandler { }", "eh")),
        )
    }

    fun testReceiverFunctionWithoutDslIsNotEntrypoint() {
        val fn = functionNamed("""fun Route.helper() { route("/x") { } }""", "helper")
        assertNull(KatalystPsi.functionKind(fn))
    }

    // --- Implicit usage (the light-element unwrap regression) ---

    fun testEntrypointMarkedUsedDirectlyAndViaLightMethod() {
        val fn = functionNamed("fun Route.authRoutes() = katalystRouting { }", "authRoutes")
        val provider = KatalystImplicitUsageProvider()

        // Direct KtNamedFunction (what the gutter sees):
        assertTrue(provider.isImplicitUsage(fn))

        // The path the Kotlin unused-symbol inspection actually uses — the light PsiMethod on the
        // file facade. This is the case that previously returned false, leaving the "never used" grey.
        val lightMethod = fn.toLightMethods().first()
        assertTrue("light method must be recognized via unwrap", provider.isImplicitUsage(lightMethod))
    }

    fun testNonEntrypointIsNotMarkedUsed() {
        val fn = functionNamed("fun Route.helper() { }", "helper")
        assertFalse(KatalystImplicitUsageProvider().isImplicitUsage(fn))
    }

    // --- Signature inspection ---

    fun testInspectionIsSilentOnValidRoute() {
        myFixture.enableInspections(KatalystDslSignatureInspection())
        myFixture.configureByText("Sample.kt", "fun Route.authRoutes() = katalystRouting { }")
        val ours = myFixture.doHighlighting().filter { it.description?.contains("will not be discovered") == true }
        assertEmpty(ours)
    }

    fun testInspectionWarnsWhenDslIsMissing() {
        myFixture.enableInspections(KatalystDslSignatureInspection())
        myFixture.configureByText("Sample.kt", "fun Route.adminRoutes() { }")
        val ours = myFixture.doHighlighting().filter { it.description?.contains("will not be discovered") == true }
        assertTrue("expected a missing-DSL warning", ours.isNotEmpty())
    }
}
