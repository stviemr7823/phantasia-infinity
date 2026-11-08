package com.phantasia.core.model.item;

import com.phantasia.core.data.LocalSession;
import java.util.Objects;

/** Storage-neutral carried-slot facade for authored-area services. */
public final class LocalInventory {
    private final LocalSession.Member owner;
    public LocalInventory(LocalSession.Member owner) { this.owner = Objects.requireNonNull(owner); }
    public int getItemId(int slot) { return owner.getInventoryItemId(slot); }
    public boolean add(int itemId) {
        ItemTable.get(itemId);
        if (itemId == 0) throw new IllegalArgumentException("Cannot add an empty item");
        for (int i = 0; i < owner.inventorySlots(); i++) if (getItemId(i) == 0) {
            owner.setInventoryItem(i, itemId); return true;
        }
        return false;
    }
    public void removeAt(int slot) { owner.setInventoryItem(slot, 0); }
}
