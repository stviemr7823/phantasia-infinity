package com.phantasia.core.model.item;

/**
 * The four equipment slots — exact original four, no more.
 * Maps directly onto DataLayout offsets for the character record.
 */
public enum EquipSlot {
    WEAPON,   // Main hand melee
    SHIELD,   // Off hand
    ARMOR,    // Body
    BOW;      // Ranged

    /** True if this category can occupy this slot. */
    public boolean accepts(ItemCategory category) {
        return switch (this) {
            case WEAPON -> category == ItemCategory.WEAPON;
            case SHIELD -> category == ItemCategory.SHIELD;
            case ARMOR  -> category == ItemCategory.ARMOR;
            case BOW    -> category == ItemCategory.BOW;
        };
    }
}