package io.github.darkryh.katalyst.scanner.pkgfilter

/**
 * Marker interface used exclusively by the package-filtering tests.
 *
 * Its implementations are deliberately spread across a small package tree so that
 * `excludePackages` / `includeSubPackages` can be exercised through a real Reflections
 * traversal instead of through configuration round-trips:
 *
 * ```
 * pkgfilter
 *   ├─ included            -> IncludedRootService
 *   │   └─ nested          -> IncludedNestedService
 *   ├─ excluded            -> ExcludedService
 *   │   └─ deeper          -> DeeplyExcludedService
 *   └─ excludedish         -> PrefixLookalikeService   (shares a string prefix with `excluded`
 *                                                       but is NOT one of its sub-packages)
 * ```
 *
 * Nothing outside these tests scans `io.github.darkryh.katalyst.scanner.pkgfilter`, so the
 * fixture set is stable and countable.
 */
interface PackageFilterMarker
