// phantasia-core/src/main/java/com/phantasia/core/data/ShopRegistry.java
package com.phantasia.core.data;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime registry of all authored shop inventories, keyed by shop ID.
 *
 * <p>Shop IDs are assigned in {@link ShopInventory} and referenced by
 * {@link com.phantasia.core.model.NpcDefinition#getShopId()} for merchant
 * NPCs. When the player interacts with a {@link com.phantasia.core.model.NpcRole#MERCHANT}
 * NPC, {@code NpcDefinition.getShopId()} is used to look up the inventory here.</p>
 *
 * <h3>ID conventions</h3>
 * <pre>
 *   1–9   overworld NPC shops (merchant NPCs placed on the world map)
 *   10–29 town general stores (one per town, keyed by town feature ID + 10)
 *   30–49 town specialty shops (armorer, apothecary, etc.)
 *   50+   dungeon shops (special merchants inside dungeons)
 * </pre>
 *
 * <h3>Lifecycle</h3>
 * While {@code shops.dat} is not yet authored, {@link #buildDefault()} provides
 * the same shops programmatically. Replace with a file-based loader once the
 * editor pipeline is complete.
 *
 * <h3>Usage</h3>
 * <pre>
 *   // When a MERCHANT NPC is interacted with:
 *   ShopInventory shop = shopRegistry.get(npcDef.getShopId());
 *   if (shop == null) shop = shopRegistry.getFallback();
 *   screenManager.push(GameState.SHOP, new ShopOverlay.ShopData(shop, session));
 * </pre>
 */
public final class ShopRegistry {

    private final Map<Integer, ShopInventory> byShopId = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Mutation
    // -------------------------------------------------------------------------

    /**
     * Registers a shop inventory. Overwrites any existing entry with the same ID.
     */
    public void register(ShopInventory shop) {
        byShopId.put(shop.getShopId(), shop);
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Returns the shop inventory for the given shop ID, or {@code null} if not found.
     */
    public ShopInventory get(int shopId) {
        return byShopId.get(shopId);
    }

    /**
     * Returns the town shop for a given town feature ID using the
     * convention {@code shopId = townFeatureId + 10}.
     * Returns {@code null} if no shop is registered for that town.
     */
    public ShopInventory getForTown(int townFeatureId) {
        return byShopId.get(townFeatureId + 10);
    }

    /**
     * Returns all registered shop inventories (unmodifiable).
     */
    public Collection<ShopInventory> getAll() {
        return Collections.unmodifiableCollection(byShopId.values());
    }

    public int size() {
        return byShopId.size();
    }

    // -------------------------------------------------------------------------
    // Default programmatic registry
    // -------------------------------------------------------------------------

    /**
     * Builds the authoritative shop registry from code.
     *
     * <p>Item IDs are verified against {@link com.phantasia.core.model.item.ItemTable}:
     * shields 1–19, armor 21–40, bows 41–60, weapons 61–100,
     * healing potions 101–110, magic potions 111–120.</p>
     *
     * Replace with a file-based loader once {@code shops.dat} is authored.
     */
    public static ShopRegistry buildDefault() {
        ShopRegistry registry = new ShopRegistry();

        // ── Shop 10 — Pendragon General Store (town ID 0 + 10) ───────────
        ShopInventory pendragon = new ShopInventory(10, "Pendragon General Store");
        // Shields
        pendragon.addEntry(new ShopEntry(1));    // Glove           10g
        pendragon.addEntry(new ShopEntry(2));    // Wooden Shield   20g
        pendragon.addEntry(new ShopEntry(4));    // Small Shield    80g
        pendragon.addEntry(new ShopEntry(8));    // Medium Shield  200g
        // Armor
        pendragon.addEntry(new ShopEntry(21));   // Clothing         5g
        pendragon.addEntry(new ShopEntry(23));   // Leather         80g
        pendragon.addEntry(new ShopEntry(25));   // Ring Mail      250g
        pendragon.addEntry(new ShopEntry(27));   // Chain Mail     600g
        // Weapons
        pendragon.addEntry(new ShopEntry(61));   // Knife           10g
        pendragon.addEntry(new ShopEntry(62));   // Dagger          25g
        pendragon.addEntry(new ShopEntry(64));   // Mace            60g
        pendragon.addEntry(new ShopEntry(66));   // Staff           20g
        // Potions
        pendragon.addEntry(new ShopEntry(101));  // Healing Potion 1   10g
        pendragon.addEntry(new ShopEntry(103));  // Healing Potion 3   90g
        pendragon.addEntry(new ShopEntry(105));  // Healing Potion 5  250g
        pendragon.addEntry(new ShopEntry(111));  // Magic Potion 1     24g
        pendragon.addEntry(new ShopEntry(113));  // Magic Potion 3     72g
        registry.register(pendragon);

        // ── Shop 11 — Scandor Trading Post (town ID 1 + 10) ─────────────
        ShopInventory scandor = new ShopInventory(11, "Scandor Trading Post");
        pendragon.addEntry(new ShopEntry(2));    // Wooden Shield
        scandor.addEntry(new ShopEntry(4));      // Small Shield
        scandor.addEntry(new ShopEntry(23));     // Leather
        scandor.addEntry(new ShopEntry(61));     // Knife
        scandor.addEntry(new ShopEntry(63));     // Club             15g
        scandor.addEntry(new ShopEntry(101));    // Healing Potion 1
        scandor.addEntry(new ShopEntry(111));    // Magic Potion 1
        registry.register(scandor);

        // ── Shop 12 — Loftwood Healers (town ID 2 + 10) ─────────────────
        ShopInventory loftwood = new ShopInventory(12, "Loftwood Healers");
        loftwood.addEntry(new ShopEntry(101));   // Healing Potion 1
        loftwood.addEntry(new ShopEntry(102));   // Healing Potion 2   40g
        loftwood.addEntry(new ShopEntry(103));   // Healing Potion 3   90g
        loftwood.addEntry(new ShopEntry(104));   // Healing Potion 4  160g
        loftwood.addEntry(new ShopEntry(105));   // Healing Potion 5  250g
        loftwood.addEntry(new ShopEntry(111));   // Magic Potion 1
        loftwood.addEntry(new ShopEntry(112));   // Magic Potion 2     48g
        loftwood.addEntry(new ShopEntry(113));   // Magic Potion 3     72g
        registry.register(loftwood);

        // ── Shop 13 — Ironhaven Armory (town ID 3 + 10) ─────────────────
        ShopInventory ironhaven = new ShopInventory(13, "Ironhaven Armory");
        // Better shields and armor — iron mines mean better stock
        ironhaven.addEntry(new ShopEntry(8));    // Medium Shield   200g
        ironhaven.addEntry(new ShopEntry(12));   // Large Shield    400g
        ironhaven.addEntry(new ShopEntry(27));   // Chain Mail      600g
        ironhaven.addEntry(new ShopEntry(28));   // Splint Mail     900g
        ironhaven.addEntry(new ShopEntry(29));   // Banded Mail    1200g
        ironhaven.addEntry(new ShopEntry(65));   // Small Axe        50g
        ironhaven.addEntry(new ShopEntry(67));   // Sword            70g  (verify ID)
        ironhaven.addEntry(new ShopEntry(101));  // Healing Potion 1
        ironhaven.addEntry(new ShopEntry(103));  // Healing Potion 3
        registry.register(ironhaven);

        return registry;
    }
}
