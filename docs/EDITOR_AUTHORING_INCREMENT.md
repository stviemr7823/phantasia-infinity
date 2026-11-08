# Typed interior feature authoring and validation

This increment follows the persistence work on `work/editor-persistence` (draft
PR #1). It does not change the binary formats or production combat rules.

## Editing

Right-click a map tile or double-click an entry in the feature list. The form has
fields for feature type, activation flag, consumption flag and visual override.
Its payload fields follow the selected type:

| Type | Fields |
| --- | --- |
| Sign / altar | Multiline text |
| Shop counter | Shop ID; existing inline inventory is retained unless explicitly replaced by a reference |
| Inn | Gold per HP, greeting, resurrection |
| Guild | Base training cost, cost scaling and level cap |
| Bank | Deposit and withdrawal percentages |
| Lock | Key item, unlock flag and message |
| Stairs | Destination map, floor and arrival coordinates |
| Exit | Return to entry point or explicit world coordinates |
| Trap | Damage range, detection threshold and message |
| Chest | Gold range, random draws, guaranteed item table and weighted random item table |

Switching types retains each type's draft fields. Invalid numeric input keeps the
form open. Merely opening or cancelling does not mutate the map. Updating an old
payload of the wrong type is identified as a replacement. Placement, editing and
removal each use one undo step and preserve feature order and metadata. The
feature list and canvas refresh after commands, undo and redo.

The inline shop option deliberately does not offer a second inventory editor.
Shop definitions remain editable through the existing shop editor. NPC definition
and placement editing are separate from this feature dialog.

## Validation boundary

The Validate button checks the project's authored maps, NPCs and shops. Full bake
and category bake for maps/NPCs/shops run the same validator before creating or
writing output. Project save remains permissive so incomplete content can be
preserved. Direct core codecs remain serialization tools and do not impose the
editor's authoring rules.

Diagnostics identify the map, coordinates or definition involved. They include:

- Duplicate IDs, missing names/types and unsupported tile sets or tile indices.
- Out-of-bounds feature/NPC placement, overlapping features and overlapping
  unconditional NPC placements.
- Wrong feature payloads and missing map, NPC, shop and item references.
- Invalid costs, percentages, stock, prices, trap ranges and treasure quantities.
- Missing, out-of-bounds or blocked stairs arrival positions.

Only `town_standard` and `dungeon_standard` tile sets and the current ItemTable
are registered today. Map ID 0 in StairsData retains its existing meaning of
"this map". Authored areas are separate map IDs with destination floor 0; nonzero
floors are rejected because authored InteriorMap has no floor collection. Use
positive IDs when authoring connected areas. Default/null exit data still means
return to the entry point.

These checks do not establish a reachable return route, evaluate quest conditions,
validate explicit world returns against a world map, or simulate a campaign.
Conditional NPC overlap requires later quest-aware checking. Named entrances and
shared town/dungeon/castle traversal remain pending. A successful validation is a
structural result, not proof that every interaction is implemented in the runtime.

Bake errors appear in a scrollable report. Failed Bake & Tour or Bake & Close
stops the follow-on action so it cannot tour stale output or discard work after a
failed export. I/O failures during a multi-file bake are not transactional; the
existing staged project archive save remains the durable preservation mechanism.

## Verification

```bash
bash run_core_baseline.sh
bash run_editor_persistence.sh
bash run_editor_authoring.sh
./gradlew :phantasia-core:test :phantasia-j2d:compileJava :phantasia-editor:compileJava
```

The new authoring test exercises actual Swing form models on the event thread in
headless mode. It covers all eleven feature types, metadata, type switching,
invalid input, edited treasure tables, inline stock preservation, exact undo/redo,
built-in content validation, reference failures, blocked arrivals, export from a
clean reopened project and invalid export preserving prior output/dirty state.

Local Java 17 compatibility-copy run: **136 authoring checks and 448 persistence
checks passed**. Only unrelated Java 21 Math.clamp/List.getFirst calls were adapted
in the temporary copy and switch preview enabled. Consult the Java 21 core CI run
for native build results. CI now runs both editor smoke suites. Visual inspection
of the actual dialogs and game interactions remains pending.

The persistence smoke fixture intentionally tests out-of-vocabulary tile values
and arbitrary payload transports; it now exports those edge cases through the
core codecs. Valid editor export after save/reopen is covered by the new authoring
suite. This keeps wire preservation tests separate from authoring constraints.

Next: explicit map entrances and exits, NPC placement editing and the shared local
traversal path, followed by the connected diagnostic adventure described in the
build plan.
