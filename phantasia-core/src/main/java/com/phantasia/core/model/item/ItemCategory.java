// phantasia-core/src/main/java/com/phantasia/core/model/item/ItemCategory.java
package com.phantasia.core.model.item;

/**
 * The seven categories from the 1987 manifest.
 * Determines how an item is used and where it can be equipped.
 */
public enum ItemCategory {
    SHIELD,          // Equipped — off hand
    ARMOR,           // Equipped — body
    BOW,             // Equipped — ranged weapon
    WEAPON,          // Equipped — melee weapon
    HEALING_POTION,  // Consumable — restores HP by X²
    MAGIC_POTION,    // Consumable — restores MP by 3X
    SCROLL,          // Readable — displays scroll text; stays in pack after reading
    TREASURE         // Quest items, valuables, story objects
}