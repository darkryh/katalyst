package io.github.darkryh.katalyst.core.annotation

/**
 * Marks a declaration as Katalyst framework-internal infrastructure.
 *
 * Such declarations are `public` only because they are shared across framework
 * modules (Kotlin `internal` does not cross module boundaries). They are **not**
 * part of the supported public API: application code should not depend on them,
 * and they are excluded from the committed `api/<module>.api` surface via the
 * binary-compatibility-validator `nonPublicMarkers` configuration.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
annotation class KatalystInternalApi
