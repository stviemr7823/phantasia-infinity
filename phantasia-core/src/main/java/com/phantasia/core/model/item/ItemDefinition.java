// phantasia-core/src/main/java/com/phantasia/core/model/item/ItemDefinition.java
package com.phantasia.core.model.item;

import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.model.Stat;

/**
 * Authoritative, immutable definition of a game item.
 * Ground-truthed against Phantasie III binary structures.
 *
 * SCROLLS:
 *   scrollId identifies which scroll text entry to display when the player
 *   reads this scroll.  Scrolls are NOT consumable — they may be read any
 *   number of times and remain in the pack until explicitly dropped or
 *   given away.  scrollId has no meaning on non-SCROLL items (value 0).
 */
public class ItemDefinition {
    private final int          id;
    private final String       name;
    private final ItemCategory category;
    private final int          gold;
    private final int          attack;
    private final int          defense;
    private final int          enchant;
    private final int          potionRank;
    private final int          scrollId;       // renamed from spellId
    private final boolean      questItem;
    private final int          jobRestriction;
    private final int          raceRestriction;
    private final int          levelRequirement;

    public ItemDefinition(int id, String name, ItemCategory category, int gold,
                          int attack, int defense, int enchant, int potionRank,
                          int scrollId, boolean questItem, int jobRestriction,
                          int raceRestriction, int levelRequirement) {
        this.id               = id;
        this.name             = name;
        this.category         = category;
        this.gold             = gold;
        this.attack           = attack;
        this.defense          = defense;
        this.enchant          = enchant;
        this.potionRank       = potionRank;
        this.scrollId         = scrollId;
        this.questItem        = questItem;
        this.jobRestriction   = jobRestriction;
        this.raceRestriction  = raceRestriction;
        this.levelRequirement = levelRequirement;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public int          id()               { return id; }
    public String       name()             { return name; }
    public ItemCategory category()         { return category; }
    public int          gold()             { return gold; }
    public int          attack()           { return attack; }
    public int          defense()          { return defense; }
    public int          enchant()          { return enchant; }
    public int          potionRank()       { return potionRank; }

    /** Content ID for SCROLL items — identifies which text to display. 0 on all other items. */
    public int          scrollId()         { return scrollId; }

    public boolean      isQuestItem()      { return questItem; }
    public int          jobRestriction()   { return jobRestriction; }
    public int          raceRestriction()  { return raceRestriction; }
    public int          levelRequirement() { return levelRequirement; }

    // -------------------------------------------------------------------------
    // Derived helpers
    // -------------------------------------------------------------------------

    public boolean isConsumable() {
        return category == ItemCategory.HEALING_POTION
                || category == ItemCategory.MAGIC_POTION;
        // NOTE: SCROLL is intentionally excluded — scrolls are not consumed on use.
    }

    public boolean isEquippable() {
        return category == ItemCategory.WEAPON
                || category == ItemCategory.SHIELD
                || category == ItemCategory.ARMOR
                || category == ItemCategory.BOW;
    }

    public boolean isReadable() {
        return category == ItemCategory.SCROLL;
    }

    public EquipSlot getEquipSlot() {
        return switch (category) {
            case WEAPON -> EquipSlot.WEAPON;
            case SHIELD -> EquipSlot.SHIELD;
            case ARMOR  -> EquipSlot.ARMOR;
            case BOW    -> EquipSlot.BOW;
            default     -> throw new IllegalStateException(name + " is not equippable.");
        };
    }

    /** HP restored when this potion is used.  Formula: potionRank². */
    public int healingAmount() {
        return potionRank * potionRank;
    }

    /** MP restored when this potion is used.  Formula: 3 × potionRank. */
    public int magicRestoreAmount() {
        return 3 * potionRank;
    }

    public boolean canBeEquippedBy(PlayerCharacter pc) {
        int jobBit = 1 << pc.getJob();
        if ((this.jobRestriction & jobBit) != 0) return false;
        if (pc.getStat(Stat.LEVEL) < this.levelRequirement) return false;
        return true;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static class Builder {
        private int          id;
        private String       name;
        private ItemCategory category;
        private int          gold;
        private int          attack;
        private int          defense;
        private int          enchant;
        private int          potionRank;
        private int          scrollId;       // renamed from spellId
        private boolean      questItem;
        private int          jobRestriction;
        private int          raceRestriction;
        private int          levelRequirement;

        public Builder(int id, String name, ItemCategory category) {
            this.id       = id;
            this.name     = name;
            this.category = category;
        }

        public Builder gold(int gold)                     { this.gold             = gold;             return this; }
        public Builder attack(int attack)                 { this.attack           = attack;           return this; }
        public Builder defense(int defense)               { this.defense          = defense;          return this; }
        public Builder enchant(int enchant)               { this.enchant          = enchant;          return this; }
        public Builder potionRank(int potionRank)         { this.potionRank       = potionRank;       return this; }
        public Builder scrollId(int scrollId)             { this.scrollId         = scrollId;         return this; }
        public Builder questItem(boolean questItem)       { this.questItem        = questItem;        return this; }
        public Builder jobRestriction(int jobRestriction) { this.jobRestriction   = jobRestriction;   return this; }
        public Builder raceRestriction(int raceRestriction){ this.raceRestriction = raceRestriction;  return this; }
        public Builder levelRequirement(int lvl)          { this.levelRequirement = lvl;              return this; }

        public ItemDefinition build() {
            return new ItemDefinition(id, name, category, gold, attack, defense,
                    enchant, potionRank, scrollId, questItem,
                    jobRestriction, raceRestriction, levelRequirement);
        }
    }
}