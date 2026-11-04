#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
core='phantasia-core/src/main/java'
hits="$(grep -R -nE 'javax\.swing|java\.awt|javafx\.' --include='*.java' "$core" \
  | grep -Ev ':[0-9]+:[[:space:]]*(\*|//)' || true)"
if [[ -n "$hits" ]]; then
  printf '%s\n' "$hits"
  echo 'FAIL: core contains desktop UI dependencies.' >&2
  exit 1
fi
if grep -R -nE '^import com\.phantasia\.editor\.' --include='*.java' "$core"; then
  echo 'FAIL: core depends on desktop editor.' >&2
  exit 1
fi
echo 'PASS: core has no Swing/AWT/JavaFX or desktop editor references.'
