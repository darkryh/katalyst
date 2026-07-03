#!/bin/bash

# Build and launch the katalyst-tui inspector in the current terminal.
#
# The inspector is a full-screen Dispatch TUI: it needs a real interactive terminal (TTY),
# which the IDE Run window and `gradlew run` do not provide. Run this from Terminal.app,
# iTerm, the IntelliJ Terminal tab, or an ssh session on the machine where the backend runs.

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BIN_FILE="$SCRIPT_DIR/katalyst-tui/build/install/katalyst-tui/bin/katalyst-tui"

echo "Building katalyst-tui..."
cd "$SCRIPT_DIR" || exit 1
./gradlew :katalyst-tui:installDist --warning-mode all || {
    echo "Build failed"
    exit 1
}

if [ ! -f "$BIN_FILE" ]; then
    echo "Error: Binary not found after build!"
    exit 1
fi

if [ -t 0 ] && [ -t 1 ]; then
    exec "$BIN_FILE" "$@"
else
    echo "Non-interactive terminal detected; skipping launch."
    echo "Run ./tui.sh from a real terminal, or validate the attach chain headlessly with:"
    echo "  java -cp \"$SCRIPT_DIR/katalyst-tui/build/install/katalyst-tui/lib/*\" io.github.darkryh.katalyst.tui.AttachDoctorKt"
    exit 0
fi
