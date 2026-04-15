// phantasia-core/src/main/java/com/phantasia/core/model/item/Inventory.java
package com.phantasia.core.model.item;

import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.model.Stat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A rich façade over the eight carried-item slots baked into every
 * PlayerCharacter's 48-byte record (DataLayout.PC_INVENTORY, length 8).
 *
 * The underlying storage is those raw bytes — Inventory adds no new
 * data structure and requires no changes to DataLayout or DataCore.
 * Every read and write goes through the existing
 * PlayerCharacter.getInventoryItemId() / setInventoryItem() API.
 *
 * CAPACITY RULE (faithful to Phantasie III):
 *   Each character may carry at most MAX_ITEMS items in their pack.
 *   Equipped items (weapon, shield, armour, bow) do NOT count against
 *   this limit — they occupy their own dedicated slots.
 *
 * ITEM IDS:
 *   0     = empty slot
 *   1-180 = ItemTable entry
 *
 * ITEM BEHAVIOUR SUMMARY:
 *   Potions (HEALING_POTION, MAGIC_POTION) — consumed on use; slot cleared.
 *   Scrolls (SCROLL)                       — read any number of times; slot
 *                                            is NOT cleared.  The scroll viewer
 *                                            is invoked by the caller using the
 *                                            scrollId() from the returned definition.
 *   Equipment                              — equip() moves item from pack to gear slot.
 *
 * TYPICAL USAGE:
 *   Inventory inv = new Inventory(playerCharacter);
 *
 *   inv.add(itemId)                 // add to first free slot; returns false if full
 *   inv.remove(itemId)              // remove first occurrence; returns false if absent
 *   inv.useConsumable(slot, target) // consume a potion from the given slot
 *   inv.readScroll(slot)            // return the scroll's definition for display
 *   inv.transfer(slotIndex, other)  // hand an item to another character
 *   inv.isFull()                    // true when all 8 slots are occupied
 *   inv.itemIds()                   // snapshot list of non-zero IDs
 */
public class Inventory {

    public static final int MAX_ITEMS = DataLayout.PC_INVENTORY_LEN; // 8

    private final PlayerCharacter owner;

    public Inventory(PlayerCharacter owner) {
        this.owner = owner;
    }

    // -------------------------------------------------------------------------
    // Core slot access
    // -------------------------------------------------------------------------

    /** Returns the item ID in the given slot (0 = empty). */
    public int getItemId(int slot) {
        return owner.getInventoryItemId(slot);
    }

    /** Returns the ItemDefinition for the given slot, or empty if the slot is empty. */
    public Optional<ItemDefinition> getItem(int slot) {
        int id = getItemId(slot);
        if (id == 0) return Optional.empty();
        return Optional.of(ItemTable.get(id));
    }

    // -------------------------------------------------------------------------
    // Adding items
    // -------------------------------------------------------------------------

    /**
     * Adds an item to the first free slot.
     *
     * @return true if the item was added; false if the pack is full
     * @throws IllegalArgumentException if itemId is not in ItemTable
     */
    public boolean add(int itemId) {
        ItemTable.get(itemId); // validates the ID — throws if unknown
        int slot = firstEmptySlot();
        if (slot == -1) return false;
        owner.setInventoryItem(slot, itemId);
        return true;
    }

    // -------------------------------------------------------------------------
    // Removing items
    // -------------------------------------------------------------------------

    /**
     * Removes the item at the given slot.
     *
     * @throws IndexOutOfBoundsException if slot is out of range
     * @throws IllegalStateException     if the slot is already empty
     */
    public void removeAt(int slot) {
        if (getItemId(slot) == 0)
            throw new IllegalStateException("Slot " + slot + " is already empty.");
        owner.clearInventorySlot(slot);
    }

