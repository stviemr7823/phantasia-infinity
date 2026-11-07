#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
out="$(mktemp -d "${TMPDIR:-/tmp}/phantasia-mockup.XXXXXX")"
trap 'rm -rf "$out"' EXIT
find phantasia-core/src/main/java phantasia-j2d/src/main/java -name '*.java' -print | LC_ALL=C sort > "$out/sources.txt"
javac --release 21 -d "$out" @"$out/sources.txt"
java -cp "$out" com.phantasia.j2d.diagnostic.DiagnosticMain "$@"
