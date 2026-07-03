#!/bin/bash

# Build and run the katalyst-example backend in the current terminal.
#
# Run this from a real terminal (Terminal.app, iTerm, the IntelliJ Terminal tab, or ssh): once the
# app is ready, the embedded Katalyst TUI inspector takes over the screen as the default developer
# view. Double Ctrl+C quits the inspector and stops the backend. Without a TTY (piped output, IDE
# Run window) the app falls back to normal logs and prints a one-time notice instead.
# Opt out of the TUI entirely with: ./run.sh -Dkatalyst.tui.enabled=false (via JAVA_OPTS) or
# KATALYST_TUI_ENABLED=false ./run.sh

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SAMPLES_DIR="$SCRIPT_DIR/.."
GRADLEW="$SCRIPT_DIR/../../gradlew"
BIN_FILE="$SCRIPT_DIR/build/install/katalyst-example/bin/katalyst-example"

echo "Building katalyst-example..."
cd "$SAMPLES_DIR" || exit 1
"$GRADLEW" :katalyst-example:installDist --warning-mode all || {
    echo "Build failed"
    exit 1
}

if [ ! -f "$BIN_FILE" ]; then
    echo "Error: Binary not found after build!"
    exit 1
fi

if [ -t 0 ] && [ -t 1 ]; then
    echo "Launching katalyst-example (bootstrap progress renders in the TUI, then the inspector)..."
else
    echo "No interactive terminal detected: running with plain logs (no TUI)."
fi
exec "$BIN_FILE" "$@"