    /**
     * Removes the first occurrence of the given item ID.
     *
     * @return true if found and removed; false if not present
     */
    public boolean remove(int itemId) {
        for (int s = 0; s < MAX_ITEMS; s++) {
            if (getItemId(s) == itemId) {
                owner.clearInventorySlot(s);
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Using consumables (potions only)
    // -------------------------------------------------------------------------

    /**
     * Uses the consumable item in the given slot on the given target.
     *
     * Only HEALING_POTION and MAGIC_POTION are consumable.  Scrolls are NOT
     * consumed on use — call readScroll(slot) instead.
     *
     * Healing Potions restore X² HP to the target.
     * Magic Potions restore 3X MP to the target.
     *
     * The item is removed from the slot after use.
     *
     * @return a human-readable description of what happened
     * @throws IllegalStateException if the slot is empty or the item is not a consumable
     */
    public String useConsumable(int slot, PlayerCharacter target) {
        Optional<ItemDefinition> opt = getItem(slot);
        if (opt.isEmpty())
            throw new IllegalStateException(
                    owner.getName() + " has no item in slot " + slot + ".");

        ItemDefinition item = opt.get();
        if (!item.isConsumable())
            throw new IllegalStateException(
                    item.name() + " is not a consumable."
                            + (item.isReadable() ? " Use readScroll() for scrolls." : ""));

        owner.clearInventorySlot(slot);

        return switch (item.category()) {
            case HEALING_POTION -> {
                int restored = applyHeal(target, item.healingAmount());
                yield owner.getName() + " uses " + item.name()
                        + " on " + target.getName()
                        + " — restores " + restored + " HP.";
            }
            case MAGIC_POTION -> {
                int restored = applyManaRestore(target, item.magicRestoreAmount());
                yield owner.getName() + " uses " + item.name()
                        + " on " + target.getName()
                        + " — restores " + restored + " MP.";
            }
            default -> throw new IllegalStateException(
                    "Unhandled consumable category: " + item.category());
        };
    }

    // -------------------------------------------------------------------------
    // Reading scrolls
    // -------------------------------------------------------------------------

    /**
     * Returns the ItemDefinition for the scroll in the given slot so the
     * caller can invoke the scroll viewer with definition.scrollId().
     *
     * The scroll is NOT removed from the pack — it may be read any number
     * of times.
     *
     * @return the ItemDefinition of the scroll
     * @throws IllegalStateException if the slot is empty or the item is not a scroll
     */
    public ItemDefinition readScroll(int slot) {
        Optional<ItemDefinition> opt = getItem(slot);
        if (opt.isEmpty())
            throw new IllegalStateException(
                    owner.getName() + " has no item in slot " + slot + ".");

        ItemDefinition item = opt.get();
        if (!item.isReadable())
            throw new IllegalStateException(
                    item.name() + " is not a scroll.");

        // Scroll stays in pack — no clearInventorySlot() call here.
        return item;
    }

    // -------------------------------------------------------------------------
    // Equipping from the pack
    // -------------------------------------------------------------------------

    /**
     * Equips the item in the given pack slot, moving any previously equipped
     * item of the same type back into the pack.
     *
     * If the pack is full and there is an item to displace back, the equip
     * is refused to prevent silent item loss.
     *
     * @return a description of what happened
     * @throws IllegalStateException if the item cannot be equipped by this character,
     *                               or if displacing the old item would overflow the pack
     */
    public String equip(int slot) {
        Optional<ItemDefinition> opt = getItem(slot);
        if (opt.isEmpty())
            throw new IllegalStateException("Slot " + slot + " is empty.");

        ItemDefinition item = opt.get();
        if (!item.isEquippable())
            throw new IllegalStateException(item.name() + " cannot be equipped.");

        EquipSlot equipSlot = item.getEquipSlot();

        // Check if we can un-equip the current item back into the pack
        ItemDefinition current = owner.getEquipped(equipSlot);
        if (current != null) {
            // We'll need a free slot to put the old item back
            int freeSlot = firstEmptySlotExcluding(slot); // slot will be freed by equip
            if (freeSlot == -1)
                throw new IllegalStateException(
                        "Pack is full — cannot swap " + current.name()
                                + " back into inventory.");
            owner.setInventoryItem(freeSlot, current.id());
        }

        // Remove the item from the pack slot and equip it
        owner.clearInventorySlot(slot);
        owner.equip(equipSlot, item.id());

        String result = owner.getName() + " equips " + item.name() + ".";
        if (current != null)
            result += " " + current.name() + " returned to pack.";
        return result;
    }

    // -------------------------------------------------------------------------
    // Transferring between characters
    // -------------------------------------------------------------------------

    /**
     * Transfers the item in the given slot to another character's pack.
     *
     * @return a description of what happened
     * @throws IllegalStateException if the slot is empty or the recipient's pack is full
     */
    public String transfer(int slot, PlayerCharacter recipient) {
        Optional<ItemDefinition> opt = getItem(slot);
        if (opt.isEmpty())
            throw new IllegalStateException("Slot " + slot + " is empty.");

        ItemDefinition item = opt.get();
        Inventory recipientInv = new Inventory(recipient);

        if (recipientInv.isFull())
            throw new IllegalStateException(
                    recipient.getName() + "'s pack is full.");

        owner.clearInventorySlot(slot);
        recipientInv.add(item.id());

        return owner.getName() + " gives " + item.name()
                + " to " + recipient.getName() + ".";
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** True if every slot holds an item. */
    public boolean isFull() {
        return firstEmptySlot() == -1;
    }

    /** Number of items currently carried. */
    public int count() {
        int n = 0;
        for (int s = 0; s < MAX_ITEMS; s++)
            if (getItemId(s) != 0) n++;
        return n;
    }

    /** True if the character is carrying at least one item with this ID. */
    public boolean contains(int itemId) {
        for (int s = 0; s < MAX_ITEMS; s++)
            if (getItemId(s) == itemId) return true;
        return false;
    }

    /**
     * Returns a snapshot list of all non-zero item IDs in slot order.
     * Empty slots are omitted.
     */
    public List<Integer> itemIds() {
        List<Integer> ids = new ArrayList<>();
        for (int s = 0; s < MAX_ITEMS; s++) {
            int id = getItemId(s);
            if (id != 0) ids.add(id);
        }
        return ids;
    }

    /**
     * Produces a human-readable multi-line listing of all carried items,
     * suitable for a character sheet or debug output.
     *
     *   Slot 0: Healing Potion 3  [HEALING_POTION]
     *   Slot 1: Sword +1          [WEAPON]
     *   Slot 2: (empty)
     *   ...
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(owner.getName()).append("'s Pack  (")
                .append(count()).append("/").append(MAX_ITEMS).append("):\n");

        for (int s = 0; s < MAX_ITEMS; s++) {
            int id = getItemId(s);
            if (id == 0) {
                sb.append(String.format("  Slot %d: (empty)%n", s));
            } else {
                ItemDefinition item = ItemTable.get(id);
                sb.append(String.format("  Slot %d: %-22s [%s]%n",
                        s, item.name(), item.category()));
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private int firstEmptySlot() {
        for (int s = 0; s < MAX_ITEMS; s++)
            if (getItemId(s) == 0) return s;
        return -1;
    }

    /** Like firstEmptySlot() but skips the specified slot index. */
    private int firstEmptySlotExcluding(int exclude) {
        for (int s = 0; s < MAX_ITEMS; s++)
            if (s != exclude && getItemId(s) == 0) return s;
        return -1;
    }

    private int applyHeal(PlayerCharacter target, int amount) {
        int current = target.getStat(Stat.HP);
        int max     = target.getStat(Stat.MAX_HP);
        int healed  = Math.min(amount, max - current);
        target.setStat(Stat.HP, current + healed);
        return healed;
    }

    private int applyManaRestore(PlayerCharacter target, int amount) {
        int current  = target.getStat(Stat.MAGIC_POWER);
        int max      = target.getStat(Stat.MAX_MAGIC);
        int restored = Math.min(amount, max - current);
        target.setStat(Stat.MAGIC_POWER, current + restored);
        return restored;
    }
}