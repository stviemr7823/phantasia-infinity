// phantasia-core/src/main/java/com/phantasia/core/data/town/GuildService.java
package com.phantasia.core.data.town;

import com.phantasia.core.logic.ExperienceTable;
import com.phantasia.core.model.PlayerCharacter;

/**
 * A guild's training service — allows a qualifying character to advance
 * one level in exchange for gold.
 *
 * In Phantasie III, every town's guild offers the same core service:
 * "train to next level." The only authored variation between towns is
 * whether the guild is available at all (some towns may not have one).
 * The actual gold cost is computed dynamically from the character's
 * Charisma and Race, so there is nothing per-town to store here beyond
 * a flag indicating the service exists.
 *
 * TRAINING FLOW:
 *   1. Player selects a party member to train.
 *   2. GuildService.canTrain(pc) checks XP threshold + not undead + not at cap.
 *   3. GuildService.trainingCost(pc) shows the gold cost.
 *   4. Player confirms; PartyLedger.spendGold(cost) deducts the gold.
 *   5. ExperienceTable.applyLevelUp(pc) is called — LEVEL/HP/MP increase.
 *   6. Character receives SocialClass.goldPerLevel (social class bonus gold).
 *   7. GameEvent.PlayerLeveledUp is fired.
 *
 * COST FORMULA:
 *   (4 * targetLevel * max(1, 20 - cha) * race.trainingMult) / 3
 *
 *   This is a per-character calculation — the guild doesn't set prices,
 *   the character's attributes determine what they pay.
 */
public final class GuildService {

    /** Singleton — every town's guild offers the same service. */
    public static final GuildService INSTANCE = new GuildService();

    private GuildService() {}

    // -------------------------------------------------------------------------
    // API
    // -------------------------------------------------------------------------

    /**
     * Returns true if this character is eligible to train right now.
     *
     * Requirements:
     *   - Not undead (undead are stuck at level 20 and cannot advance).
     *   - Has accumulated enough XP to reach the next level.
     *   - Not already at LEVEL_MAX (20).
     */
    public boolean canTrain(PlayerCharacter pc) {
        return !pc.isUndead() && ExperienceTable.isReadyToLevelUp(pc);
    }

    /**
     * Returns the gold cost to train this character to their next level.
     * Returns Integer.MAX_VALUE if the character cannot train.
     */
    public int trainingCost(PlayerCharacter pc) {
        return ExperienceTable.guildTrainingCost(pc);
    }

    /**
     * Applies training: advances the character one level.
     *
     * PRECONDITION: caller must have already verified canTrain() and
     * deducted the gold via PartyLedger.spendGold().  This method does
     * not touch the ledger — that separation keeps the service testable
     * without a live session.
     *
     * @return a TrainingResult describing what changed (for display)
     */
    public TrainingResult train(PlayerCharacter pc) {
        if (!canTrain(pc)) {
            throw new IllegalStateException(
                    pc.getName() + " is not eligible for training.");
        }

        int oldLevel = pc.getStat(com.phantasia.core.model.Stat.LEVEL);
        int hpGained = ExperienceTable.applyLevelUp(pc);
        int newLevel = pc.getStat(com.phantasia.core.model.Stat.LEVEL);

        // Social class bonus gold — awarded directly to carried gold
        int bonusGold = pc.getSocialClass().goldPerLevel;

        return new TrainingResult(pc, oldLevel, newLevel, hpGained, bonusGold);
    }

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    /**
     * Describes the outcome of a training session for display in the UI.
     */
    public record TrainingResult(
            PlayerCharacter character,
            int             oldLevel,
            int             newLevel,
            int             hpGained,
            int             bonusGold
    ) {
        public String describe() {
            return character.getName()
                    + " advances to level " + newLevel + "!"
                    + "  +" + hpGained + " HP"
                    + (bonusGold > 0 ? "  +" + bonusGold + " gp" : "");
        }
    }
}