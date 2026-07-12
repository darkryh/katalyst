#!/usr/bin/env bash
#
# Validate every Katalyst sample as a real EXTERNAL CONSUMER of the published library.
#
# It publishes the current library to your local Maven repo (~/.m2) and then builds every
# sample against it — proving the `io.github.darkryh.katalyst` Gradle plugin and the
# katalyst-* starter artifacts resolve exactly as they will for a real user consuming the
# release. The samples are standalone builds (not part of the library's Gradle build), so
# they can only see the library through what is actually published.
#
# Run this whenever you ADD or CHANGE a sample, and in CI. Extra args are passed to Gradle.
#
#   ./samples/validate-samples.sh                 # full validation
#   ./samples/validate-samples.sh --console=plain # e.g. on CI
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> [1/2] Publishing Katalyst to mavenLocal (~/.m2) ..."
( cd "$ROOT" && ./gradlew publishToMavenLocal "$@" )

echo "==> [2/2] Building all samples against mavenLocal ..."
( cd "$ROOT/samples" && ./gradlew build "$@" )

echo ""
echo "==> OK — samples validated: the io.github.darkryh.katalyst plugin and all starters"
echo "    resolve from the published artifacts for a real consumer."
