// phantasia-core/src/main/java/com/phantasia/core/data/LootTable.java
package com.phantasia.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Authored loot contents for a dungeon chest or similar container.
 *
 * A LootTable defines what the player finds when they open a chest:
 * guaranteed gold, guaranteed items, and weighted random items.
 *
 * GUARANTEED vs RANDOM:
 *   - Gold and guaranteed items are always granted.
 *   - Random entries are rolled using weights — one winner is selected
 *     per roll. The rollCount determines how many random draws are made.
 *
 * TRAPS:
 *   The trap field on the owning PlacedFeature handles whether the chest
 *   is trapped. LootTable is purely about contents.
 *
 * LIFECYCLE:
 *   - Authored in the editor as part of a CHEST PlacedFeature
 *   - Serialized as the serviceData payload on PlacedFeature
 *   - Evaluated at runtime when the player steps on the chest tile
 *   - The PlacedFeature's consumedFlag prevents re-looting
 */
public class LootTable {

    private int               goldMin;           // minimum gold (always granted)
    private int               goldMax;           // maximum gold (always granted)
    private final List<LootEntry> guaranteed = new ArrayList<>();  // always granted
    private final List<LootEntry> randomPool = new ArrayList<>();  // weighted random
    private int               rollCount = 1;     // number of random draws

    private static final Random RNG = new Random();
    public static final int MAX_ROLLS = 1_000_000; // bounded content processing, not a gameplay balance value

    public LootTable() {}

    public LootTable(int goldMin, int goldMax) {
        this.goldMin = goldMin;
        this.goldMax = goldMax;
    }

    // -------------------------------------------------------------------------
    // Building
    // -------------------------------------------------------------------------

    public LootTable gold(int min, int max) {
        this.goldMin = min; this.goldMax = max; return this;
    }

    public LootTable addGuaranteed(int itemId, int quantity) {
        guaranteed.add(new LootEntry(itemId, quantity, 0));
        return this;
    }

    public LootTable addRandom(int itemId, int quantity, int weight) {
        randomPool.add(new LootEntry(itemId, quantity, weight));
        return this;
    }

    public LootTable rolls(int count) {
        this.rollCount = count; return this;
    }

    // -------------------------------------------------------------------------
    // Evaluation
    // -------------------------------------------------------------------------

    /**
     * Rolls the loot table and returns the results.
     *
     * @return a LootResult with gold amount, guaranteed items, and rolled items
     */
    public LootResult evaluate() { return evaluate(RNG); }

    /** Deterministic seam for tests; the normal caller retains ordinary random rolls. */
    public LootResult evaluate(java.util.random.RandomGenerator random) {
        java.util.Objects.requireNonNull(random);
        if (goldMin < 0 || goldMax < goldMin || rollCount < 0 || rollCount > MAX_ROLLS)
            throw new IllegalArgumentException("Invalid loot gold range or roll count");
        for (var entry : guaranteed) validateEntry(entry, false);
        long totalWeight = 0;
        for (var entry : randomPool) { validateEntry(entry, true); totalWeight += entry.weight(); }
        int gold = (int) (goldMin + (goldMax > goldMin ? random.nextLong((long) goldMax - goldMin + 1) : 0));
        List<LootEntry> results = new ArrayList<>(guaranteed);
        for (int roll = 0; roll < rollCount && totalWeight > 0; roll++) {
            long draw = random.nextLong(totalWeight), accumulated = 0;
            for (var entry : randomPool) {
                accumulated += entry.weight();
                if (draw < accumulated) { results.add(entry); break; }
            }
        }
        return new LootResult(gold, List.copyOf(results));
    }
    private static void validateEntry(LootEntry entry, boolean weighted) {
        if (!com.phantasia.core.model.item.ItemTable.exists(entry.itemId()) || entry.quantity() <= 0
                || weighted && entry.weight() <= 0) throw new IllegalArgumentException("Invalid loot item, quantity or weight");
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int               getGoldMin()     { return goldMin; }
    public int               getGoldMax()     { return goldMax; }
    public List<LootEntry>   getGuaranteed()  { return Collections.unmodifiableList(guaranteed); }
    public List<LootEntry>   getRandomPool()  { return Collections.unmodifiableList(randomPool); }
    public int               getRollCount()   { return rollCount; }

    // -------------------------------------------------------------------------
    // Result container
    // -------------------------------------------------------------------------

    /**
     * The outcome of evaluating a loot table.
     *
     * @param gold  total gold to add to the party ledger
     * @param items items to grant (both guaranteed and rolled)
     */
    public record LootResult(int gold, List<LootEntry> items) {}

    /**
     * A single item in a loot table.
     *
     * @param itemId   references ItemTable
     * @param quantity how many to grant
     * @param weight   selection weight for random pool (0 for guaranteed items)
     */
    public record LootEntry(int itemId, int quantity, int weight) {}
}
