package io.github.darkryh.katalyst.idea.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import io.github.darkryh.katalyst.idea.convention.PluginConventions
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/** The kind of Katalyst entrypoint a PSI declaration represents. Drives gutter icons + tooltips. */
enum class EntrypointKind(val label: String) {
    SERVICE("Katalyst service"),
    COMPONENT("Katalyst component"),
    REPOSITORY("Katalyst repository"),
    TABLE("Katalyst table"),
    EVENT_HANDLER("Katalyst event handler"),
    MIGRATION("Katalyst migration"),
    INITIALIZER("Katalyst application initializer"),
    CONFIG_LOADER("Katalyst config loader"),
    KTOR_MODULE("Katalyst Ktor module"),
    ROUTE("Katalyst route"),
    MIDDLEWARE("Katalyst middleware"),
    WEBSOCKET("Katalyst websocket"),
    EXCEPTION_HANDLER("Katalyst exception handler"),
    SCHEDULER("Katalyst scheduled job"),
}

/**
 * Recognises Katalyst entrypoints from Kotlin PSI, applying the same closed rule-set the runtime
 * and `katalyst-analysis` use (sourced from [PluginConventions]).
 *
 * All checks are resolve-light and defensive: marker membership uses light classes + the platform's
 * inheritance index; route functions are matched by their DSL call; scheduler methods by their
 * (possibly inferred) return type via light methods. Nothing here boots or instantiates anything.
 */
object KatalystPsi {

    /** The entrypoint kind for a class/object, or null if it is not Katalyst-managed. */
    fun classKind(klass: KtClassOrObject): EntrypointKind? {
        if (klass.isAbstractOrInterface()) return null
        val light: PsiClass = runCatching { klass.toLightClass() }.getOrNull() ?: return null
        val marker = PluginConventions.markerInterfaces.firstOrNull { fqn ->
            runCatching { InheritanceUtil.isInheritor(light, fqn) }.getOrDefault(false)
        } ?: return null
        return markerKind(marker)
    }

    /** The entrypoint kind for a top-level function, or null if it is not a Katalyst entrypoint. */
    fun functionKind(function: KtNamedFunction): EntrypointKind? {
        routeKind(function)?.let { return it }
        if (isSchedulerFunction(function)) return EntrypointKind.SCHEDULER
        return null
    }

    /** True if this declaration is a Katalyst entrypoint of any kind. */
    fun isEntrypoint(klass: KtClassOrObject): Boolean = classKind(klass) != null

    fun isEntrypoint(function: KtNamedFunction): Boolean = functionKind(function) != null

    /** The Katalyst DSL functions invoked in a function body (empty if none). */
    fun dslCalls(function: KtNamedFunction): Set<String> {
        // Scan the whole function, not just its body expression. For an expression body like
        // `fun Route.authRoutes() = katalystRouting { ... }` the body *is* the katalystRouting call,
        // and PsiTreeUtil.findChildrenOfType(body, …) would miss it because it only returns
        // descendants — not the root element. Collecting from the function includes that top-level call.
        return PsiTreeUtil.collectElementsOfType(function, KtCallExpression::class.java)
            .mapNotNull { it.calleeExpression?.text }
            .filterTo(mutableSetOf()) { it in PluginConventions.dslMethodNames }
    }

    private fun routeKind(function: KtNamedFunction): EntrypointKind? {
        // A route function is an extension function whose body calls a Katalyst routing DSL.
        if (function.receiverTypeReference == null) return null
        val calls = dslCalls(function)
        return when {
            PluginConventions.DSL_EXCEPTION_HANDLER in calls -> EntrypointKind.EXCEPTION_HANDLER
            PluginConventions.DSL_MIDDLEWARE in calls -> EntrypointKind.MIDDLEWARE
            PluginConventions.DSL_WEBSOCKETS in calls -> EntrypointKind.WEBSOCKET
            PluginConventions.DSL_ROUTING in calls -> EntrypointKind.ROUTE
            else -> null
        }
    }

    private fun isSchedulerFunction(function: KtNamedFunction): Boolean {
        // Scheduler entrypoints are service *members*, never top-level functions.
        if (function.parent is KtFile) return false
        return runCatching {
            function.toLightMethods().any { method ->
                method.returnType?.canonicalText == PluginConventions.SCHEDULER_JOB_HANDLE
            }
        }.getOrDefault(false)
    }

    private fun markerKind(markerFqn: String): EntrypointKind = when (markerFqn) {
        PluginConventions.SERVICE -> EntrypointKind.SERVICE
        PluginConventions.CRUD_REPOSITORY -> EntrypointKind.REPOSITORY
        PluginConventions.TABLE -> EntrypointKind.TABLE
        PluginConventions.EVENT_HANDLER -> EntrypointKind.EVENT_HANDLER
        PluginConventions.KATALYST_MIGRATION -> EntrypointKind.MIGRATION
        PluginConventions.APPLICATION_INITIALIZER,
        PluginConventions.APPLICATION_READY_INITIALIZER -> EntrypointKind.INITIALIZER
        PluginConventions.AUTOMATIC_SERVICE_CONFIG_LOADER -> EntrypointKind.CONFIG_LOADER
        PluginConventions.KTOR_MODULE -> EntrypointKind.KTOR_MODULE
        else -> EntrypointKind.COMPONENT
    }

    private fun KtClassOrObject.isAbstractOrInterface(): Boolean =
        this is KtClass && (isInterface() || hasModifier(KtTokens.ABSTRACT_KEYWORD))
}
