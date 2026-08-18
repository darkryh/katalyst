package io.github.darkryh.katalyst.scanner.pkgfilter.excludedish

import io.github.darkryh.katalyst.scanner.pkgfilter.PackageFilterMarker

/**
 * Package name starts with the string `...pkgfilter.excluded` but is a sibling package,
 * not a sub-package. A naive `startsWith` exclusion would wrongly drop this type.
 */
class PrefixLookalikeService : PackageFilterMarker
