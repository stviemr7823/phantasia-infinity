# Windowed diagnostic mockup

A resizable 1280×720 window now runs the common authored-map screen with solid
color tiles, named NPC placeholders and a player-facing arrow. This is non-canon
test content. It creates a fresh test party and never calls SaveManager.

## Launch (JDK 21)

```bash
bash run_mockup.sh
```

Or use `./gradlew :phantasia-j2d:runDiagnostic`.

Enter starts the settlement. Arrows/WASD move and bump into NPCs. Enter advances
dialogue (first press reveals text if it is still appearing). Esc pauses. R in
the pause/entry menu resets the party, flags, gold and overlay stack; Q quits.
The window close button also quits.

Input, fixed updates and rendering run on the AWT event thread at a capped 30 FPS.
The normal launcher and its save behavior remain unchanged.

## Test route

| Map ID | Area | Contents / connections |
| --- | --- | --- |
| 1000 | Test settlement | West exit to menu; east dungeon; north castle; guide, merchant and trainer |
| 1001 | Test dungeon / room 1 | Settlement return; second room; chest/trap tests |
| 1002 | Test dungeon / room 2 | Return to room 1; placeholder notice |
| 1003 | Test castle courtyard | Settlement return; locked gate; conditional guard; hall |
| 1004 | Test castle hall | Courtyard return; conditional NPC |

1. Test the settlement's dialogue, merchant and trainer screens.
2. Travel east through both dungeon rooms and return west.
3. Try the castle gate before speaking to Test guide: it blocks passage.
4. Return and speak to Test guide to grant diagnostic gate permission.
5. Go north, open the gate, enter the hall and return through the courtyard.
6. Leave via the settlement's west exit, or pause and reset.

The five maps, five NPC definitions and one shop are validated, exported to
temporary interiors/NPC/shop files, then reloaded with the production codecs
before launch. Temporary fixture files are removed on exit.

Chest rewards and trap effects are active; encounters and native-v5 campaign
progression remain pending. The trainer uses the existing service overlay. No combat or
training rules are changed, and these test names do not fill undecided story
content.

## Edited content and previews

Export the fixture into a new directory, or load your editor's baked content:

```bash
bash run_mockup.sh --export-dir /path/to/new/diagnostic-content
bash run_mockup.sh --content-dir /path/to/baked-data --map 1000
bash run_mockup.sh --check
bash run_mockup.sh --render-dir /path/to/new/previews
```

Custom content must include interiors.dat, npcs.dat and shops.dat, and the chosen
map needs a valid default entrance. Missing/invalid content fails explicitly.
The diagnostic world anchor is (0,0); custom explicit world exits must use that
anchor. The session remains a fresh test party. Export and image output refuse
existing directories rather than replacing user content.

Preview mode writes menu.png and one PNG per map at 1920×1080, preserving actual
arrival positions and fog settings.

## Verification

```bash
bash run_diagnostic_tests.sh
bash run_local_traversal.sh
```

The new suite checks the exported connected route, gate before/after permission,
actual dialogue flag changes, conditional NPCs, actual shop/guild overlays,
return to the entry menu, reset/quit, every map render, missing custom files,
existing-export protection and PNG output. Local compatibility-copy runs passed
44 mockup checks and the prior 71 traversal checks. The local host has Java 17;
only unrelated Math.clamp/List.getFirst calls are adapted in temporary copies.

CI runs all earlier suites and the unmodified Java 21 build. It also runs
`xvfb-run -a bash run_mockup.sh --window-smoke` with a 30-second process timeout:
this creates the actual window, renders thirty frames and closes automatically.
That is a window lifecycle check, not a human play-through. Target-machine sizing,
input feel and manual gameplay review remain pending.

Next: encounters and native-v5 campaign/progression integration.

Treasure/trap behavior and retry/persistence limits are detailed in [LOCAL_TREASURE_TRAPS.md](LOCAL_TREASURE_TRAPS.md).
