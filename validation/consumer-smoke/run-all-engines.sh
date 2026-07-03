#!/usr/bin/env bash
# Validates the published Katalyst consumption path against every engine.
#
#   1. (from the framework root)  ./gradlew publishToMavenLocal
#   2. (from here)                ./run-all-engines.sh
#
# For each engine it runs `check`, which compiles the app + the engine source set, boots the full
# stack in the test (DI + Exposed/H2 + serialization + routing), and runs verifyEngineClasspath
# (selected engine present, other two absent, Ktor + Exposed transitively present, none declared).
set -euo pipefail

cd "$(dirname "$0")"
GRADLEW="../../gradlew"

for engine in netty jetty cio; do
    echo "==================== engine: $engine ===================="
    "$GRADLEW" -p . clean check "-PkatalystEngine=$engine"
done

echo "All engines validated."
