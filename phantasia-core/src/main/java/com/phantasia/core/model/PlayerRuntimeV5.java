package com.phantasia.core.model;

import com.phantasia.core.model.PlayerStateV5.Attribute;
import com.phantasia.core.model.item.EquipSlot;
import com.phantasia.core.model.item.ItemDefinition;
import com.phantasia.core.model.item.ItemTable;
import java.util.Objects;

/**
 * Headless gameplay operations on a native v5 player record.
 * This is not yet an Entity: the existing combat and save paths remain v4.
 */
public final class PlayerRuntimeV5 {
    private final PlayerStateV5 state;

    public PlayerRuntimeV5(PlayerStateV5 state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public String name() { return state.name(); }
    public int attribute(Attribute attribute) { return state.attribute(attribute); }
    public int hp() { return state.hp(); }
    public int maxHp() { return state.maxHp(); }
    public int mp() { return state.mp(); }
    public int maxMp() { return state.maxMp(); }
    public long experience() { return state.experience(); }
    public boolean isAlive() { return hp() > 0; }

    /** Bit 0 of the v5 player status byte denotes sleep. Other bits are preserved. */
    public static final int STATUS_ASLEEP = 0x01;

    public boolean isAsleep() {
        return (state.statusFlags() & STATUS_ASLEEP) != 0;
    }

    public void setAsleep(boolean asleep) {
        int flags = state.statusFlags();
        state.setStatusFlags(asleep
                ? flags | STATUS_ASLEEP
                : flags & ~STATUS_ASLEEP);
    }

    /** A detached copy: callers cannot change this runtime's record. */
    public PlayerStateV5 snapshot() { return PlayerStateV5.fromBytes(state.toBytes()); }

    public void damage(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative damage");
        state.setHp(Math.max(0, hp() - amount));
    }

    /** Healing alone does not resurrect a character. */
    public void heal(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative healing");
        if (isAlive())
            state.setHp((int) Math.min((long) maxHp(), (long) hp() + amount));
    }

    /** Deducts MP at most once; insufficient MP leaves the record unchanged. */
    public boolean spendMp(int cost) {
        if (cost < 0) throw new IllegalArgumentException("Negative MP cost");
        if (mp() < cost) return false;
        state.setMp(mp() - cost);
        return true;
    }

    public void restoreMp(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative MP restoration");
        state.setMp((int) Math.min((long) maxMp(), (long) mp() + amount));
    }

    public void awardExperience(long amount) {
        if (amount < 0 || amount > 0xFFFFFFFFL - experience())
            throw new IllegalArgumentException("Experience award out of range");
        state.setExperience(experience() + amount);
    }

    public int equippedItemId(EquipSlot slot) { return state.equipment(slot); }
    public int inventoryItemId(int slot) {
        if (slot < 0 || slot >= DataLayoutV5.PC_INVENTORY.length()) throw new IllegalArgumentException("Invalid native pack slot");
        return state.inventory(slot);
    }
    /** Compare-and-consume the planned potion; a changed slot is never substituted. */
    public boolean consumePotion(int slot, int expectedItemId) {
        var item = ItemTable.get(expectedItemId);
        if (!item.isConsumable()) throw new IllegalArgumentException("Not a potion");
        if (inventoryItemId(slot) != expectedItemId) return false;
        state.setInventory(slot, 0); return true;
    }

    /**
     * This initial runtime seam enforces item category and level.
     * Class restrictions and inventory transfers will be handled in the
     * equipment-rules increment; do not invent a legacy job-bit mapping.
     */
    public void equip(EquipSlot slot, int itemId) {
        Objects.requireNonNull(slot, "slot");
        ItemDefinition item = ItemTable.get(itemId);
        if (!slot.accepts(item.category()))
            throw new IllegalArgumentException("Item does not fit " + slot);
        if (state.level() < item.levelRequirement())
            throw new IllegalArgumentException("Character level too low");
        if (item.jobRestriction() != 0 || item.raceRestriction() != 0)
            throw new IllegalArgumentException("Restricted equipment requires migrated eligibility rules");
        state.setEquipment(slot, itemId);
    }

    public void unequip(EquipSlot slot) { state.setEquipment(slot, 0); }
}
