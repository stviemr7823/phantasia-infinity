// phantasia-core/src/main/java/com/phantasia/core/world/EventOutcome.java
package com.phantasia.core.world;

/**
 * The consequence of a player choice at a TileEvent.
 * An outcome can trigger combat, grant a reward, deal damage,
 * reveal information, or do nothing — or several at once.
 */
public class EventOutcome {

    public enum OutcomeType {
        NOTHING,
        COMBAT,         // Triggers a specific monster encounter
        REWARD,         // Gold, items, XP granted directly
        DAMAGE,         // Party takes direct HP damage (trap, curse)
        REVEAL,         // Unlocks a passage, updates map
        LORE            // Text only — no mechanical effect
    }

    public final OutcomeType    type;
    public final String         monsterName;   // COMBAT only
    public final int            monsterCount;  // COMBAT only
    public final int            goldReward;    // REWARD only
    public final int            xpReward;      // REWARD only
    public final String         message;       // Shown to player regardless

    private EventOutcome(Builder b) {
        this.type         = b.type;
        this.monsterName  = b.monsterName;
        this.monsterCount = b.monsterCount;
        this.goldReward   = b.goldReward;
        this.xpReward     = b.xpReward;
        this.message      = b.message;
    }

    // --- Builder for readable construction ---

    public static Builder combat(String monsterName, int count) {
        return new Builder(OutcomeType.COMBAT)
                .monster(monsterName, count);
    }

    public static Builder reward(int gold, int xp) {
        return new Builder(OutcomeType.REWARD)
                .reward(gold, xp);
    }

    public static Builder nothing() {
        return new Builder(OutcomeType.NOTHING);
    }

    public static class Builder {
        final OutcomeType type;
        String monsterName  = "";
        int    monsterCount = 0;
        int    goldReward   = 0;
        int    xpReward     = 0;
        String message      = "";

        Builder(OutcomeType type) { this.type = type; }

        public Builder monster(String name, int count) {
            this.monsterName  = name;
            this.monsterCount = count;
            return this;
        }

        public Builder reward(int gold, int xp) {
            this.goldReward = gold;
            this.xpReward   = xp;
            return this;
        }

        public Builder message(String msg) {
            this.message = msg;
            return this;
        }

        public EventOutcome build() { return new EventOutcome(this); }
    }
}