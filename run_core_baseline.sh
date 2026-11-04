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

# Additive v5 schema checks; production still uses save v4.
javac --release 21 -cp "$out" -d "$out" baseline-tests/V5LayoutSmokeTest.java
java -cp "$out" com.phantasia.baseline.V5LayoutSmokeTest

# Additive typed v5 player-state smoke checks; gameplay still uses v4.
javac --release 21 -cp "$out" -d "$out" baseline-tests/PlayerStateV5SmokeTest.java
java -cp "$out" com.phantasia.baseline.PlayerStateV5SmokeTest
