#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
out="${TMPDIR:-/tmp}/phantasia-april-core-classes-$$"
trap 'rm -rf "$out"' EXIT
mkdir -p "$out"
find phantasia-core/src/main/java -name '*.java' -print | LC_ALL=C sort > "$out/sources.txt"
javac --release 21 -d "$out" @"$out/sources.txt" baseline-tests/CoreBaselineSmokeTest.java
java -cp "$out" com.phantasia.baseline.CoreBaselineSmokeTest

bash baseline-tests/verify_headless_core.sh
