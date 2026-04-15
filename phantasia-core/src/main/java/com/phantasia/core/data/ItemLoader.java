// phantasia-core/src/main/java/com/phantasia/core/data/ItemLoader.java
package com.phantasia.core.data;

import com.phantasia.core.model.item.*;
import com.phantasia.core.tools.ItemBaker;

import java.io.*;

/**
 * Reads item records from items.dat at runtime.
 * Since ItemTable already holds everything in memory, this is
 * primarily useful for tools and save verification — the game
 * itself uses ItemTable.get(id) directly.
 *
 * Kept here for completeness and round-trip verification.
 */
public class ItemLoader {

    private final String path;
    public static final int RECORD_SIZE = 16; // Must match ItemBaker.RECORD_SIZE

    public ItemLoader(String path) {
        this.path = path;
    }

    /**
     * Load a single item record by ID.
     * Seeks directly — O(1), no scanning.
     */
    public ItemDefinition load(int id) throws IOException {
        if (id < 1 || id > 180)
            throw new IllegalArgumentException("Item ID out of range: " + id);

        try (RandomAccessFile raf = new RandomAccessFile(path, "r")) {
            long offset = (long) (id - 1) * ItemBaker.RECORD_SIZE;
            raf.seek(offset);

            byte[] record = new byte[ItemBaker.RECORD_SIZE];
            raf.readFully(record);

            return deserialize(record);
        }
    }

    /**
     * Verify the baked file against the live ItemTable.
     * Prints any discrepancies — useful after editing ItemTable.
     */
    public void verify() throws IOException {
        System.out.println("Verifying items.dat against ItemTable...");
        int mismatches = 0;

        for (int id = 1; id <= 180; id++) {
            if (!ItemTable.exists(id)) continue;

            ItemDefinition fromFile  = load(id);
            ItemDefinition fromTable = ItemTable.get(id);

            if (fromFile.attack()  != fromTable.attack()  ||
                    fromFile.defense() != fromTable.defense() ||
                    fromFile.gold()    != fromTable.gold()) {

                System.out.println("  MISMATCH at ID " + id
                        + " (" + fromTable.name() + ")");
                mismatches++;
            }
        }

        System.out.println(mismatches == 0
                ? "All records verified clean."
                : mismatches + " mismatches found — rebake recommended.");
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private ItemDefinition deserialize(byte[] r) {
        int id          = r[0]  & 0xFF;
        int catOrdinal  = r[1]  & 0xFF;
        int attackBonus = r[2]  & 0xFF;
        int defenseBonus= r[3]  & 0xFF;
        int enchant     = r[4]  & 0xFF;
        int potionRank  = r[5]  & 0xFF;
        int scrollId    = r[6]  & 0xFF;   // content ID for SCROLL items; 0 on all others
        boolean quest   = (r[7] & 0x01) != 0;
        int gold        = ((r[8] & 0xFF) << 8) | (r[9] & 0xFF);
        int jobMask     = ((r[10] & 0xFF) << 8) | (r[11] & 0xFF);
        int raceMask    = ((r[12] & 0xFF) << 8) | (r[13] & 0xFF);

        ItemCategory category = ItemCategory.values()[
                Math.min(catOrdinal, ItemCategory.values().length - 1)];

        String name = ItemTable.exists(id)
                ? ItemTable.get(id).name()
                : "Unknown Item " + id;

        return new ItemDefinition.Builder(id, name, category)
                .attack(attackBonus)
                .defense(defenseBonus)
                .enchant(enchant)
                .potionRank(potionRank)
                .scrollId(scrollId)
                .questItem(quest)
                .gold(gold)
                .jobRestriction(jobMask)
                .raceRestriction(raceMask)
                .build();
    }
}