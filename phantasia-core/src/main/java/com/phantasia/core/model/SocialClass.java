// phantasia-core/src/main/java/com/phantasia/core/model/SocialClass.java
package com.phantasia.core.model;

/**
 * A character's social class — assigned at creation, never changes.
 *
 * Social class determines:
 *   1. Starting gold (deposited into the bank at character creation).
 *   2. Gold awarded each time the character gains a level via training.
 *
 * From the Phantasie III manual:
 *   "The character will also receive an amount of Gold that depends on
 *    his social class. Humans, Elves and Dwarves tend to be in the
 *    higher classes."
 *
 * Random creatures are almost always Peasants, which compounds their
 * disadvantage: they pay more to train AND receive less gold for doing so.
 *
 * BINARY STORAGE:
 *   Stored as 2 bits packed into a spare byte alongside other character
 *   flags.  Currently stored at DataLayout.PC_FLAGS (byte 45) bits 2–3,
 *   leaving bits 0–1 free for future flags.
 */
public enum SocialClass {

    //           displayName    startingGold  goldPerLevel
    PEASANT   ("Peasant",       50,            25),
    LABORER   ("Laborer",       128,           50),
    CRAFTSMAN ("Craftsman",     256,          100),
    NOBLE     ("Noble",         512,          200);

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    public final String displayName;

    /** Gold deposited into the character's bank account at creation. */
    public final int startingGold;

    /** Gold received (into carried gold) each time a level is gained. */
    public final int goldPerLevel;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    SocialClass(String displayName, int startingGold, int goldPerLevel) {
        this.displayName  = displayName;
        this.startingGold = startingGold;
        this.goldPerLevel = goldPerLevel;
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    public static SocialClass fromOrdinal(int value) {
        if (value >= 0 && value < values().length) return values()[value];
        return PEASANT;
    }

    @Override
    public String toString() { return displayName; }
}