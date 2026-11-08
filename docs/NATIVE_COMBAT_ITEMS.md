# Native combat potions

The native campaign can now use its carried healing and magic potions during a
reviewed combat round. This uses the same ten inventory slots and HP/MP records
as exploration, shops, checkpoints and guild training.

## Selection and resolution

I cycles carried potions that can currently help a living party member. The first
beneficial ally is selected automatically; Y cycles beneficial allies. The order
row shows the potion name, one-based carried slot and intended ally. Enter
confirms the entire party plan. Selection does not consume anything. Replacing
an item order with Attack, Defend or Run leaves the potion in the pack.

One item order replaces that character's action for the round. The typed order
captures the exact slot and item ID. Preflight checks the whole plan before any
turn runs: party membership, living user/target, a beneficial effect, and the
expected potion still occupying that slot. Invalid plans require review without
advancing the round or consuming resources.

On the user's initiative turn, the resolver checks the target and slot again.
If an earlier action filled the target's HP/MP, killed the target, or replaced the
carried item, the action is skipped and the potion is retained. Dead or sleeping
users likewise retain their potion. Escape or victory before the user's turn
stops that later order. Successful use removes exactly one potion, restores only
the missing HP/MP up to its existing item-table amount, and emits the actual
restored amount. It does not attack, spend MP, resurrect, or wake anyone.

Potion orders are cleared after each resolved round and require fresh selection.
Other orders retain the existing carry-forward behavior. No automatic repeated
potion use is implied by the previous round.

## Compatibility and limits

Only existing HP and MP potions are exposed. Scrolls, resurrection items,
item transfer and equipment changes are not introduced. No new balance values or
canonical content are added. USE_ITEM is appended to Action to preserve existing
ordinal positions. Legacy item-use behavior is unchanged. The shared narrator and
JME/libGDX event adapters accept the new item event; those older frontends have no
new item animation or selection UI.

Checkpoint version 3 already stores inventory and vitals; no format revision is
needed. Saving remains restricted to stable outside-combat visits. Reloading
preserves spent slots and restored resources, including slot ten.

## Verification

```bash
bash run_native_items.sh
```

95 checks cover clamped HP/MP restoration, exact slot consumption, malformed and
stale plans, foreign/full/dead targets, interrupted turns, changed inventory,
earlier escape/victory, ten-slot native ownership, target overrides, fresh-round
selection, actual diagnostic controls, rendered plan/result screens and a fresh
JVM checkpoint reload. The suite runs in Java 21 CI alongside previous regression
suites, core Gradle tests, j2d/editor compilation and the Xvfb window smoke.

The local compatibility run uses temporary Java 17 source substitutions; Java 21
CI checks the actual source. JME/libGDX adapter edits are not a full build of those
frontends. Human desktop playthrough remains pending.

Native [spell planning](NATIVE_SPELL_PLANNING.md) is now active. Next: native inventory/equipment preparation.
