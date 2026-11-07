# Native party, guild progression and readied techniques

The diagnostic launcher now has a separate native-v5 test screen. Press **V** at
the entry menu after launching `bash run_mockup.sh` with JDK 21. This is an
executable progression slice, not a migration of the authored map screen.

NativeCampaignV5 owns detached native player records, one runtime per member,
party gold, learned/readied state and a single active battle. The same native
players receive damage, XP, level-ups and technique selections. No conversion to
legacy PlayerCharacter or synchronization between competing player records occurs.
Returning to the menu retains the native test party; R or whole-application reset
creates a fresh one. The authored-map mockup keeps its separate legacy session.

## Test route

1. Press V in the entry menu, then B to start a native test battle.
2. Review the two party orders. Enter confirms exactly one round. Repeat until
   victory, then Enter acknowledges the result and returns to preparation.
3. G trains the selected member if XP and gold qualify. Up/Down selects members.
4. Press 1 outside battle to ready the newly learned Thrust.
5. B starts another battle; 1 selects that member's first readied technique.
6. Enter confirms the full party plan. The learned technique uses the existing
   native round resolver, with its ordinary hit and damage rules.

| Context | Controls |
| --- | --- |
| Preparation | Up/Down member; B battle; G guild training; Esc entry menu |
| Preparation | 1 Thrust, 2 Slash, 3 Lunge, 4 Aim Blow toggle learned techniques |
| Battle planning | A attack; D defend; 1/2 readied slot; T override attack target |
| Round/result | Enter runs one reviewed round or acknowledges a terminal result |
| Native test | R starts a fresh native test party |

Held confirmation keys are debounced until release. Orders carry forward between
rounds; defeated targets are replaced with an automatic living reachable target
before the next review. The core planner rejects stale explicit targets. A and
readied commands can also choose a target explicitly. The fixture currently has
one enemy; multi-enemy override behavior is checked in the headless suite.

## Rules and ownership

XP awards do not change level or teach moves. A successful guild level-up charges
gold, grows HP/MP and grants the curriculum's newly learned techniques together.
Failed requirements leave all of those unchanged. Learning does not silently ready
a move or replace the player's existing selection. Learned moves remain available
after changing preparation. Readied selection is atomic, has a configurable slot
limit, rejects duplicates/unlearned moves and is locked throughout combat,
including the unacknowledged result screen. Training is likewise locked in battle.

Battle selection admits Attack, Defend and this member's readied physical
techniques. One order replaces the member's previous order; it does not add another
action. The underlying resolver still permits its low-level actions for existing
tests; campaign callers use the gated Battle API. Spells, items, escape and special
companions are outside this initial native screen.

The new Agility-ordered factory fixes initiative by player Agility and explicit
caller-supplied enemy Agility, with stable input-order ties and the resolver's
existing Aim Blow penalty. It does not change legacy/public-constructor combat.
Legacy monster storage has no native Agility field; the diagnostic supplies 10
explicitly rather than interpreting an unrelated byte as speed. Hit/damage rolls
remain ordinary dice.

One terminal receipt awards the captured encounter XP pool to living survivors
and grants gold once. XP saturates at the unsigned native 32-bit limit. Gold
exceeding the signed-int purse limit remains pending and is retried when training
makes space. Defeat grants no rewards. Results must be acknowledged before a new
battle; stale acknowledgements cannot release a later encounter.

## Explicit test curriculum

The two test characters are Fighters with two readied slots. These supplied values
exercise the mechanism and do not decide final job balance, slot count or unlocks.

| Target level | Total XP required | Cost | HP/MP gain | Learned move |
| --- | ---: | ---: | --- | --- |
| 2 | 20 | 10 gold | 4 / 1 | Thrust |
| 3 | 40 | 20 gold | 4 / 1 | Slash |

The fixture enemy awards a 40-XP pool and 20 gold. No higher training level is
invented. Core callers supply a validated, contiguous per-job curriculum and slot
limit. Final costs, growth tables and unlock curricula remain editable decisions.

## Persistence and next integration

Native campaigns currently start from fresh level-one records. Learned/readied
sets and pending gold are runtime campaign state, not hidden in the 64-byte
record's reserved space. There is no campaign snapshot/load format yet. The
diagnostic does not touch normal saves.

Next: introduce a shared party interface for authored-map vitals, inventory,
treasure/traps and services, then route those maps to this same native owner.
Persist the complete campaign (including learned/readied state and pending
rewards) before claiming restart continuity. Existing generated dungeons retain
their previous path.

## Verification

```bash
bash run_native_progression.sh
```

102 checks exercise detached ownership, atomic guild failures, level-bound
learning, slot limits, battle locks, actual native order plans, stable initiative,
automatic/overridden targets, survivor rewards, repeated result receipts, overflow,
stale results, actual menu/screen wiring, confirmation debounce and headless
rendering. Existing regression suites, Java 21 Gradle tests/compilation and the
thirty-frame AWT/Xvfb window lifecycle remain in CI. Manual desktop play-through
and final phone input remain pending.
