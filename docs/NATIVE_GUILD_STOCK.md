# Authored guild terms and native shop stock

The N diagnostic route now applies GuildData counter pricing/caps and tracks
finite shop listings plus one-time purchases in the native campaign.

## Guild counters

An authored GUILD_COUNTER opens the native party screen with its own terms. The
price is trainingCostBase + currentLevel × trainingCostScale, calculated with long
arithmetic. maxLevel caps the target level inclusively; zero means no local cap.
The screen shows the actual counter price and cap before G trains the member.

The curriculum still supplies XP thresholds, stat growth and techniques taught
at each level. Counter pricing does not replace that curriculum or invent more
levels. XP alone does not teach techniques. A successful operation advances one
level, charges the counter's price and learns the defined moves atomically.
Insufficient gold/XP, overflow, invalid terms and caps leave progress unchanged.
The existing trainer NPC/standalone test retain curriculum pricing.

If a counter has a completion flag, only a successful training sets it. Leaving
without training preserves the counter. The open screen rejects further training
once it is consumed; its learned techniques can still be readied before leaving.
Field preparation through P continues to permit reconfiguration, not training.

The settlement test counter is at **(19,9)**. Its supplied terms are base 5, scale
2 and cap 2: level-one training costs 7 and level-three training is refused there.
The trainer NPC remains a separate test of curriculum-priced training. These
numbers are diagnostic values, not final game balance.

## Native purchases

NativeShopState owns purchase counts; ShopInventory and ShopEntry remain authored
definitions. One purchase grants one item and consumes one unit of finite stock.
Quantity -1 is unlimited; zero is sold out. A oneTimePurchase listing is limited
to one purchase and disappears afterward, including if its quantity is -1.
Required quest flags still gate listings. A finite exhausted listing stays visible
as OUT; the one-time listing is removed from the view.

The native campaign preflights party membership, visibility/stock, price, purse
funds and a free slot in the selected native pack before committing stock,
inventory and gold. Failure leaves all three unchanged. Prices use the item base
gold times the positive finite multiplier, floored with a minimum of one gold.
A price beyond the purse range displays LIMIT and cannot be purchased. Buying can
make space for previously earned pending gold through the existing retry path.

| Listing source | Stable stock identity |
| --- | --- |
| Merchant NPC / referenced shop counter | Registered shop ID and original row index |
| Inline shop counter payload | Map ID, feature coordinates and original row index |

Duplicate item rows are independent listings. Merchants/counters referencing the
same registered shop share depletion. Separate inline counters retain independent
stock even when their embedded shop IDs match. Filtered UI rows retain their
original authored indices, so hiding a purchased or gated row cannot consume
another listing. A row signature also rejects a changed listing after purchase.

Reopening, map travel, party selection and checkpoint reload do not replenish
stock. Selling to the shop does not restock its authored listings or clear a
one-time purchase. No timer or quest-based restock rule is introduced here:
ShopInventory.restockFlag remains preserved authored metadata awaiting a defined
restock policy. Per-entry required flags already support newly visible wares.
The legacy comparison shop retains its prior non-depleting runtime behavior.

The default shop keeps its two original unlimited listings and adds two tests:
Healing Potion 2 has quantity 2 at a 0.1 multiplier (4 gold each); Magic Potion 1
is a one-time listing at a 0.25 multiplier (6 gold). These are test supplies.

## Checkpoints and compatibility

Native writer version 3 adds canonical catalog/row purchase counts and row
signatures to the existing campaign checkpoint. Version-1 and version-2 readers
remain: their absent stock history starts empty, and version 1 also has no bank
balance. Counts, duplicate records, catalog identity, row existence, signatures
and authored quantity/one-time limits are validated before installation. Invalid
stock fails without replacing the running owner.

The application supplies the actual shop registry during save/load. The older
maps-only NativeSaveManager overload remains usable for checkpoints without
registered stock history; stock-aware callers supply maps and shops. Inline
catalogs resolve from their map feature payloads.

The new guild counter and shop rows change the default content fingerprint.
Older checkpoints still require their matching authored exports; format readers
do not silently migrate between different map/shop definitions. No normal legacy
save path changes. See [NATIVE_CHECKPOINTS.md](NATIVE_CHECKPOINTS.md).

## Verification

```bash
bash run_native_economy.sh
```

87 checks cover guild costs/caps, atomic failure, curriculum learning, free/large
prices and one-shot counters; finite/unlimited/zero/one-time stock, duplicate rows,
flag gates, distinct catalogs, full packs, insufficient gold, invalid prices and
combat locks; actual merchant/guild/inline counter screens, reopening, version-3
checkpoints, source validation and fresh-JVM reload. The service suite now has
100 checks, including retained version-1/2 reading. Prior suites, Java 21 Gradle
tests/compilation and the Xvfb window lifecycle remain in CI. Human desktop
play-through and final phone controls remain pending.

Native [battle escape](NATIVE_ESCAPE.md) is now active. Native [combat potions](NATIVE_COMBAT_ITEMS.md) are now active. Native [spell planning](NATIVE_SPELL_PLANNING.md) is active with explicit test permissions. Next: native inventory/equipment preparation. Automatic restocking, additional final balance rules
and moving the native route into the normal launcher remain separate work.
