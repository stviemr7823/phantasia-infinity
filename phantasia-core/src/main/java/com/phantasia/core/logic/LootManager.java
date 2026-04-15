// phantasia-core/src/main/java/com/phantasia/core/logic/LootManager.java
package com.phantasia.core.logic;

import com.phantasia.core.data.PartyLedger;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.model.item.Inventory;
import com.phantasia.core.model.item.ItemDefinition;
import com.phantasia.core.model.item.ItemTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Distributes loot after a successful combat encounter.
 *
 * In Phantasie III, defeated monsters yield:
 *   - Gold   : read from MON_TREASURE, pooled directly into party gold
 *   - Items  : each monster carries up to two item IDs (MON_ITEM_1 / MON_ITEM_2);
 *              dropped items are handed out to the first party member with space
 *
 * LootManager sits between the combat result and the inventory/ledger layers.
 * It never modifies combat state — it only reads dead monsters and writes to
 * the party's Inventory objects and the PartyLedger.
 *
 * USAGE:
 *   LootManager loot = new LootManager(survivors, ledger);
 *   LootResult  result = loot.distributeFrom(defeatedEnemies);
 *   System.out.println(result.describe());
 */
public class LootManager {

    private static final Random RNG = new Random();

    private final List<PlayerCharacter> party;
    private final PartyLedger           ledger;

    public LootManager(List<PlayerCharacter> party, PartyLedger ledger) {
        this.party  = party;
        this.ledger = ledger;
    }

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    /**
     * Reads loot from every defeated monster in the list and distributes it
     * to the party.  Only monsters with HP <= 0 are looted.
     *
     * @param enemies the full enemy list from the combat (including survivors)
     * @return a LootResult describing everything that was collected
     */
    public LootResult distributeFrom(List<Monster> enemies) {
        int             totalGold    = 0;
        List<String>    goldLines    = new ArrayList<>();
        List<String>    itemLines    = new ArrayList<>();
        List<String>    overflowLines = new ArrayList<>();

        for (Monster m : enemies) {
            if (m.isAlive()) continue;   // only loot the fallen

            // --- Gold ---
            int treasure = m.getTreasure();
            if (treasure > 0) {
                totalGold += treasure;
                goldLines.add(m.getName() + " yields " + treasure + " gp.");
            }

            // --- Item 1 ---
            int item1Id = m.getDataCore().getStat(DataLayout.MON_ITEM_1);
            if (item1Id > 0 && ItemTable.exists(item1Id)) {
                String line = distributeItem(item1Id);
                (line.contains("(no room)") ? overflowLines : itemLines).add(line);
            }

            // --- Item 2 ---
            int item2Id = m.getDataCore().getStat(DataLayout.MON_ITEM_2);
            if (item2Id > 0 && ItemTable.exists(item2Id) && item2Id != item1Id) {
                String line = distributeItem(item2Id);
                (line.contains("(no room)") ? overflowLines : itemLines).add(line);
            }
        }

        // Pool gold into the ledger
        if (totalGold > 0) ledger.addGold(totalGold);

        return new LootResult(totalGold, itemLines, overflowLines, goldLines);
    }

    // -------------------------------------------------------------------------
    // Item distribution helpers
    // -------------------------------------------------------------------------

    /**
     * Tries to hand an item to the first living party member with a free
     * pack slot.  If everyone's pack is full the item is lost (noted in the
     * result as overflow — this mirrors the original game's behaviour where
     * the party could not carry everything they found).
     */
    private String distributeItem(int itemId) {
        ItemDefinition item = ItemTable.get(itemId);

        // Find a recipient — prefer random order to avoid always front-loading
        List<PlayerCharacter> candidates = new ArrayList<>(party);
        java.util.Collections.shuffle(candidates, RNG);

        for (PlayerCharacter pc : candidates) {
            if (!pc.isAlive()) continue;
            Inventory inv = new Inventory(pc);
            if (!inv.isFull()) {
                inv.add(itemId);
                return pc.getName() + " picks up " + item.name() + ".";
            }
        }

        return item.name() + " left behind — packs are full. (no room)";
    }

    // -------------------------------------------------------------------------
    // LootResult
    // -------------------------------------------------------------------------

    /**
     * An immutable summary of everything that was collected from one encounter.
     *
     * Separate lists are kept for gold entries, item pickups, and items that
     * could not be carried — so the renderer can format each differently.
     */
    public record LootResult(
            int          totalGold,
            List<String> itemLines,
            List<String> overflowLines,
            List<String> goldLines
    ) {
        /**
         * Produces a compact, human-readable loot summary.
         *
         * Example:
         *   === LOOT ===
         *   Skeleton A yields 25 gp.
         *   Orc Warrior yields 40 gp.
         *   Total gold collected: 65 gp.  Party purse: 465 gp.
         *   Bonzo picks up Healing Potion 3.
         *   Korg picks up Sword +1.
         *   Dagger +2 left behind — packs are full.
         */
        public String describe(PartyLedger ledger) {
            StringBuilder sb = new StringBuilder();
            sb.append("=== LOOT ===\n");

            goldLines.forEach(l -> sb.append("  ").append(l).append("\n"));

            if (totalGold > 0) {
                sb.append(String.format("  Total gold collected: %d gp.  Party purse: %d gp.%n",
                        totalGold, ledger.getPartyGold()));
            } else {
                sb.append("  No gold found.\n");
            }

            if (itemLines.isEmpty() && overflowLines.isEmpty()) {
                sb.append("  No items found.\n");
            } else {
                itemLines.forEach(l -> sb.append("  ").append(l).append("\n"));
                overflowLines.forEach(l -> sb.append("  !! ").append(l).append("\n"));
            }

            return sb.toString();
        }

        public boolean hasLoot() {
            return totalGold > 0 || !itemLines.isEmpty();
        }

        public boolean hadOverflow() {
            return !overflowLines.isEmpty();
        }
    }
}