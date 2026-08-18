package io.github.darkryh.katalyst.core.exception

import kotlin.reflect.KClass

/**
 * Thrown by [io.github.darkryh.katalyst.core.di.KatalystContainer.get] when no bean is registered
 * for the requested type.
 *
 * This is the resolution counterpart to the registration failures its parent covers, and it is the
 * one DI failure an application is likely to catch deliberately: "the bean I asked for is not
 * there" is a different situation from "wiring is broken", and it deserves a type of its own.
 *
 * **Why a framework type rather than the engine's own.** [io.github.darkryh.katalyst.core.di.KatalystContainer]
 * is the engine-agnostic seam: Koin backs it in production and an in-memory engine backs it under
 * `katalystTestEnvironment`. Letting each engine surface its own exception would mean a caller
 * could only catch the production one by putting that engine on its own compile classpath, and
 * would see a different type in tests than in production — the exact class of divergence that let
 * a scheduler ship broken while its suite was green. Every engine raises this instead, keeping the
 * engine's own exception as [cause] for anyone who needs it.
 *
 * [requestedType] and [qualifier] are carried as fields so a caller can react to *what* was missing
 * without parsing [message].
 *
 * @param requestedType the type that could not be resolved
 * @param qualifier the qualifier that was requested, or `null` for an unqualified lookup
 * @param cause the underlying engine exception, when there was one
 */
class BeanNotFoundException(
    val requestedType: KClass<*>,
    val qualifier: String? = null,
    cause: Throwable? = null,
) : DependencyInjectionException(buildMessage(requestedType, qualifier), cause) {

    private companion object {
        fun buildMessage(requestedType: KClass<*>, qualifier: String?): String {
            val name = requestedType.qualifiedName ?: requestedType.simpleName ?: "<unknown type>"
            val qualifierHint = qualifier?.let { " with qualifier '$it'" } ?: ""
            return "No bean registered for $name$qualifierHint. " +
                "Check that the type is annotated for discovery, that its package is scanned, " +
                "and that the feature providing it is on the classpath."
        }
    }
}
