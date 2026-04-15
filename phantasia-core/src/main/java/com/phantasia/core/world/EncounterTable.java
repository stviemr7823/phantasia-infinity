// phantasia-core/src/main/java/com/phantasia/core/world/EncounterTable.java
package com.phantasia.core.world;

import java.util.List;
import java.util.Random;

/**
 * A weighted list of monster group definitions for a specific
 * dungeon zone or overland region.
 *
 * Each Entry names a monster type, a count range, and a weight.
 * Higher weight = more likely to appear. The table is rolled once
 * per encounter trigger to select what the party faces.
 *
 * Roll resolution is linear weighted — simple, faithful to 1987 design.
 */
public class EncounterTable {

    private static final Random rand = new Random();

    // -------------------------------------------------------------------------
    // Entry — one possible outcome in the table
    // -------------------------------------------------------------------------

    public record Entry(
            String monsterName,  // Key into MonsterFactory
            int    minCount,     // Minimum number that appear
            int    maxCount,     // Maximum number that appear
            int    weight        // Relative probability weight
    ) {
        /** Resolve a specific count for this entry. */
        public int rollCount() {
            if (minCount == maxCount) return minCount;
            return minCount + rand.nextInt(maxCount - minCount + 1);
        }
    }

    // -------------------------------------------------------------------------
    // Table
    // -------------------------------------------------------------------------

    private final List<Entry> entries;
    private final int         totalWeight;

    public EncounterTable(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException(
                    "EncounterTable must have at least one entry.");
        }
        this.entries     = List.copyOf(entries);
        this.totalWeight = entries.stream().mapToInt(Entry::weight).sum();
    }

    /**
     * Roll the table — returns one Entry selected by weighted random.
     */
    public Entry roll() {
        int roll       = rand.nextInt(totalWeight);
        int cumulative = 0;

        for (Entry e : entries) {
            cumulative += e.weight();
            if (roll < cumulative) return e;
        }

        return entries.get(entries.size() - 1); // Unreachable but safe fallback
    }

    public List<Entry> getEntries()  { return entries; }
    public int         getTotalWeight() { return totalWeight; }
    public int         size()        { return entries.size(); }
}