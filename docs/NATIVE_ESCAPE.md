# Native battle escape

In native battle planning, **F** assigns Run to the selected member. Enter still
confirms the complete party plan. Run is a basic command: it does not need a
learned/readied slot, target, spell, rank or MP. The selection message shows the
acting member's chance and explains that success withdraws the party.

## Turn and outcome rules

The runner attempts escape on their ordinary initiative turn. It consumes that
member's one action. The existing native formula is unchanged:

`chance = clamp(30 + Agility, 5, 85)` percent.

The public API still uses ordinary dice. Deterministic predicates exist only at
the package-private test seams, not as a force-escape gameplay option.

A failed attempt emits FleeAttempt(false) and leaves combat ongoing. That runner
does not also attack or cast; the remaining planned actors continue. If several
members choose Run, each may attempt it once on their own turn until success or
another terminal outcome. An order for a sleeping or dead member does not roll.
Faster enemies can damage or kill the runner before their turn; that damage is
not undone by a subsequent successful retreat. A prior victory/defeat prevents
later attempts.

On success, FleeAttempt(true) ends combat as ESCAPED. Remaining attacks, spells,
monster turns and escape rolls stop; temporary stance cleanup can still emit its
closing event. The party withdraws intact. No player is removed from the native
roster, including dead members who were already in it; no resurrection occurs.

Escape grants no encounter XP, treasure or new pending gold. The result receipt
is idempotent. Earlier, already-earned pending gold remains part of the campaign
and may still be collected by its existing retry mechanism when capacity permits.
The result must be acknowledged before preparation, services, a new battle or
saving become available.

## Return and checkpoints

Acknowledging a local escape resumes the same traversal instance, preserving
map position, facing, fog and world anchor. Pacing resets as it does for other
resolved encounters, so the next successful move does not immediately refight
the escaped encounter. Invalid early/stale acknowledgements retain the existing
battle ownership safeguards.

Failed Run selections carry forward for review with the other orders; Enter is
required again for the next round. T cannot assign an enemy target to Run. The
standalone test returns to preparation; the mapped route returns to the area.
The legacy battle path is unchanged.

An acknowledged escape can be saved with the existing version-3 outside-combat
checkpoint. No active escape roll or terminal result is serialized, so no format
change is needed. A reloaded checkpoint has the same party/visit and no phantom
victory reward or active encounter. Mid-battle snapshots remain excluded.

## Verification

```bash
bash run_native_escape.sh
```

94 checks exercise formula boundaries, target/payload rejection, duplicate/foreign
orders, success/failure/retry, multiple runners, MP and action stopping, earlier
damage/death/victory, inactive runners, no-reward receipts, roster identity,
acknowledgement locks, actual map/planning/result screens, return/pacing and
post-escape checkpoint reload. Prior suites, Java 21 Gradle tests/compilation and
the Xvfb window lifecycle remain in CI. Human desktop play-through remains pending.

Next: native combat item use. Final escape balancing, encounter-specific escape
restrictions, story defeat consequences and native normal-launcher migration
remain separate work.
