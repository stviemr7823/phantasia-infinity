// phantasia-core/src/main/java/com/phantasia/core/logic/ExperienceTable.java
package com.phantasia.core.logic;

import com.phantasia.core.model.Job;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.model.Stat;

/**
 * Experience thresholds, level-up progression, and guild training costs.
 *
 * Faithful to Phantasie III's progression model:
 *   - Each Job has its own XP curve.
 *   - Level cap is 20 (LEVEL_MAX), matching the original.
 *   - On level-up: LEVEL, HP, and MP grow.  Base stats (STR/INT/DEX/CON/CHA/LUCK)
 *     NEVER change after character creation — they are fixed at roll time.
 *   - HP growth is job-dependent with dice variance.
 *   - MP growth only applies to spellcasting jobs.
 *
 * GUILD TRAINING:
 *   Characters may train at the guild once they have enough XP to qualify.
 *   Training costs gold; the amount is determined by:
 *     - The character's target level
 *     - Their Charisma stat (high CHA = lower cost)
 *     - Their Race (random creatures pay a hefty multiplier)
 *   Formula: (4 * targetLevel * max(1, 20 - cha) * race.trainingMult) / 3
 *
 *   On successful training:
 *     - Gold is deducted via PartyLedger.spendGold()
 *     - applyLevelUp() is called (LEVEL/HP/MP increase)
 *     - The character receives gold equal to their SocialClass.goldPerLevel
 *     - GameEvent.PlayerLeveledUp is fired by the caller
 */
public final class ExperienceTable {

    private ExperienceTable() {}

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    public static final int LEVEL_MAX = 20;

    // -------------------------------------------------------------------------
    // XP thresholds — total XP required to REACH each level.
    //
    // Index 0 is unused (level 0 doesn't exist).
    // Index N = total XP needed to be at level N.
    //
    // Job ordinals: WIZARD=0, PRIEST=1, MONK=2, RANGER=3, FIGHTER=4, THIEF=5
    // -------------------------------------------------------------------------

    private static final int[][] THRESHOLDS = buildThresholds();

    private static int[][] buildThresholds() {
        final int LEVELS = LEVEL_MAX + 1;

        double[][] multipliers = {
                buildCurve(200,  1.40, LEVELS),  // WIZARD   — fastest leveller
                buildCurve(300,  1.45, LEVELS),  // PRIEST
                buildCurve(400,  1.50, LEVELS),  // MONK
                buildCurve(450,  1.52, LEVELS),  // RANGER
                buildCurve(600,  1.55, LEVELS),  // FIGHTER  — slowest leveller
                buildCurve(150,  1.35, LEVELS),  // THIEF
        };

        int[][] table = new int[Job.values().length][LEVELS];
        for (int j = 0; j < Job.values().length; j++) {
            table[j][0] = 0;
            table[j][1] = 0;
            int cumulative = 0;
            for (int lvl = 2; lvl < LEVELS; lvl++) {
                cumulative += (int) multipliers[j][lvl];
                table[j][lvl] = cumulative;
            }
        }
        return table;
    }

    private static double[] buildCurve(int base, double mult, int levels) {
        double[] gaps = new double[levels];
        for (int lvl = 2; lvl < levels; lvl++) {
            gaps[lvl] = base * Math.pow(mult, lvl - 2);
        }
        return gaps;
    }

    // -------------------------------------------------------------------------
    // Public API — XP queries
    // -------------------------------------------------------------------------

    public static int xpRequiredForLevel(Job job, int level) {
        if (level <= 1)      return 0;
        if (level > LEVEL_MAX) return Integer.MAX_VALUE;
        return THRESHOLDS[job.ordinal()][level];
    }

    public static int xpToNextLevel(PlayerCharacter pc) {
        int currentLevel = pc.getStat(Stat.LEVEL);
        if (currentLevel >= LEVEL_MAX) return 0;
        Job job    = Job.fromValue(pc.getJob());
        int needed = xpRequiredForLevel(job, currentLevel + 1);
        int current = pc.getStat(Stat.XP);
        return Math.max(0, needed - current);
    }

    public static boolean isReadyToLevelUp(PlayerCharacter pc) {
        return xpToNextLevel(pc) == 0 && pc.getStat(Stat.LEVEL) < LEVEL_MAX;
    }

    // -------------------------------------------------------------------------
    // Public API — XP award (combat)
    // -------------------------------------------------------------------------

    /**
     * Awards XP to a single character.
     * Does NOT trigger level-up — in Phantasie III, levelling up requires
     * explicitly visiting the guild and paying the training cost.
     * Returns the new XP total.
     */
    public static int awardXp(PlayerCharacter pc, int xpEarned) {
        if (xpEarned <= 0 || pc.isUndead()) return pc.getStat(Stat.XP);

        Job job    = Job.fromValue(pc.getJob());
        int capXp  = xpRequiredForLevel(job, LEVEL_MAX);
        int newXp  = Math.min(pc.getStat(Stat.XP) + xpEarned, capXp);
        pc.setStat(Stat.XP, newXp);
        return newXp;
    }

