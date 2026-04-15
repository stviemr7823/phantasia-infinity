// phantasia-core/src/main/java/com/phantasia/core/model/PlayerCharacter.java
package com.phantasia.core.model;

import com.phantasia.core.model.item.EquipSlot;
import com.phantasia.core.model.item.ItemDefinition;
import com.phantasia.core.model.item.ItemTable;
import com.phantasia.core.logic.ExperienceTable;

import java.util.EnumMap;
import java.util.Map;

import static com.phantasia.core.model.item.ItemCategory.*;

public class PlayerCharacter extends Entity
{

    private final DataCore core;
    private final Map<EquipSlot, ItemDefinition> equipment = new EnumMap<>(EquipSlot.class);
    private final Map<Stat, Integer> baseStats = new EnumMap<>(Stat.class);

    public PlayerCharacter(DataCore core)
    {
        super(core.getName());
        this.core = core;
        // Ensure entity is alive on creation
        core.setStat(DataLayout.BODY_STATUS, 1);
    }

    // -------------------------------------------------------------------------
    // Stats — with undead stat-lock enforcement
    // -------------------------------------------------------------------------

    public int getStat(Stat stat)
    {
        return stat.wide
                ? core.getShort(stat.offset)
                : core.getStat(stat.offset);
    }

    /**
     * Sets a stat value, respecting the undead stat-lock.
     * <p>
     * While cursed as undead, all writes to DataCore offsets in the range
     * PC_STAT_LOCK_START–PC_STAT_LOCK_END (0x0F–0x1F, indices 0–16 of the
     * stat block: STR through MAX_MAGIC) are silently ignored.  This prevents
     * any stat or skill growth while the character is in the undead state.
     * <p>
     * HP and MAX_HP writes are also blocked by this range — undead cannot be
     * healed by normal means.  The only way to restore an undead character is
     * a Raise Dead / Resurrection spell, which calls setUndead(false) first,
     * then sets HP to a target value.
     * <p>
     * Writes outside the lock range (XP, equipment, inventory, etc.) always
     * proceed normally.  XP accrual is separately blocked in ExperienceTable
     * by checking isUndead() before awarding XP.
     */
    public void setStat(Stat stat, int value)
    {
        // Enforce stat-lock for undead characters
        if (isUndead()
                && stat.offset >= DataLayout.PC_STAT_LOCK_START
                && stat.offset <= DataLayout.PC_STAT_LOCK_END)
        {
            return;   // silently blocked — undead cannot grow or be healed
        }
        if (stat.wide)
        {
            core.setShort(stat.offset, value);
        } else
        {
            core.setStat(stat.offset, value);
        }
    }

    // -------------------------------------------------------------------------
    // Entity contract
    // -------------------------------------------------------------------------

    @Override
    public int getLimbStatus(Limb limb)
    {
        return core.getLimbBitStatus(limb.ordinal());
    }

    @Override
    public void setLimbStatus(Limb limb, int status)
    {
        core.setLimbBitStatus(limb.ordinal(), status);
    }

    @Override
    public DataCore getDataCore()
    {
        return core;
    }

    @Override
    public int getExperience()
    {
        return core.getShort(DataLayout.PC_XP);
    }

    @Override
    public boolean canPerform(ActionType type)
    {
        return getLimbStatus(Limb.HEAD) < 3;
    }

    /**
     * A character is alive if they have HP > 0 AND are not undead.
     * <p>
     * Undead characters act in combat and appear in the party, but return
     * false here so they are skipped by all healing, XP, and liveness checks
     * that call isAlive(). Use isUndead() to distinguish them from the truly
     * dead (HP = 0, not undead).
     */
    @Override
    public boolean isAlive()
    {
        return getStat(Stat.HP) > 0 && !isUndead();
    }

    /**
     * Returns true if this character is under the Undead Curse.
     * <p>
     * Implemented by checking PC_RACE for the sentinel value PC_RACE_UNDEAD
     * (0xFF), as per the DataCore specification: the Race/Class offset at
     * dec 22 carries undead status for player characters.
     */
    public boolean isUndead()
    {
        return core.getStat(DataLayout.PC_RACE) == DataLayout.PC_RACE_UNDEAD;
    }

