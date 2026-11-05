# Core v4 → v5 Migration Matrix (Increment C4)

**Audited commit:** `00e3cf3` on `work/april-source-reconciliation`  
**Scope:** read-only audit. No production code changes in this increment.  
**Baseline at audit:** `run_core_baseline.sh` passes: core 17, headless check, v5 layout 26, v5 player accessors 22. The Gradle tests were not run in the audit environment.

## 1. Live v4 defects

Items 1–4 were reproduced by running the code. Items 5–7 come from reading it.

| # | Defect | Evidence | Effect |
|---|---|---|---|
| 1 | The `PlayerCharacter` constructor writes `BODY_STATUS` (`0x20`), which is the high byte of XP | XP 7 → 263 on construction | Every load forces XP into the 256–511 range |
| 2 | XP's low byte is the weapon slot (`0x21`) | `setStat(XP, 5)` → weapon ID 5; `0x1234` → weapon ID 52 | XP changes corrupt the weapon, and the weapon changes XP |
| 3 | Limb status (`0x1D–0x1E`) overlaps MP and max MP (`0x1C–0x1F`) | Limb write: max MP 772 → 3844 | Corrupts magic values, though nothing calls the limb API today |
| 4 | `getBaseStat` calls itself | `StackOverflowError` | No callers |
| 5 | `equip(slot, id)` writes only the in-memory map, while `getEquipped` reads the bytes | `PlayerCharacter:244–275,303` | Equipped items don't persist. `Inventory.equip` also removes the item from the pack, and the replaced item can't be seen, so items are lost |
| 6 | `equip(ItemDefinition, slot)` does nothing | `PlayerCharacter:367` | No effect |
| 7 | `GlobalLedger.saveCharacter` seeks but writes nothing | `GlobalLedger:38–46` | Roster saves don't save |

**Equipment has no gameplay effect today.** `getAttackStrength`, `getArmorClass`, `getWeaponAttackBonus` and `getArmorDefenseBonus` have no callers in any module, and player damage and hit rolls don't use them.

## 2. Stat mapping

Counts are `Stat.X` references. Frontends use only vitals, level and XP, so retiring attributes doesn't touch them.

| v4 stat | Core | J2D / JME / libGDX | Live use | v5 target | Status |
|---|---:|---|---|---|---|
| `STRENGTH` | 4 | 0 / 0 / 0 | Player damage | `Attribute.STRENGTH` | **Direct map** |
| `DEXTERITY` | 5 | 0 / 0 / 0 | Hit, defense, escape | `Attribute.AGILITY` | **Direct map**; meaning matches |
| `INTELLIGENCE` | 4 | 0 / 0 / 0 | Spell variance, spell save | `Attribute.MIND` | **Direct map**; meaning matches |
| `CONSTITUTION` | 0 | 0 / 0 / 0 | None | none | **Remove**; nothing depends on it |
| `LUCK` | 2 | 0 / 0 / 0 | `rollEncounter` (no callers) and a console print | none | **Remove**; no live effect |
| `CHARISMA` | 1 | 0 / 0 / 0 | Training cost through `Race.trainingCost` | none | **Decision D1** |
| `LEVEL` | 11 | 3 / 0 / 0 | Progression | `level()` | Direct map |
| `HP` / `MAX_HP` | 10 / 7 | 26 / 7 / 9 | Vitals | `hp()` / `maxHp()` | Direct map, see §5 |
| `MAGIC_POWER` / `MAX_MAGIC` | 9 / 3 | 24 / 11 / 9 | Vitals | `mp()` / `maxMp()` | Direct map, see §5 |
| `XP` | 5 | 1 / 0 / 0 | Progression | `experience()` (`long`) | Widen; `ExperienceTable` uses `int` |

## 3. Other player fields

