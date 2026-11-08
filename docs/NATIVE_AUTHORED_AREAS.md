# Authored areas with native party ownership

Launch `bash run_mockup.sh` with JDK 21, then press **N** at the entry menu.
The settlement, dungeon rooms, courtyard and hall now run with the native party.
V opens preparation/standalone combat for that same native party. Enter retains
the separate legacy comparison route; the normal game launcher remains legacy.

## One native owner

LocalSession is a narrow authored-area interface for member vitals, Agility,
inventory slots, party gold, flags, pending chest rolls and the world anchor.
GameSession and NativeCampaignV5 implement it directly. Native members forward
to their actual PlayerStateV5/PlayerRuntimeV5; no PlayerCharacter copies or
round-trip synchronization are used.

The common LocalMapTraversal, LocalFeatureEffects, LocalMapScreen, dialogue
evaluation and merchant overlay now consume that interface. Legacy packs remain
eight slots; native packs use all ten v5 slots. Chest capacity checks and key
searches honor the actual member capacity. Trap detection reads native Agility,
and damage changes the same HP used by native combat. Native dialogue flags gate
doors and conditional NPCs without touching the legacy comparison session.

Chest collection remains all-or-nothing, with one cached blocked roll per feature
in the campaign. Gold limits, completion flags and repeated claims retain their
existing checks. The merchant buys/sells against native inventory and gold. Sale
overflow now leaves the item in place for either session type. The existing shop
limitation remains: finite stock depletion/one-time purchase enforcement is not
implemented; use the unlimited diagnostic listings for this test route.

## Exploration, battle, guild, preparation

NativeLocalEncounterFlow counts successful ordinary moves once each, tracks
per-map pacing within a visit and starts the native campaign's battle. The fixture
uses six moves in either dungeon room, a test enemy worth 40 XP/20 gold, and enemy
Agility 10. Settlement/castle areas are peaceful. These are test values, not final
game balance. Custom diagnostic content also uses this supplied test composition.

Movement/interactions remain suspended until the native battle's terminal result
is acknowledged. Return preserves the traversal instance, map position, facing,
fog and world anchor. Early acknowledgement fails; stale results cannot release a
newer encounter. The battle awards XP/gold to the exact exploration members once.
Native combat currently supports victory/defeat; escape is still absent from this
native slice (the legacy comparison path retains its escape behavior).

Bump Test trainer in the settlement to open native guild training. G trains the
selected member when XP and gold qualify and grants the curriculum's techniques
only as part of that successful level-up. Number keys ready learned techniques.
Esc returns to the same map visit. Press P anywhere while exploring with an
active party to change preparation; G there directs you to a trainer instead of
performing training remotely. Battles and preparation share the same native party
screen, with context determining which operations are available.

The standalone B/R shortcuts are disabled in mapped contexts: movement starts
battles and the map pause menu resets the complete diagnostic session. Native
inn/bank NPC services are explicitly unavailable for now; they never route into
legacy HP or gold. Custom inn/guild/bank counter settings remain pending as before.

## Controls

| Context | Controls |
| --- | --- |
| Entry menu | N native areas; V same native party test/preparation; Enter legacy comparison |
| Native areas | Arrows/WASD move; P prepare; Esc pause |
| Trainer | Up/Down member; G guild level-up; number keys ready learned moves; Esc area |
| Field preparation | Up/Down member; number keys change readied moves; Esc area |
| Native battle | A attack; D defend; 1/2 readied move; T target; Enter confirm/acknowledge |
| Map pause | Enter/Esc resume; R reset both diagnostic sessions; Q quit |

## Persistence and limits

Native progress, flags, inventories, chest cache and pending combat gold survive
area changes and return to the entry menu during the current test session. Native
R from the standalone screen resets that native test party; N then creates a visit
using the new owner. The map pause reset replaces both test sessions and all visits.

This remains opt-in diagnostic wiring. There is no native campaign save/load,
resume-inside snapshot, normal-game launcher migration, native inn/bank service,
final curriculum or authored encounter table in this increment. The new party
interface does not change legacy saves or the existing generated-dungeon path.
Pending rewards and learned/readied sets must be included in a future campaign
snapshot before claiming restart continuity.

## Verification

```bash
bash run_native_local_maps.sh
```

110 checks cover ten-slot chest distribution, blocked reward retry across visits,
native gold overflow, trap damage/detection, keys in slot ten, defeat, exact owner
identity, encounter pacing/suspension, stale results, actual guide/merchant/guild
and battle screens, castle links/gates, conditional NPCs, field preparation,
learned moves in later battles, menu/world return, reset and headless rendering.
Previous suites, the Java 21 Gradle build and the AWT/Xvfb window lifecycle remain
in CI. Manual desktop play-through and final phone controls remain pending.

Next: persist the full native campaign and visit state, then complete native
services and decide when to make this route the default.
