// phantasia-core/src/main/java/com/phantasia/core/data/ShopInventory.java
package com.phantasia.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An authored inventory for a town shop.
 *
 * Each shop has a unique ID referenced by merchant NPCs and shop counter
 * features. The inventory is a list of {@link ShopEntry} records, each
 * linking to an item in ItemTable with quantity, pricing, and quest gating.
 *
 * RESTOCKING:
 *   The restockFlag allows the shop to gain new items after a quest
 *   milestone — e.g., after clearing a dungeon, rare weapons appear.
 *   Items gated by requiredFlag on individual entries provide finer control.
 *
 * LIFECYCLE:
 *   - Authored in the editor's town/shop panel
 *   - Baked to shops.dat
 *   - Loaded at runtime by ShopRegistry
 *   - Opened when player bumps a SHOP_COUNTER feature or MERCHANT NPC
 */
public class ShopInventory {

    private int             shopId;
    private String          name;           // "Ironhaven Armory"
    private QuestFlag       restockFlag;    // new items appear after this (nullable)
    private final List<ShopEntry> entries = new ArrayList<>();

    public ShopInventory() {}

    public ShopInventory(int shopId, String name) {
        this.shopId = shopId;
        this.name   = name;
    }

    // -------------------------------------------------------------------------
    // Entry management
    // -------------------------------------------------------------------------

    public void addEntry(ShopEntry entry) {
        entries.add(entry);
    }

    public void removeEntry(ShopEntry entry) {
        entries.remove(entry);
    }

    public List<ShopEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Returns entries visible to the player given the current flag state.
     * @param flagChecker typically session::hasFlag
     */
    public List<ShopEntry> getAvailableEntries(
            java.util.function.Predicate<QuestFlag> flagChecker) {
        return entries.stream()
                .filter(e -> e.requiredFlag() == null
                        || flagChecker.test(e.requiredFlag()))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int       getShopId()      { return shopId; }
    public String    getName()        { return name; }
    public QuestFlag getRestockFlag() { return restockFlag; }

    public void setShopId(int id)              { this.shopId = id; }
    public void setName(String name)           { this.name = name; }
    public void setRestockFlag(QuestFlag flag) { this.restockFlag = flag; }
}
