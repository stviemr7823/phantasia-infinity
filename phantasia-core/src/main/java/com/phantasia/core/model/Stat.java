// phantasia-core/src/main/java/com/phantasia/core/model/Stat.java
package com.phantasia.core.model;

/**
 * Typed stat accessors for PlayerCharacter — ground-truthed against
 * the Phantasie III character sheet.
 *
 * Base stats:    STRENGTH, INTELLIGENCE, DEXTERITY, CONSTITUTION, CHARISMA, LUCK
 * Vitals:        HP, MAX_HP, MAGIC_POWER, MAX_MAGIC
 * Identity:      LEVEL
 * Progression:   XP
 *
 * SPEED does NOT exist in Phantasie III and has been removed entirely.
 * Initiative order in combat is determined by Dexterity + a random roll.
 */
public enum Stat {

    // --- Six base stats (8-bit) ---
    STRENGTH     (DataLayout.PC_STRENGTH,     false),
    INTELLIGENCE (DataLayout.PC_INTELLIGENCE, false),
    DEXTERITY    (DataLayout.PC_DEXTERITY,    false),
    CONSTITUTION (DataLayout.PC_CONSTITUTION, false),
    CHARISMA     (DataLayout.PC_CHARISMA,     false),
    LUCK         (DataLayout.PC_LUCK,         false),

    // --- Identity (8-bit) ---
    LEVEL        (DataLayout.PC_LEVEL,        false),

    // --- Vitals (16-bit big-endian) ---
    HP           (DataLayout.PC_HP,           true),
    MAX_HP       (DataLayout.PC_MAX_HP,       true),
    MAGIC_POWER  (DataLayout.PC_MAGIC_POWER,  true),
    MAX_MAGIC    (DataLayout.PC_MAX_MAGIC,    true),

    // --- Progression (16-bit big-endian) ---
    XP           (DataLayout.PC_XP,           true);

    public final int offset;
    public final boolean wide;   // true = 16-bit big-endian read/write

    Stat(int offset, boolean wide) {
        this.offset = offset;
        this.wide   = wide;
    }
}