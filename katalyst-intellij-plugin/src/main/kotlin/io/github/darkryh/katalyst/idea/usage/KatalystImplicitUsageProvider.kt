package io.github.darkryh.katalyst.idea.usage

import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.psi.PsiElement
import io.github.darkryh.katalyst.idea.psi.KatalystPsi
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Tells the IDE that Katalyst entrypoints are *used*, even when nothing in the project references
 * them — which is the normal case for routes, middleware, websockets, exception handlers, event
 * handlers, migrations, initializers, config loaders and scheduled jobs, all of which Katalyst
 * discovers reflectively at startup.
 *
 * This is what removes the need for `@Suppress("unused")` on Katalyst code: the Kotlin unused-symbol
 * inspection consults every [ImplicitUsageProvider] before reporting a declaration as unused.
 */
class KatalystImplicitUsageProvider : ImplicitUsageProvider {

    override fun isImplicitUsage(element: PsiElement): Boolean {
        // The Kotlin unused-symbol inspection delegates to the Java unused-detection using the
        // *light* element — e.g. a top-level `fun authRoutes()` is checked as a PsiMethod on the
        // file-facade class (AuthRoutesKt), not as the KtNamedFunction. Unwrap to the Kotlin origin
        // so we recognise it. (The gutter provider sees the KtNamedFunction directly, which is why
        // detection worked but suppression didn't until now.)
        return when (val declaration = element.unwrapped) {
            is KtClassOrObject -> KatalystPsi.isEntrypoint(declaration)
            is KtNamedFunction -> KatalystPsi.isEntrypoint(declaration)
            else -> false
        }
    }

    // Katalyst never reads or writes fields implicitly; only declarations are implicitly *used*.
    override fun isImplicitRead(element: PsiElement): Boolean = false

    override fun isImplicitWrite(element: PsiElement): Boolean = false
}
