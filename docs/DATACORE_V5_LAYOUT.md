# DataCore v5 — Binary layout contract

**Status:** Candidate layout implemented alongside legacy v4, pending runtime adoption.  
**Record size:** 64 bytes, big-endian unsigned integers.  
**Scope:** Storage schema only; no save format change, old-save migration, or combat/character rebalance.

## Common header (all records)

| Bytes | Size | Field | Meaning |
|---|---:|---|---|
| `00–0E` | 15 | `NAME` | Printable ASCII, space-padded; longer/localized names may be external later |
| `0F` | 1 | `KIND` | `1=PLAYER`, `2=MONSTER`, `3=SPELL`; other tags rejected |
| `10–13` | 4 | `RECORD_ID` | Unsigned 32-bit stable ID, `0` means unassigned in-memory record |
| `14–15` | 2 | `SHARED_FLAGS` | Unsigned 16-bit; no cross-type semantics assigned yet |
| `16–17` | 2 | Reserved | Must remain zero in v5 |

All domain payloads occupy `18–3F`, exactly 40 bytes. Reserved bytes are written as zero, and v5 record readers reject nonzero reserved bytes. ID assignment and per-domain uniqueness validation belong to the future storage service, not the primitive record.

## PLAYER payload

| Bytes | Size | Field |
|---|---:|---|
| `18` | 1 | Strength |
| `19` | 1 | Agility |
| `1A` | 1 | Mind |
| `1B` | 1 | Job/class ID (current encoding retained pending audit) |
| `1C` | 1 | Race/species ID |
| `1D` | 1 | Level |
| `1E` | 1 | Status flags (not limb packing) |
| `1F` | 1 | Portrait ID |
| `20–21` | 2 | Current HP |
| `22–23` | 2 | Maximum HP |
| `24–25` | 2 | Current MP |
| `26–27` | 2 | Maximum MP |
| `28–2B` | 4 | Experience points |
| `2C` | 1 | Weapon item ID |
| `2D` | 1 | Shield item ID |
| `2E` | 1 | Armor item ID |
| `2F` | 1 | Bow item ID |
| `30–39` | 10 | Inventory item IDs (10 × u8) |
| `3A–3F` | 6 | Reserved; must be zero |

Only **three base statistics** are stored. Job/race/level, HP/MP, and experience are not additional base stats. Six bytes remain unused. This proposal intentionally omits the old limb status, charisma/luck/constitution values, social standing, resonance, and trait flags. If a removed concept proves necessary, we should argue for it explicitly rather than silently reintroducing it.

## MONSTER payload — conservative placeholder

| Bytes | Size | Field |
|---|---:|---|
| `18` | 1 | Max spawn count |
| `19` | 1 | Flags |
| `1A–1B` | 2 | HP |
| `1C–1D` | 2 | Treasure |
| `1E–1F` | 2 | Experience award |
| `20` | 1 | First item ID |
| `21` | 1 | Second item ID |
| `22–3F` | 30 | Reserved |

## SPELL payload — conservative placeholder

| Bytes | Size | Field |
|---|---:|---|
| `18` | 1 | MP cost |
| `19` | 1 | Power |
| `1A` | 1 | Target |
| `1B` | 1 | Spell type |
| `1C` | 1 | Minimum level |
| `1D` | 1 | Effect type |
| `1E` | 1 | Spell ID |
| `1F–3F` | 33 | Reserved |

These conservative schemas do not settle enemy types, spell design, NPCs, or combat mechanics. They only preserve known minimum records without field collision. NPC records and other future entity types must receive explicit kind values and their own occupancy tests when designed.

## Safety and invariants

- Every byte is owned by precisely one field *within each kind*. Shared fields occur at the same offsets across kinds; payload offsets may be reused across kinds.
- Numeric readers and writers reject a wrong kind, incorrect field width, out-of-range values, and out-of-range array indices.
- `DataCoreV5` copies data on import/export. The existing v4 `DataCore` mutable array remains unchanged for now.
- New records are zero-initialized except for their kind byte. Names enforce 15 printable ASCII characters or fewer. Numbers are big-endian unsigned 8/16/32-bit.
- The old `PC_XP`/weapon overlap and limb/MP overlap are absent by construction. Neither v4 save data nor production game models have been migrated.

## Gates before runtime adoption

1. Current v4 core smoke tests still pass.
2. New v5 standalone smoke test and JUnit tests pass.
3. No UI dependencies re-enter `phantasia-core`.
4. Next increment audits `Stat`, `PlayerCharacter`, `Monster`, `Spell`, data bakers, gameplay formulas and saves before switching them to v5.
5. Save format v5 and strict town-only saving are separate follow-up increments. Historical v1–v4 saves are not imported into the new format yet.
