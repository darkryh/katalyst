#!/usr/bin/env bash
set -euo pipefail

OLD='com.ead.katalyst'
NEW='io.github.darkryh.katalyst'

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Renaming packages from ${OLD} -> ${NEW} under ${ROOT}, excluding projects/boshi"

python3 - <<'PY'
import pathlib
import re

root = pathlib.Path(__file__).resolve().parent  # scripts/.. = repo root
old = re.compile(r'\bcom\.ead\.katalyst\b')
new = 'io.github.darkryh.katalyst'

skips = ('projects/boshi/',)

for path in root.rglob('*.kt'):
    rel = path.relative_to(root)
    if any(str(rel).startswith(s) for s in skips):
        continue
    text = path.read_text()
    if not old.search(text):
        continue
    path.write_text(old.sub(new, text))
    print(f"rewrote {rel}")
PY

echo "Done. Consider moving directories to match the new namespace if needed."
