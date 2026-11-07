# Editor persistence increment

Base: `work/april-source-reconciliation` at `6e878fdb126bdf3affbec9bba75ed218e0b43c80`.

## Result

Project archives now save and reopen interior maps, NPC definitions and shop
inventories through the same codecs used by runtime exports. Loose-file loading
also reads these collections. Full bake writes all three collections even if
project save has cleared their dirty flags, or deletion has emptied a collection.

Project save stages a copy beside the destination and replaces the original only
after the ZIP closes successfully. An unsupported payload fails explicitly; it
does not turn into null or overwrite the last good project. Existing unrelated
archive entries are retained when updating an archive.

## Formats

All writers now emit version 2. Readers retain version 1 support. Old application
binaries cannot read the new version 2 files; update runtime and editor together.
Fields missing from an existing v1 file cannot be recovered by this change.

Interior headers remain `INTR`, u16 version and u16 map count. The v2 records use
32-bit integers for IDs, grid cells, dimensions and settings. Strings have an i32
UTF-8 byte length, with -1 for null. Enum and quest flag names are explicit wire
identifiers. Renaming these identifiers requires a migration. Counts, strings and
the total number of grid cells are bounded at 1,000,000; map count remains u16.

Each feature carries a tagged payload: none, text, integer reference, inn, guild,
bank, lock, stairs, exit, trap, loot table or inline shop inventory. Flags, visual
overrides, NPC visibility conditions and map music survive. Exploration remains
campaign state and is excluded. Payload preservation does not validate that a
particular feature type has the correct payload; authoring validation is next.

NPC v2 adds nullable sprite/portrait keys and the complete nullable animation
profile before dialogue. Existing dialogue format and u16 ID limits remain.
Shop v2 adds its nullable restock flag after the name; inventory format remains.
New guards reject values that would overflow the existing NPC/shop u16 fields.

## Verification

Run on Java 21:

```bash
bash run_editor_persistence.sh
bash run_core_baseline.sh
./gradlew :phantasia-core:test :phantasia-j2d:compileJava :phantasia-editor:compileJava
```

The new dependency-free smoke test covers:

- Fresh EditorState save/reopen with every supported payload, Unicode, settings,
  ordered dialogue, flags, visual overrides, animation and stock.
- Runtime export/load after saving has cleared dirty state; loose-file reopening.
- Deletion and resave to the same archive without stale content returning.
- Failed save preserving the original archive bytes and dirty state.
- Explicit null versus empty optional NPC values and campaign-state exclusion.
- Independently written v1 interior/NPC/shop fixtures, truncated data, unsupported
  interior versions and trailing interior bytes.

Local result: **448 checks passed**. This host only has Java 17. The local check
compiled a temporary copy with unrelated Math.clamp calls replaced by equivalent
min/max calls and List.getFirst calls replaced by get(0), enabling Java 17 switch
preview support. The changed persistence sources and smoke test were otherwise
unchanged. This is useful execution evidence, not a native Java 21 build result.
No UI was launched. The existing core CI workflow now runs this test on Java 21
and includes the `work/editor-persistence` branch; consult the actual workflow
result for native build status.

## Remaining work

This increment does not complete whole-project preservation. Items, quests,
legacy town/dungeon definitions and broader editor metadata still require their
own archive work. Typed feature forms, uniform undo, reference validation,
explicit entrances, shared traversal and campaign/native-v5 integration remain
pending. Runtime loader equivalence is tested; appearance and interaction in an
actual game window still need a later visual check.

Next: typed feature editing and validation, beginning with signs, stairs/exits,
locks and chest contents. Keep diagnostic content separate from story canon.
