#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
out="$(mktemp -d "${TMPDIR:-/tmp}/phantasia-editor-authoring.XXXXXX")"
trap 'rm -rf "$out"' EXIT
find phantasia-core/src/main/java -name '*.java' -print | LC_ALL=C sort > "$out/sources.txt"
for file in EditCommand EditorStateListener EditorState; do
    echo "phantasia-editor/src/main/java/com/phantasia/editor/$file.java" >> "$out/sources.txt"
done
echo phantasia-editor/src/main/java/com/phantasia/editor/commands/EditFeatureCommand.java >> "$out/sources.txt"
echo phantasia-editor/src/main/java/com/phantasia/editor/panels/FeatureEditForm.java >> "$out/sources.txt"
echo baseline-tests/EditorAuthoringSmokeTest.java >> "$out/sources.txt"
javac --release 21 -d "$out" @"$out/sources.txt"
java -Djava.awt.headless=true -cp "$out" com.phantasia.editor.EditorAuthoringSmokeTest
