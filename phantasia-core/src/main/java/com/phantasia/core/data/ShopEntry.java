// phantasia-core/src/main/java/com/phantasia/core/data/ShopEntry.java
package com.phantasia.core.data;

/**
 * A single item listing in a {@link ShopInventory}.
 *
 * @param itemId           references ItemTable / items.dat
 * @param quantity         stock count (-1 = unlimited)
 * @param priceMultiplier  1.0 = base price from ItemTable, 1.5 = 50% markup
 * @param requiredFlag     item only appears after this flag is set (nullable)
 * @param oneTimePurchase  removed from shop after buying
 */
public record ShopEntry(
        int       itemId,
        int       quantity,
        float     priceMultiplier,
        QuestFlag requiredFlag,
        boolean   oneTimePurchase
) {

    /** Convenience constructor for a standard unlimited-stock item at base price. */
    public ShopEntry(int itemId) {
        this(itemId, -1, 1.0f, null, false);
    }

    /** Convenience constructor with quantity and price. */
    public ShopEntry(int itemId, int quantity, float priceMultiplier) {
        this(itemId, quantity, priceMultiplier, null, false);
    }
}