# Native campaign checkpoints

Native diagnostics can now save and resume a campaign at an outside-combat
checkpoint, including inside an authored area. Saving is disabled by default.
Enable one explicit file when launching with JDK 21:

```bash
bash run_mockup.sh --native-save /path/to/existing-directory/test-campaign.pns
```

Use N to explore with the native party. Press Esc in the area, then **F5** in the
pause menu to save. **F9** from that pause menu or the entry menu loads the file.
An entry-menu save contains the native campaign without an active map visit.
No file is opened merely by passing the option; there is no autosave or automatic
load on startup/quit. The existing legacy SaveManager and normal save path are
not used. The parent directory must already exist.

## What survives restart

| State | Checkpoint contents |
| --- | --- |
| Native players | Exact 64-byte v5 records: IDs, names, attributes, level, HP/MP, XP, equipment and all ten pack slots |
| Preparation | Learned techniques and the ordered readied selection for each member |
| Progression | Explicit training curriculum and readied-slot limit used by this campaign |
| Shared campaign | Party gold, pending combat gold, all 64 quest flags and world position |
| Pending treasure | Cached rolls keyed by map and feature position, preserving exact gold/items |
| Local visit | Map, position, facing, world-return anchor, movement count and fog for every visited map |
| Encounter pacing | Per-map step counters and the last counted movement stamp |

The native owner and traversal are reconstructed together; no legacy-player
conversion or mutable state shared with the old session is involved. A blocked
chest resumes its existing roll instead of drawing again. An already claimed
chest remains consumed. Pending combat gold is retried through the existing
mechanism when space becomes available. These guarantees cover native checkpoints;
legacy saves still do not contain their runtime pending-reward caches.

Combat must be finished and its terminal result acknowledged first. Neither
ongoing battle plans nor unacknowledged reward receipts can be checkpointed.
Dialogue, shop, guild/preparation screens, legacy exploration and screen fades
also reject save/load; return to a native area or the entry menu. A defeated
party may be checkpointed outside combat, preserving its defeated state. The
legacy comparison party and its visit are not part of a native checkpoint.

## Validation and replacement

NativeSaveManager uses a distinct PNS1 envelope with version 1, explicit payload
length and SHA-256 checksum. The complete file is limited to 8 MiB. Counts and
enum names are bounded; native player kinds, reserved bytes, vitals, known items,
equipment eligibility, IDs, curriculum, learned/readied state, pending rewards,
map positions, fog bits and pacing are checked. Duplicate entries, unknown
versions, trailing bytes, truncation and invalid checksums fail explicitly.
The checksum detects damaged data; it is not an authentication mechanism.

Each checkpoint carries an authored-content fingerprint. Sorted map/NPC/shop
exports supply that fingerprint; NPC flag sets have stable bit order. A changed
map, NPC/dialogue or shop definition rejects the load rather than silently moving
the party or applying old rewards to different content. Saved progression rules
travel with the campaign. Code/rule migrations and edited-content save migration
remain future work; this is not an arbitrary-version compatibility promise.

Loading builds and validates a detached campaign, traversal, pacing and candidate
screen manager before replacing the running native owner. Failure leaves the
current party and screens intact. Successful load discards the old overlay stack
and resumes the saved map (or entry menu), while retaining the legacy comparison
session separately.

Saving validates the checkpoint before touching the target, writes a temporary
file in the same directory, forces the file bytes and atomically renames it over
the chosen path. Failure cleans up the temporary file. If the filesystem does
not support atomic replacement, saving fails; there is no unsafe fallback.
Directory metadata is not separately forced, so this is not a guarantee against
every power-loss/filesystem failure.

## Verification

```bash
bash run_native_saves.sh
```

94 checks include a fresh JVM reload, exact record/progression/preparation
restoration, multi-map fog, pending chest retry without RNG, pending combat gold,
resumed encounter pacing, F5/F9/reset wiring, failed-load identity preservation,
truncation/checksum/version/size and semantic rejection, changed-content rejection,
old-file preservation on failed save and temporary-file cleanup. The earlier
native/legacy map and combat suites, Java 21 Gradle tests/compilation and Xvfb
window lifecycle remain in CI. Human desktop play-through remains pending.

Next: native inn/bank services and remaining authored service settings. Mid-battle
snapshots and making the native route the normal launcher remain separate work.