    /**
     * Applies or lifts the Undead Curse.
     * <p>
     * Setting undead = true:
     * - Writes PC_RACE_UNDEAD (0xFF) to PC_RACE.
     * - Pins HP to 1.  The stat-lock will prevent any healing from
     * changing it while the curse is active.
     * - The original Race ID is lost.  This is acceptable because Race
     * is not yet implemented; when it is, this method should save the
     * original value elsewhere before overwriting.
     * <p>
     * Setting undead = false (curse lifted, e.g. by Raise Dead spell):
     * - Writes 0 to PC_RACE (neutral / no race), clearing the sentinel.
     * - HP remains at 1 — the calling spell should restore it afterward.
     * - Stat growth is immediately re-enabled.
     */
    public void setUndead(boolean undead)
    {
        if (undead)
        {
            core.setStat(DataLayout.PC_RACE, DataLayout.PC_RACE_UNDEAD);
            // Set level to 20 via core directly — bypasses the stat-lock
            // which activates as soon as the race sentinel is written above
            core.setStat(DataLayout.PC_LEVEL, ExperienceTable.LEVEL_MAX);
            // HP intentionally left as-is (stuck at whatever value they
            // died with, per original Phantasie III behaviour)
        } else
        {
            // Clear sentinel — stat-lock lifted, normal healing resumes
            core.setStat(DataLayout.PC_RACE, 0);
        }
    }

    /**
     * Returns true if this character can participate in combat in any form —
     * alive or undead.  Used by CombatManager to build the combatant list.
     */
    public boolean isActiveInParty()
    {
        return isAlive() || isUndead();
    }

    // -------------------------------------------------------------------------
    // Vitals
    // -------------------------------------------------------------------------

    @Override
    public int getHp()
    {
        return getStat(Stat.HP);
    }

    @Override
    public void setHp(int value)
    {
        setStat(Stat.HP, value);
    }

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    public int getJob()
    {
        return core.getStat(DataLayout.PC_JOB);
    }

    public int getLevel()
    {
        return core.getStat(DataLayout.PC_LEVEL);
    }

    /**
     * Returns the raw race byte. Will be PC_RACE_UNDEAD (0xFF) if cursed.
     */
    public int getRace()
    {
        return core.getStat(DataLayout.PC_RACE);
    }

    /**
     * Returns the character's Race enum.
     * Returns Race.HUMAN as a safe fallback if the character is undead.
     */
    public Race getRaceEnum()
    {
        return Race.fromOrdinal(core.getStat(DataLayout.PC_RACE));
    }

    /**
     * Returns the character's SocialClass.
     */
    public SocialClass getSocialClass()
    {
        return SocialClass.fromOrdinal(core.getStat(DataLayout.PC_SOCIAL_CLASS));
    }

    /**
     * Sets the character's SocialClass. Call once at character creation.
     */
    public void setSocialClass(SocialClass sc)
    {
        core.setStat(DataLayout.PC_SOCIAL_CLASS, sc.ordinal());
    }

    // -------------------------------------------------------------------------
    // Equipment
    // -------------------------------------------------------------------------

    public int getEquippedItemId(EquipSlot slot)
    {
        return core.getStat(slotOffset(slot));
    }

    public void equip(EquipSlot slot, int itemId)
    {
        ItemDefinition item = ItemTable.get(itemId);

        // 1. Validate Slot Compatibility
        if (!slot.accepts(item.category()))
        {
            System.err.println(item.name() + " cannot be equipped in slot " + slot);
            return;
        }

        // 2. Validate Character Requirements
        if (!item.canBeEquippedBy(this))
        {
            System.err.println(this.getName() + " does not meet requirements for " + item.name());
            return;
        }

        // 3. The Actual Swap
        // If something was already there, it is simply overwritten in the map
        this.equipment.put(slot, item);

        // 4. Update the character's live stats
        this.recalculateStats();

        System.out.println(this.getName() + " equipped " + item.name() + " to " + slot);
    }

    /**
     * Updates derived stats based on base values and currently equipped items.
     * Follows safe design by re-calculating from the authoritative equipment map.
     */
    public void recalculateStats()
    {
        // 1. Start with your base stats (level-based or initial rolls)
        // Assuming you have internal fields for base values or reset to a known state
        int bonusAttack = 0;
        int bonusDefense = 0;

        // 2. Sum up bonuses from all equipped items
        // Using the safe getters we implemented: name(), gold(), attack(), defense(), enchant()
        for (ItemDefinition item : equipment.values())
        {
            bonusAttack += (item.attack() + item.enchant());
            bonusDefense += (item.defense() + item.enchant());
        }

    }

