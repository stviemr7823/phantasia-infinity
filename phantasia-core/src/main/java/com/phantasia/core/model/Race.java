// phantasia-core/src/main/java/com/phantasia/core/model/Race.java
package com.phantasia.core.model;

/**
 * The fifteen playable races of Phantasie III: The Wrath of Nikademus.
 *
 * STANDARD RACES (selectable directly):
 *   Human, Dwarf, Elf, Gnome, Halfling
 *
 * RANDOM RACES (rolled when player selects "Random Creature"):
 *   Gnoll, Goblin, Kobold, Lizard Man, Minotaur, Ogre, Orc, Pixie, Sprite, Troll
 *
 * STAT MAXIMUMS:
 *   These are the hard ceilings for each stat by race, faithful to the
 *   original Phantasie III manual.  Starting values are randomly assigned
 *   in the range [3, maxStat] and never change during play.
 *
 * TRAINING COST MULTIPLIER:
 *   Training gold cost = (4 * targetLevel * (20 - cha) * trainingMult) / 3
 *
 *   Derived from the original game's known data points:
 *     Human Ranger level 2→3 with CHA 14:  24 gp  (mult=1)
 *     Ogre  Fighter level 2→3 with CHA  9: 1056 gp (mult=24)
 *
 *   Random creatures pay "hefty training fees" as stated in the manual,
 *   primarily because their low CHA maximums force low rolls AND the
 *   multiplier compounds the penalty.
 *
 * SOCIAL CLASS TENDENCY:
 *   Each race has a default social class that determines starting gold
 *   and gold awarded on level-up.  The actual class is randomly assigned
 *   at character creation with a bias toward the race's tendency.
 *   Humans, Elves, and Dwarves tend toward higher classes.
 *   Random creatures are typically Peasants.
 *
 * BINARY STORAGE:
 *   Stored as a single byte (ordinal) at DataLayout.PC_RACE.
 *   The sentinel value 0xFF (DataLayout.PC_RACE_UNDEAD) overrides this
 *   to indicate the Undead Curse — see PlayerCharacter.isUndead().
 */
public enum Race {

    // -------------------------------------------------------------------------
    // Standard races
    // -------------------------------------------------------------------------

    //               strMax intMax dexMax conMax chaMax  trainMult  isRandom  socialTendency
    HUMAN    (18, 18, 18, 18, 18,  1, false, SocialClass.CRAFTSMAN),
    DWARF    (20, 17, 17, 19, 17,  1, false, SocialClass.CRAFTSMAN),
    ELF      (17, 19, 19, 17, 18,  1, false, SocialClass.CRAFTSMAN),
    GNOME    (19, 17, 18, 19, 17,  1, false, SocialClass.LABORER),
    HALFLING (16, 18, 20, 18, 17,  1, false, SocialClass.LABORER),

    // -------------------------------------------------------------------------
    // Random races — only obtainable by selecting "Random Creature"
    // -------------------------------------------------------------------------

    GNOLL    (20, 13, 17, 21, 11, 12, true,  SocialClass.PEASANT),
    GOBLIN   (17, 14, 18, 17, 11, 12, true,  SocialClass.PEASANT),
    KOBOLD   (17, 15, 19, 19, 13,  8, true,  SocialClass.PEASANT),
    LIZARD   (19, 14, 17, 18, 13, 12, true,  SocialClass.PEASANT),
    MINOTAUR (20, 14, 16, 19, 12, 16, true,  SocialClass.PEASANT),
    OGRE     (21, 14, 16, 19, 13, 24, true,  SocialClass.PEASANT),
    ORC      (19, 16, 17, 18, 14,  8, true,  SocialClass.PEASANT),
    PIXIE    (16, 18, 21, 16, 17,  4, true,  SocialClass.LABORER),
    SPRITE   (16, 18, 22, 16, 17,  4, true,  SocialClass.LABORER),
    TROLL    (22, 13, 15, 20, 12, 20, true,  SocialClass.PEASANT);

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Maximum value this race can reach for each stat (range 3–22). */
    public final int maxStr;
    public final int maxInt;
    public final int maxDex;
    public final int maxCon;
    public final int maxCha;

    /**
     * Multiplier used in the training cost formula.
     * Standard races = 1.  Random creatures = 4–24.
     * Formula: cost = (4 * targetLevel * (20 - cha) * trainingMult) / 3
     */
    public final int trainingMult;

    /** True if this race is only obtainable via the "Random Creature" option. */
    public final boolean isRandom;

    /** The social class this race tends toward at character creation. */
    public final SocialClass socialTendency;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    Race(int maxStr, int maxInt, int maxDex, int maxCon, int maxCha,
         int trainingMult, boolean isRandom, SocialClass socialTendency) {
        this.maxStr         = maxStr;
        this.maxInt         = maxInt;
        this.maxDex         = maxDex;
        this.maxCon         = maxCon;
        this.maxCha         = maxCha;
        this.trainingMult   = trainingMult;
        this.isRandom       = isRandom;
        this.socialTendency = socialTendency;
    }

    // -------------------------------------------------------------------------
    // Training cost
    // -------------------------------------------------------------------------

    /**
     * Calculates the gold cost to train a character to {@code targetLevel}.
     *
     * Formula: (4 * targetLevel * max(1, 20 - cha) * trainingMult) / 3
     *
     * Floored to a minimum of 1 gp.  The (20 - cha) term ensures that
     * high-charisma characters pay significantly less — CHA 18 costs
     * only 1/6 what CHA 8 does for the same race.
     *
     * @param targetLevel  the level being trained into (current level + 1)
     * @param cha          the character's current Charisma stat
     */
    public int trainingCost(int targetLevel, int cha) {
        int chaFactor = Math.max(1, 20 - cha);
        return Math.max(1, (4 * targetLevel * chaFactor * trainingMult) / 3);
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Returns the Race for the given DataCore PC_RACE byte value.
     * Returns HUMAN as a safe default if the value is unrecognised
     * (including 0xFF which is the Undead Curse sentinel — callers
     * should check PlayerCharacter.isUndead() before calling this).
     */
    public static Race fromOrdinal(int value) {
        if (value >= 0 && value < values().length) return values()[value];
        return HUMAN;
    }

    /**
     * Returns all random creature races — used by the character creation
     * screen when the player selects "Random Creature".
     */
    public static Race[] randomCreatures() {
        return new Race[]{ GNOLL, GOBLIN, KOBOLD, LIZARD, MINOTAUR,
                OGRE, ORC, PIXIE, SPRITE, TROLL };
    }

    /** Display name shown on the character sheet. */
    public String displayName() {
        return switch (this) {
            case LIZARD -> "Lizard Man";
            default     -> name().charAt(0) + name().substring(1).toLowerCase();
        };
    }
}