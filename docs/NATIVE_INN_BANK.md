# Native inns and banks

The N diagnostic route now supports native INNKEEPER/BANKER NPCs and typed
INN_COUNTER/BANK_COUNTER features. Both use NativeCampaignV5 directly: no legacy
HP, MP, purse or bank state is read or changed. Closing an overlay returns to the
same local visit and tile. Combat, including an unacknowledged result, locks both
services.

The default test settlement has two new authored counters in the east lane:

| Position | Service | Test terms |
| --- | --- | --- |
| (15,9) | Inn | 2 gold per restored HP; 1 per MP; no revival |
| (17,9) | Bank | No deposit/withdrawal fees |

These values are diagnostic content, not final balance. Native innkeeper NPCs
without authored counter terms use existing InnData.standard (10 per HP, no
revival); banker NPCs use BankData.standard (zero fees). The legacy comparison
NPC services retain their old behavior, including their existing inn pricing.

## Rest

The quote sums each eligible member's missing HP times authored costPerHp, plus
missing MP at one gold per point. No additional minimum charge is invented for
the authored schema: healthy guests therefore have a zero quote. The overlay
shows the rate, current cost and member vitals before Enter confirms.

Normal inns restore living guests to full HP/MP and clear sleep. Dead members are
excluded from the cost and restoration. If restoredDead is explicitly enabled,
the quote includes those members' full HP deficit and missing MP, and the service
revives them. The normal map defeat restriction still prevents a fully defeated
party from walking to an inn; this does not add a new defeat escape rule.

The core requotes immediately before commit and validates funds with long
arithmetic. An unaffordable or invalid quote changes no gold or vitals. Success
does not alter XP, level, learned techniques or preparation. The overlay records
completion before accepting further input, so repeated Enter cannot charge again
during the same rest visit. Esc returns to exploration.

## Bank transfers

D selects deposit, W selects withdrawal. Enter a positive gross amount with 0-9;
Backspace deletes and Enter confirms. Ten digits are permitted, with a maximum
of Integer.MAX_VALUE. The screen shows rates and quoted fee/net credit first.

For either direction, the entered gross amount is debited from the source. The
fee is ceiling(gross × percent / 100), calculated once using long arithmetic;
gross minus fee is credited to the destination. Percentages must be 0..100.
A 100% fee explicitly credits zero. No amount is silently clamped.

Funds and destination capacity are checked before either balance changes.
Purse and bank each remain within 0..Integer.MAX_VALUE. Failed transfers preserve
both. Successful deposits can make space for already-earned pending combat gold;
the existing retry mechanism fills that space without duplicating the pending
award. The overlay displays pending gold separately. A successful transfer clears
the amount, preventing accidental repeat confirmation.

An authored completion flag is applied only after a successful service. Leaving
without buying rest or transferring gold does not consume it. A one-shot bank
overlay blocks further transactions after completion; a consumed counter no
longer opens. Unflagged bank services allow multiple explicit transfers.

## Checkpoints

Native checkpoint writer version 2 adds the bank balance alongside purse and
pending gold. Restored HP/MP and service completion flags use existing campaign
fields. The version-1 reader remains available and initializes its absent bank
balance to zero; unknown versions and invalid negative bank balances reject.

The default fixture changed when these two counters were added, so its authored
fingerprint also changed. Existing checkpoints require the matching old authored
exports, even though the version-1 record reader is retained. The content check
does not silently migrate a checkpoint to different map definitions.

Saving remains outside services: close the overlay, then use native-map pause
F5/F9 with --native-save PATH. No transactions autosave or touch normal legacy
saves. See [NATIVE_CHECKPOINTS.md](NATIVE_CHECKPOINTS.md).

## Verification

```bash
bash run_native_services.sh
```

98 checks cover rest quotes, MP/sleep, optional revival, insufficient funds and
large-cost rejection; fee rounding, conservation, invalid amounts, purse/bank
overflow and pending-award retry; real NPC/counter overlays, one-shot completion,
same-tile return, checkpoint version 2 and version-1 reading, and a fresh JVM
reload of bank/rest state. Service screens are also rendered headlessly. Prior
suites, Java 21 Gradle tests/compilation and the real Xvfb window lifecycle remain
in CI; manual desktop play-through remains pending.

Remaining service work: authored guild-counter price/cap settings, finite shop
stock and one-time purchases. Final balance and the normal-launcher native
migration remain separate decisions.