    public void unequip(EquipSlot slot)
    {
        core.setStat(slotOffset(slot), 0);
    }

    public ItemDefinition getEquipped(EquipSlot slot)
    {
        int id = getEquippedItemId(slot);
        return id == 0 ? null : ItemTable.get(id);
    }

    // -------------------------------------------------------------------------
    // Inventory
    // -------------------------------------------------------------------------

    public int getInventoryItemId(int slot)
    {
        if (slot < 0 || slot >= DataLayout.PC_INVENTORY_LEN)
            throw new IndexOutOfBoundsException("Inventory slot: " + slot);
        return core.getStat(DataLayout.PC_INVENTORY + slot);
    }

    public void setInventoryItem(int slot, int itemId)
    {
        if (slot < 0 || slot >= DataLayout.PC_INVENTORY_LEN)
            throw new IndexOutOfBoundsException("Inventory slot: " + slot);
        core.setStat(DataLayout.PC_INVENTORY + slot, itemId);
    }

    public void clearInventorySlot(int slot)
    {
        setInventoryItem(slot, 0);
    }

    // -------------------------------------------------------------------------
    // Equipment bonuses
    // -------------------------------------------------------------------------

    public int getWeaponAttackBonus()
    {
        ItemDefinition w = getEquipped(EquipSlot.WEAPON);
        return w == null ? 0 : w.attack() + w.enchant();
    }

    public int getArmorDefenseBonus()
    {
        int total = 0;
        ItemDefinition armor = getEquipped(EquipSlot.ARMOR);
        ItemDefinition shield = getEquipped(EquipSlot.SHIELD);
        if (armor != null) total += armor.defense() + armor.enchant();
        if (shield != null) total += shield.defense() + shield.enchant();
        return total;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int slotOffset(EquipSlot slot)
    {
        return switch (slot)
        {
            case WEAPON -> DataLayout.PC_EQUIP_WEAPON;
            case SHIELD -> DataLayout.PC_EQUIP_SHIELD;
            case ARMOR -> DataLayout.PC_EQUIP_ARMOR;
            case BOW -> DataLayout.PC_EQUIP_BOW;
        };
    }

    public void equip(ItemDefinition item, EquipSlot slot)
    {
        if (!slot.accepts(item.category()))
        {  // We changed this line [cite: 198]
            return;
            // We are likely MISSING a closing brace '}' for the IF block here!
        } // This brace now closes the METHOD, but the IF is still "open" to the compiler.

    }

    /**
     * Retrieves the raw, unmodified value of a stat before equipment or magic.
     */
    public int getBaseStat(Stat stat)
    {
        // Accessing your internal container for permanent/rolled stats.
        // This ensures that recalculateStats() always starts from a clean baseline.
        return this.getBaseStat(Stat.STRENGTH);
    }

    /**
     * Calculates Attack Strength: Strength + Weapon bonuses.
     * Ground-truthed: Items add to the base attribute for the final roll.
     */
    /**
     * Calculates the final Armor Class based on Agility (Dexterity)
     * and currently equipped gear.
     */
    /**
     * Calculates the final Armor Class based on Agility (Dexterity)
     * and currently equipped gear.
     */
    /**
     * Calculates Armor Class: Dexterity-based baseline + Armor bonuses.
     * In Phantasie III, your protection is a mix of your gear and your agility.
     */
    /**
     * Calculates Armor Class based on Dexterity and equipped gear.
     * Ground-truthed: Derived value, not a stored stat.
     */
    public int getArmorClass()
    {
        // Phantasie III typically uses a portion of Dexterity as a base
        int baseProtection = this.getStat(Stat.DEXTERITY) / 2;
        int gearBonus = 0;

        // Sum defense and enchantment from all equipped items in our map
        for (ItemDefinition item : equipment.values())
        {
            gearBonus += (item.defense() + item.enchant());
        }

        return baseProtection + gearBonus;
    }
    /**
     * Calculates the final Attack Strength based on Physical Power (Strength)
     * and currently equipped weapons.
     */
    /**
     * Calculates Attack Strength: Strength + Weapon/Equipment bonuses.
     */
    public int getAttackStrength()
    {
        int base = this.getStat(Stat.STRENGTH);
        int bonus = 0;

        for (ItemDefinition item : equipment.values())
        {
            bonus += (item.attack() + item.enchant());
        }
        return base + bonus;
    }
}