| v4 field | Live use | v5 target | Status |
|---|---|---|---|
| Name, job, level, portrait | Live | Same names | Direct. Store `Job.getBitValue()` as the job ID. Portrait range changes from a 0–31 clamp to 0–255 |
| Race byte | Display, training multiplier | `raceId()` | Direct once the undead value is gone |
| `PC_RACE_UNDEAD`, stat lock, `setUndead` | `JudgmentEngine` UNDEAD fate, guild and XP checks | none | **Decision D2** |
| `BODY_STATUS` on players | Written only, and this causes defect 1 | none | **Remove**; aliveness comes from HP |
| Limbs, `canPerform` | No callers | none | **Remove** from players |
| `PC_SOCIAL_CLASS` | Guild gold bonus; nothing ever sets it, so always `PEASANT` (25 gp per level) | none | **Decision D3** |
| Equipment, four slots | Broken, defect 5 | `equipment(slot)` | Store in the record only; see **Decision D4** |
| Inventory, 8 slots | Live | `inventory(0–9)`, 10 slots | Direct; `Inventory.MAX_ITEMS` becomes 10 |
| Traits, resonance, extended inventory | Baseline tests only | none | **Remove**, including those smoke checks |

## 4. Files affected

**Direct v4 player access, core:**
- `PlayerCharacter` and `Stat`: the gateway.
- `EncounterFactory:136–146`: writes bytes directly, and must use the API.
- `SaveManager:99,166–170` and `GlobalLedger`: raw records.
- `Main:13`: scratch entry point.
- `Inventory:51`: slot count.
- `Entity.getDataCore()`: forces `DataCore` on every entity; move it to `Monster` and `Spell`.

**Formula consumers using `Stat`:** `FormulaEngine`, `AttackResolver`, `ExperienceTable`, `CombatAI`, `JudgmentEngine`, `GuildService`, `ItemDefinition`, `CombatConsoleRunner`.

**Monster and spell records (separate, later slice):**
- `Monster`, `Spell`, `EncounterFactory:253–259`, `MonsterFactory`, `SpellFactory`, `LootManager`, `MonsterPacker`, `SpellBaker`.
- Seven `phantasia-editor` files use these offsets directly.
- Baked data such as `monsters.dat`.
- v5 moves every monster and spell field, because `0x0F` is now the kind byte. `MON_HP` also widens to 16 bits.
- `Monster` depends on `BODY_STATUS` and limb bytes (24+) that have no v5 field.

**Baseline tests:** `CoreBaselineSmokeTest` pins v4 player and save behavior and must change in the same slice as the storage swap.

## 5. Hazards

1. **Save ordering.** The handoff puts runtime migration before save v5, but `SaveManager` writes raw player bytes, so a v5-backed `PlayerCharacter` can't save in v4 format. Proposed fix: swap storage and bump the save version in one slice, keeping older versions as an explicit unsupported error. Atomic writes and full validation stay in the later save increment.
2. **Clamping becomes rejection.** v4 setters clamp silently, while `PlayerStateV5` throws on out-of-range values. `PlayerCharacter` must keep clamping at its boundary, or `MAX_HP` growth and XP awards could start throwing.
3. **Determinism.** `Dice` uses an unseeded shared `Random`, and 13 core files create or call random sources directly, including `Dice`. Deterministic combat tests need an injectable seed, which is its own increment.
4. **Monsters and spells stay on v4 for a while.** `DataCore` and `DataLayout` must remain until that slice, so both layouts coexist on purpose.

## 6. Proposed slices

Each is one commit with the baseline green.

| Slice | Scope | Behavior change |
|---|---|---|
| **C5a Seal** | All player byte access goes through `PlayerCharacter`; `EncounterFactory` uses the API; `Entity.getDataCore` moves down | None |
| **C5b Retire** | Remove limbs from players, `getBaseStat`, traits, resonance, extended inventory, `CONSTITUTION`, `LUCK`, `rollEncounter` | None live |
| **C5c Decisions** | Apply D1–D3 | As decided |
| **C5d Equipment** | Equipment persists; apply D4 | Fixes item loss |
| **C5e Swap** | Back `PlayerCharacter` with `PlayerStateV5`, rename to three attributes, minimal save v5, update baseline | Fixes defects 1–3 |
| **C6 Monsters and spells** | Migrate the records, tools, editor and baked data | Data format |

## 7. Open decisions

Recommendations are proposals until decided.

- **D1, Charisma in training cost.** Recommended: remove the term from the formula and use a fixed factor, because paid town training stays and no other input is needed. The factor sets the gold balance.
- **D2, Undead fate.** Recommended: remove the curse and the UNDEAD outcome. Total party defeat consequences stay open for the story design.
- **D3, Social class.** Recommended: remove it, using one fixed guild gold bonus, or none.
- **D4, Equipment in combat.** Recommended: first make it persist with no combat effect, so the fix stays bounded, then wire gear into formulas in its own increment.
