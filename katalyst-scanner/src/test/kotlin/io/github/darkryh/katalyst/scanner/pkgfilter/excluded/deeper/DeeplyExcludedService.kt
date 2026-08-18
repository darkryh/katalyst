package io.github.darkryh.katalyst.scanner.pkgfilter.excluded.deeper

import io.github.darkryh.katalyst.scanner.pkgfilter.PackageFilterMarker

/** Lives *below* the excluded package: exclusion must be transitive. */
class DeeplyExcludedService : PackageFilterMarker