    /**
     * Awards a share of a combat XP pool to every surviving party member.
     * XP is split evenly; dead and undead members receive no XP.
     * Returns a summary string for the narrator/HUD.
     */
    public static String awardCombatXp(java.util.List<PlayerCharacter> party,
                                       int totalXp) {
        long survivors = party.stream().filter(PlayerCharacter::isAlive).count();
        if (survivors == 0 || totalXp <= 0) return "No experience awarded.";

        int share = totalXp / (int) survivors;
        if (share == 0) return "Experience shared but too little to register.";

        StringBuilder sb = new StringBuilder();
        for (PlayerCharacter pc : party) {
            if (!pc.isAlive()) continue;
            int prev = pc.getStat(Stat.XP);
            awardXp(pc, share);
            sb.append(pc.getName()).append(" gains ").append(share).append(" XP");
            if (isReadyToLevelUp(pc)) sb.append(" — ready to train!");
            sb.append("  ");
        }
        return sb.toString().trim();
    }

    // -------------------------------------------------------------------------
    // Public API — Guild training
    // -------------------------------------------------------------------------

    /**
     * Calculates the gold cost to train a character to their next level.
     *
     * Formula: (4 * targetLevel * max(1, 20 - cha) * race.trainingMult) / 3
     *
     * Returns Integer.MAX_VALUE if the character is already at LEVEL_MAX
     * or is undead (undead cannot train).
     *
     * Verified against original game data:
     *   Human Ranger level 2→3 with CHA 14 = 24 gp
     *   Ogre  Fighter level 2→3 with CHA  9 = 1056 gp
     */
    public static int guildTrainingCost(PlayerCharacter pc) {
        if (pc.isUndead()) return Integer.MAX_VALUE;
        int level = pc.getStat(Stat.LEVEL);
        if (level >= LEVEL_MAX) return Integer.MAX_VALUE;
        int targetLevel = level + 1;
        int cha         = pc.getStat(Stat.CHARISMA);
        return pc.getRaceEnum().trainingCost(targetLevel, cha);
    }

    /**
     * Applies one level-up to the character (called by GuildTrainer after
     * gold is deducted and XP requirement is confirmed).
     *
     * Increments LEVEL and grows HP/MP according to job tables.
     * Base stats (STR/INT/DEX/CON/CHA/LUCK) are NEVER modified here —
     * they are fixed at character creation and do not change.
     *
     * @return the HP gained this level (for display in the training result)
     */
    public static int applyLevelUp(PlayerCharacter pc) {
        int newLevel = pc.getStat(Stat.LEVEL) + 1;
        pc.setStat(Stat.LEVEL, newLevel);

        Job job = Job.fromValue(pc.getJob());

        // --- HP growth ---
        int hpGain   = rollHpGain(job);
        int newMaxHp = pc.getStat(Stat.MAX_HP) + hpGain;
        pc.setStat(Stat.MAX_HP, newMaxHp);
        // Heal up by the same amount gained (reward for levelling)
        pc.setStat(Stat.HP, Math.min(pc.getStat(Stat.HP) + hpGain, newMaxHp));

        // --- MP growth (spellcasters only) ---
        int mpGain = rollMpGain(job);
        if (mpGain > 0) {
            int newMaxMp = pc.getStat(Stat.MAX_MAGIC) + mpGain;
            pc.setStat(Stat.MAX_MAGIC, newMaxMp);
            pc.setStat(Stat.MAGIC_POWER,
                    Math.min(pc.getStat(Stat.MAGIC_POWER) + mpGain, newMaxMp));
        }

        // NOTE: Base stats (STR/INT/DEX/CON/CHA/LUCK) do NOT change on
        // level-up.  They are fixed at character creation per Phantasie III rules.

        return hpGain;
    }

    // -------------------------------------------------------------------------
    // HP and MP growth tables
    // -------------------------------------------------------------------------

    private static int rollHpGain(Job job) {
        return switch (job) {
            case FIGHTER -> Dice.d8()  + 4;
            case MONK    -> Dice.d8()  + 2;
            case RANGER  -> Dice.d6()  + 2;
            case PRIEST  -> Dice.d6()  + 1;
            case THIEF   -> Dice.d6();
            case WIZARD  -> Dice.d4();
        };
    }

    private static int rollMpGain(Job job) {
        return switch (job) {
            case WIZARD         -> Dice.d6() + 3;
            case PRIEST         -> Dice.d6() + 2;
            case MONK           -> Dice.d4();
            case RANGER         -> Dice.d4();
            case FIGHTER, THIEF -> 0;
        };
    }
}