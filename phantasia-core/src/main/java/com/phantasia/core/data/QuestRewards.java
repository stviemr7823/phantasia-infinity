// phantasia-core/src/main/java/com/phantasia/core/data/QuestRewards.java
package com.phantasia.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rewards granted when a {@link Quest} is completed.
 *
 * All fields are optional — a quest can grant any combination of gold,
 * experience, items, and a flag unlock. Zero/empty means "no reward
 * of that type."
 */
public class QuestRewards {

    private int             gold;
    private int             experience;
    private final List<Integer> itemIds = new ArrayList<>();  // item IDs to grant
    private QuestFlag       unlocksFlag;    // flag set as a reward (nullable)

    public QuestRewards() {}

    public QuestRewards(int gold, int experience) {
        this.gold       = gold;
        this.experience = experience;
    }

    // -------------------------------------------------------------------------
    // Builder-style
    // -------------------------------------------------------------------------

    public QuestRewards gold(int gold)               { this.gold = gold; return this; }
    public QuestRewards experience(int xp)           { this.experience = xp; return this; }
    public QuestRewards addItem(int itemId)           { this.itemIds.add(itemId); return this; }
    public QuestRewards unlocksFlag(QuestFlag flag)  { this.unlocksFlag = flag; return this; }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int            getGold()        { return gold; }
    public int            getExperience()  { return experience; }
    public List<Integer>  getItemIds()     { return Collections.unmodifiableList(itemIds); }
    public QuestFlag      getUnlocksFlag() { return unlocksFlag; }

    public boolean isEmpty() {
        return gold == 0 && experience == 0 && itemIds.isEmpty() && unlocksFlag == null;
    }
}
