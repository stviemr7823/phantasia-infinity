# Treasure and trap effects in authored areas

LocalFeatureEffects now applies the existing LootTable and TrapData payloads to
GameSession through the current inventory, gold and HP facades. The common 2D
screen displays results and live party HP, gold and pack usage. Combat and
training rules are unchanged; native-v5 campaign ownership remains separate.

## Treasure

An authored chest requires a completion flag. Export validation reports a missing
flag, contradictory activation/completion flags, invalid loot or a blocked trigger
tile. The runtime also refuses unflagged treasure rather than making it farmable.

One reward roll is retained per map ID and feature position in the session. If
there is insufficient pack space or the gold total would overflow, no items,
gold or completion flags are changed. Retrying keeps the original roll. The cache
belongs to GameSession, so leaving and re-entering a map in the same session does
not reroll it. There is no partial collection or automatic discarding of items.

When capacity permits, items fill free slots in party order using the existing
eight-slot capacity. Packs belonging to dead members remain ordinary pack storage;
the party must still have an active member to interact. Repeated references to
the same character cannot double-count available capacity. Keys and equipment
rules are not changed.

Payload/capacity/gold checks precede mutation. Inventory and completion state are
committed before the gold notification, so reentrant event listeners cannot claim
the chest again. Completed gold/items/flags survive the existing save format.
Notification callbacks may still propagate their own exceptions after the model
commit; the underlying event bus is unchanged.

Pending, uncollected rolls are runtime-only and are not yet serialized by the
legacy save format. Their retry stability therefore covers the current session,
not quitting and reloading an uncollected chest. Campaign snapshots must preserve
this cache before claiming stable pending rewards across restarts. The diagnostic
launcher itself still never loads or writes normal saves.

LootTable gains a RandomGenerator overload for deterministic tests and validates
its payload before rolling. Inclusive gold ranges use long bounds to avoid
integer overflow. Weighted draws retain their existing semantics. The processing
budget is at most 1,000,000 random draws per table; exports reject larger values.

## Traps

A positive detectSkill uses the authored DEX threshold: any living member meeting
it detects and avoids the trap. Zero means undetectable. An optional completion
flag is recorded after either successful avoidance or damage, resolving a one-shot
trap. With no completion flag, the authored trap can trigger on a later visit to
its tile. Idle frames do not retrigger it.

Undetected traps roll within the authored inclusive damage range for each living
party member. HP is clamped at zero; dead members are unchanged and cannot supply
detection skill. Legacy undead stat-lock behavior is retained rather than
redefined. A defeated party cannot move, claim more loot or trigger further
interactions. Pause remains available; the diagnostic pause menu can reset or quit.

Chest/trap interactions require the party to stand on the actual feature tile.
Generic interaction completion cannot bypass their effect handlers. Authored map
definitions, tiles and placements are not modified.

## Verification

```bash
bash run_local_effects.sh
bash run_diagnostic_tests.sh
bash run_local_traversal.sh
```

The new suite checks weighted boundaries, inclusive ranges, multi-pack transfer,
full-pack/no-partial-grant retries, cross-visit cached rolls, remote interaction
rejection, reentrant notifications, repeated claims, gold overflow, invalid
payloads, saved completion/rewards, detection, repeated/one-shot traps, overkill,
party defeat, actual screen effects and reset. Earlier editor/traversal/mockup
suites remain in CI, along with the virtual-display window smoke test and full
Java 21 build. Manual target-machine gameplay remains pending.

Next: authored encounters and reward return flow, then native-v5 progression and
campaign persistence.
