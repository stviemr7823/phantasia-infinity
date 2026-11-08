# Native spell planning

The native party diagnostic now exposes the existing damage, healing, sleep and
awakening resolver through reviewed spell orders. One cast replaces the member's
one action for the round. Ordinary resistance, power and initiative rules remain
in the existing native resolver.

## Diagnostic controls

C at the entry menu starts a fresh non-canon Wizard/Priest party, replacing the
native test owner and visit. It preserves the separate legacy comparison party.
It is disabled while a native battle remains active. N then explores authored
areas with that owner; V opens standalone preparation/battles. R retains its
existing fresh-Fighter reset behavior.

During combat, S cycles this member's supplied spell choices. T overrides a
single-enemy target; Y overrides a single-ally target. Group spells select the
whole group and reject individual overrides. The row shows name, MP cost and
single/group target. Enter confirms the full party plan. Spell and item orders
are cleared after a resolved round, requiring fresh selection before another
round can run. Attack/Defend/Run/readied commands retain their prior behavior.

Single-enemy casts automatically select the first living enemy. Healing prefers
an injured living ally, awakening prefers a sleeping living ally; both fall back
to the first living ally. Ordinary healing cannot resurrect. Full-health and
already-awake living targets remain legal, matching existing spell semantics;
these casts still cost MP. Damage/status resistance still pays for the cast.

## Permissions and ownership

NativeSpellBook supplies positive unique IDs, explicit permitted jobs and the
spell record's minimum level. Definitions are detached at admission, enum bytes
are checked before their legacy getters can clamp them, unsupported effects and
mismatched target shapes are rejected. Public record access returns copies so
source/tool edits cannot change a selected spell's cost or target shape.

Battle.selectSpell checks membership, job/level permissions, living caster,
affordable MP and the target shape before replacing the selection. Confirmation
rechecks MP and stale/dead targets for the complete party before any action runs.
No MP is reserved or deducted by selection. The resolver pays once on the caster's
initiative turn and rechecks living targets/resources. A dead/sleeping caster, no
surviving selected target, or earlier victory/escape prevents later spell costs.
A group cast pays once for all its targets. The low-level native resolver's
existing sparse-order and rank-selector APIs remain unchanged; this campaign UI
uses complete-group casts without individual rank selectors.

Permissions are caller-supplied rules, not new learned-spell character state.
The diagnostic uses four Wizard and four Priest choices (single/group variants
of each supported effect), all level one, 3 MP and power 12 where applicable.
Fighters, Rangers, Monks and Thieves receive no inferred permissions. These are
explicit test values, not a final spell list, hybrid rule, teaching system or
balance table. Guild-level changes satisfy a supplied minimum-level rule, but do
not invent a separate spell-learning event.

Existing checkpoints already preserve job, level, HP/MP and status. The diagnostic
recreates its fixed book after loading; the book is external rules, not serialized
learned progress. No checkpoint version change is needed. A future authored or
learned spell system needs its own content identity and persistence decisions.

## Verification

```bash
bash run_native_spells.sh
```

82 checks cover definition detachment, malformed enums, permissions, level
requirements and guild advancement, target selection/overrides, stale plans and
MP, group cost/restoration, native sleep/awakening, interrupted turns, earlier
escape, actual menu and screen controls, choice cycling, fresh-round selection,
rendering and a fresh-JVM checkpoint reload. Prior native/map/editor suites run
alongside it in Java 21 CI, with core Gradle tests, j2d/editor compilation and the
Xvfb window smoke. Local compatibility runs use temporary Java 17 substitutions;
CI compiles the actual Java 21 source. Human desktop playthrough remains pending.

Next: native inventory/equipment preparation, then the remaining authored-content
and party-progression work. Final magic progression and presentation remain open.
