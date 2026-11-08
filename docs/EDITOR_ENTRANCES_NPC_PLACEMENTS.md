# Named entrances and NPC placements

This increment continues draft PR #1 on `work/editor-persistence`.

## Authored arrivals

InteriorMap now stores named MapEntrance records: stable name, arrival position
and facing, plus a selected default entrance. A STAIRS_LINK may carry new
MapLinkData (exact destination map ID plus entrance name) or retain its previous
StairsData coordinate payload. Named links support destination map ID 0 directly;
only legacy coordinate links retain the old 0 = this-map convention.

Moving a named entrance changes its coordinates without editing incoming links.
Renaming or deleting one leaves broken references visible for validation and
repair. Changing a default, moving an arrival or removing an entrance is one
undoable command, including restoration of list order and the prior default.
Deleting the default does not select another entrance silently.

The registry's spawn lookup now reads the selected default instead of returning
(9,13) for every map. resolveLink returns the destination map and full arrival
record without mutating a session. Missing names, missing defaults and blocked
arrivals fail explicitly. This is the data/resolution contract for shared local
traversal; the existing game screen does not yet dispatch these named links or
apply arrival facing. That runtime integration is the next increment.

## Editor controls

- Right-click a map tile to choose feature, NPC placement or entrance editing.
- NPC and Entrance toolbar buttons use the last clicked tile.
- Double-click sidebar entries to edit an exact placement or entrance.
- Entrance markers appear as green E labels; E* is the default.
- New stairs forms use named entrances by default; existing coordinate payloads
  retain their mode until explicitly changed.

NPC placements select an existing reusable definition and edit position, facing,
appearance flag and disappearance flag. Missing definition IDs remain visible;
they are not replaced with an arbitrary NPC. Placement/movement/removal is
undoable and does not modify the NPC's shared identity, dialogue or services.
The sidebar and map refresh after edits, undo and redo.

## Preservation and compatibility

The interior writer now emits INTR **version 3**. It retains the v2 record fields,
adds the `entrance-link` payload tag and appends per-map default entrance ID and
entrance records after NPC placements. Optional entrance position and facing are
representable so incomplete drafts remain preservable. NPC/shop file versions
are unchanged. Readers accept interior versions 1, 2 and 3. Older binaries cannot
read version 3; update the editor and runtime together.

During v1/v2 import, and when creating a new map, addSuggestedDefault proposes an
editable `entry` entrance. It keeps (9,13) when that tile is clear; otherwise it
uses the first clear passable tile. It leaves a map without an entrance if no
safe tile exists. Built-in towns receive the same explicit records. V3 drafts
are never silently repaired on load. New blank wall-filled dungeons therefore
need a floor tile and an entrance before export.

## Validation

Export now also requires a resolvable default entrance and checks entrance names,
duplicates, positions, facing and named-link references. Arrivals cannot share
NPC tiles, including conditional NPC placements, or trigger tiles such as stairs,
exits or traps. NPC appearance/disappearance using the same flag is rejected.
Project archive saving remains permissive for unfinished drafts.

These checks do not prove return-route reachability or evaluate quest conditions.
Shared traversal, campaign state, entrance selection from overworld markers and
actual game interactions remain pending. NPC definitions still use the existing
NPC editor; this increment adds their placement controls.

## Verification

```bash
bash run_core_baseline.sh
bash run_editor_persistence.sh
bash run_editor_authoring.sh
bash run_editor_placements.sh
./gradlew :phantasia-core:test :phantasia-j2d:compileJava :phantasia-editor:compileJava
```

The new suite exercises real Swing form models on the event thread in headless
mode, placement/entrance undo and redo, default changes, link resolution to map 0,
links following moved entrances, renamed targets, blocked arrivals, archive and
runtime round trips, independent v1/v2 migration fixtures and v3 draft behavior.
The existing authoring fixtures now explicitly author their default entrances.

Local checks use the same Java 17 compatibility-copy method as earlier increments:
unrelated Math.clamp/List.getFirst calls are adapted only in a temporary copy.
Consult the Java 21 CI run for native results. Visual inspection of the actual
editor and gameplay remains pending.
