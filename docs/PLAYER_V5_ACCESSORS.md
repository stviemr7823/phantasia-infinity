# Increment 3 — Typed v5 player state (additive)

**Purpose:** Introduce the typed player state access layer atop `DataCoreV5`, before migrating the existing `PlayerCharacter` or save format. It is independent of any renderer and uses the collision-free 64-byte schema adopted in Increment 2.

## Interface

- `PlayerStateV5.create(name, recordId)`, `fromBytes(byte[])`, `fromRecord(DataCoreV5)` and `toBytes()`.
- Three attributes only: `STRENGTH`, `AGILITY`, `MIND`.
- Explicit IDs, level, status flags and portrait; 16-bit HP/MP; 32-bit unsigned experience (`long` in Java).
- Four equipment slots using the existing `EquipSlot` enum; ten inventory item IDs.
- Out-of-range field values, invalid slot indices and wrong entity kinds are rejected.
- Defensive copies at import/export; the underlying record is not exposed for outside mutation.
- Reserved bytes remain zero; the record format and offset map continue to be owned by `DataLayoutV5`.

## Deliberate exclusions

- No legacy v4 record conversion or old-save migration (decision 7C).
- No changes to `PlayerCharacter`, `Stat`, `CombatManager`, `ExperienceTable`, `SaveManager`, or existing gameplay formulas.
- No decisions yet about monster abilities, NPC behavior, combat moves, specific races/classes, social standing, curses, or limb injuries.
- This class is a **storage-facing typed state**, not a full gameplay entity. It does not decide how to calculate damage, whether a character can act, or whether HP may exceed maximum HP during construction. Those rules belong to a later increment.

## Migration audit (the next significant step)

The v4 runtime still uses six statistics: `STRENGTH`, `INTELLIGENCE`, `DEXTERITY`, `CONSTITUTION`, `CHARISMA`, `LUCK`. Existing formula/code references to removed or renamed stats occur in `AttackResolver`, `CombatConsoleRunner`, `ExperienceTable`, `FormulaEngine`, and `PlayerCharacter`. The old `PlayerCharacter` also has limb-status and undead sentinel logic. Those semantic rules need explicit decisions or compatibility wrappers before switching the runtime to v5; changing the field offsets alone would be unsafe.

## Verification

1. `bash run_core_baseline.sh` (legacy v4, headless check, v5 schema, and v5 player accessors).
2. `./gradlew :phantasia-core:test :phantasia-j2d:compileJava :phantasia-editor:compileJava`
3. `git diff --check`

The old v4 game and old v4 saves remain unchanged. This increment does **not** claim that the game is running on v5 yet.
