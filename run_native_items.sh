#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
out="$(mktemp -d "${TMPDIR:-/tmp}/phantasia-native-items.XXXXXX")"
trap 'rm -rf "$out"' EXIT
find phantasia-core/src/main/java phantasia-j2d/src/main/java -name '*.java' -print | LC_ALL=C sort > "$out/sources.txt"
echo baseline-tests/NativeItemsSmokeTest.java >> "$out/sources.txt"
javac --release 21 -d "$out" @"$out/sources.txt"
java -Djava.awt.headless=true -cp "$out" com.phantasia.core.logic.NativeItemsSmokeTest
