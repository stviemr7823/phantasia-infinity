package com.phantasia.core.data;

import com.phantasia.core.world.WorldPosition;
import java.util.List;

/** Authored-area operations independent of player storage version. */
public interface LocalSession {
    interface Member {
        String getName();
        int getHp();
        int getMaxHp();
        void setHp(int hp);
        boolean isAlive();
        boolean isActiveInParty();
        int getAgility();
        int getLevel();
        int getJob();
        int inventorySlots();
        int getInventoryItemId(int slot);
        void setInventoryItem(int slot, int itemId);
    }
    List<? extends Member> localMembers();
    int getPartyGold();
    void addGold(int gold);
    boolean spendGold(int gold);
    boolean hasFlag(QuestFlag flag);
    void setFlag(QuestFlag flag);
    WorldPosition getWorldPosition();
    void setPosition(WorldPosition position);
    LocalInteractionState getLocalInteractions();
}
