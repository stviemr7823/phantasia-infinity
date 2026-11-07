# Shared traversal for authored local maps

This increment continues draft PR #1 on `work/editor-persistence`.

## Behavior

LocalMapTraversal supplies the same movement rules to towns, castle rooms and
individual authored dungeon areas. Local grids use Y-down coordinates explicitly;
Direction's overworld helpers are not reused for local north/south movement.

Named links resolve the destination and arrival facing before switching maps.
Coordinate StairsData remains supported for floor 0; separate authored map IDs
represent separate floors. Failed or gated links leave the current map, position
and completion flags unchanged. Arriving never triggers the arrival tile or
repeats a held movement key. Named links can target map ID 0 exactly.

Local position, facing and per-map exploration belong to the current visit.
Traversal does not rewrite map tiles, placements, entrance definitions or the
InteriorMap exploration bitset. Quest completion/unlock flags remain session
state. GameSession keeps its overworld coordinates throughout the visit. Exits
return to the original world entry anchor or their explicitly authored world
position. The world screen supplies bounds checking for those return positions.

Movement checks all visible NPC placements before terrain movement; a hidden NPC
cannot mask a visible NPC on the same tile. NPCs on passable terrain now block
walking through them and dispatch interactions. Counter bump searches stop at
walls and only start on interactive counter terrain. Locked doors respect their
key/flag rules, record authored unlock flags and remain passable after a recorded
unlock without changing the authored terrain. Keys are not consumed because the
current lock schema has no key-consumption rule.

Sign/altar completion is recorded after opening their text interaction. Generic
feature interaction does not itself grant rewards or consume completion flags;
reward/service handlers must finish the real effect first. Links and exits record
completion only after successful traversal.

## Runtime wiring

LOCAL_MAP and LocalMapScreen provide the common solid-color 2D presentation.
The screen renders terrain, feature markers, NPC placeholders, player-facing
indicator, fog and explored memory. It reuses existing dialogue, shop and service
NPC overlays. It retains the same traversal object through overlays and local
links, so visits and world-return anchors are not reinitialized.

World settlement entry now opens the common screen. A dungeon marker opens an
authored DUNGEON map when its ID matches; otherwise it retains the existing
procedurally generated dungeon path. Castles use authored TOWN maps or connected
areas; no new castle gameplay category is invented. Missing settlement maps are
reported rather than replaced with an unrelated demonstration shop.

An existing interiors.dat file is authoritative, including an empty registry.
Corrupt existing interior data now stops startup with an explicit load error.
Built-in interiors are used only when that file is absent. NPC/shop registry
fallback behavior remains separate and unchanged.

## Scope and remaining work

This is traversal integration, not a finished campaign:

- Chest rewards, trap effects, random encounter dispatch and custom typed inn /
  guild / bank counter settings are still pending in the common screen. Their
  placeholders report that status; they do not silently grant rewards or set
  completion flags. Service NPC overlays retain their existing rules.
- Native-v5 combat, guild lessons and campaign ownership remain separate work.
- Visit position, facing and exploration are runtime-only. Exiting the game while
  inside an authored area still saves the overworld anchor; resume-inside saves
  need a dedicated campaign snapshot increment.
- World markers still use their existing ID conventions. Selecting a particular
  named arrival from an overworld marker and checking return-route reachability
  are not implemented here.
- The normal launcher remains fullscreen. A dedicated windowed diagnostic
  launcher and connected non-canon test adventure are the next useful slice.
- No interactive window was opened during verification.

## Verification

```bash
bash run_core_baseline.sh
bash run_editor_persistence.sh
bash run_editor_authoring.sh
bash run_editor_placements.sh
bash run_local_traversal.sh
./gradlew :phantasia-core:test :phantasia-j2d:compileJava :phantasia-editor:compileJava
```

The new dependency-free suite compiles all core and 2D runtime sources. It walks
three diagnostic areas before and after export/load, exercises gates, locks,
failed destinations, world return checks, conditional NPCs and fog memory, then
drives the actual common screen with headless key events and ScreenManager
transitions. It renders into a BufferedImage and checks dialogue/overlay routing
without constructing a window. These areas exist only in the test and introduce
no story canon.

Local compatibility-copy run: **71 traversal checks passed**. The local host has
Java 17; unrelated Math.clamp/List.getFirst calls are adapted only in temporary
copies with switch preview enabled. Java 21 CI runs the unmodified sources and
all earlier suites; consult the current PR run for the native result.
