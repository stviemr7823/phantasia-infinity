# Battles from authored local areas

The common LocalMapScreen now carries a retained visit through the existing
legacy encounter, planning, execution and result screens. Victory, escape and
defeat return to the same map, position, facing, discovered fog and world-return
anchor. The existing generated-dungeon path is outside this integration.

## Pacing and ownership

LocalEncounterFlow counts ordinary successful MOVED results once each. Walls,
idle updates, menus, feature interactions and map links do not advance the count.
Each map has its own counter within the visit; disabled encounters leave peaceful
areas safe. A positive authored encounter interval is required. During battle,
local movement and interactions are suspended until the result is acknowledged.
A stale result cannot release a newer battle.

The normal local screen uses EncounterFactory for enemy composition. The diagnostic
fixture instead supplies one named test creature and enables battles every six
ordinary moves in its two dungeon rooms. There is no new authored encounter table
or monster editor in this increment.

## Rewards and return

One CombatRewards receipt follows every battle round and screen transition.
Victory requires defeated enemies and a surviving active party. Re-entering the
victory screen cannot duplicate XP or gold. XP uses the existing survivor sharing
and cap rules; it does not level characters automatically. Guild training remains
the place for level-ups. Escape and defeat grant no victory rewards.

If adding gold would overflow the party purse, XP is still awarded once and gold
is queued in GameSession. Returning to a local visit or making another ordinary
move retries queued gold. The claim is marked before ledger notifications so
reentrant callbacks cannot collect it twice. Event callback exceptions may still
propagate after commit, as with existing ledger events.

Pending gold is runtime-only. Existing saves preserve collected rewards, but do
not serialize deferred combat receipts or an in-progress local visit. Diagnostic
reset starts a new session; it intentionally discards that session's pending state.
After local defeat the retained area blocks movement; Esc still permits diagnostic
reset or quit. Overworld battles retain their existing overworld return/quit routes.

## Scope

These screens still operate on the legacy party model. Native-v5 party ownership,
learned/readied techniques and final campaign progression are separate integration
work. The diagnostic supplies no spells and disables Cast; this does not remove
spells from a normal game with a populated spell bank. The fixture is non-canon.

## Verification

Run with JDK 21:

```bash
bash run_local_encounters.sh
```

The 66 checks cover pacing, duplicate movement notifications, peaceful maps,
suspension, early/repeated victory, escape/defeat, stale continuations, reward
sharing without automatic level-up, deferred gold, reentrant notifications and
actual screen transitions retaining position, facing, fog and world anchor.
CI also runs prior suites, the full Gradle core tests and j2d/editor compilation,
and a real thirty-frame window lifecycle under Xvfb. Manual target-machine
play-through remains pending.
