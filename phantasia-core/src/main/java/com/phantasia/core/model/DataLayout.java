// phantasia-core/src/main/java/com/phantasia/core/model/DataLayout.java
package com.phantasia.core.model;

/**
 * Binary record layout constants for all 48-byte DataCore records.
 *
 * All offsets are byte indices into the DataCore byte array.
 * Records are exactly 48 bytes (RECORD_SIZE).  The first 15 bytes
 * are always the entity name (shared across all domains).
 *
 * Three domains share the same 48-byte struct:
 *   PLAYER  — PlayerCharacter, offsets 0x0F–0x2C
 *   MONSTER — Monster,         offsets 0x0F–0x17
 *   SPELL   — Spell,           offsets 0x0F–0x15
 *
 * Never cross-read domains (e.g. reading PC_XP on a Monster record).
 * The NOTE on PC_XP / BODY_STATUS documents the one known collision.
 */
public final class DataLayout {

    private DataLayout() {}

    // -------------------------------------------------------------------------
    // SHARED
    // -------------------------------------------------------------------------
    public static final int NAME         = 0x00;   // dec  0 — 15-byte ASCII name
    public static final int NAME_LEN     = 15;
    public static final int BODY_STATUS  = 0x20;   // dec 32 — Monster alive flag (> 0 = alive)
    public static final int DAMAGE_CAP   = 99;

    // -------------------------------------------------------------------------
    // PLAYER domain  (0x0F – 0x2C)
    // -------------------------------------------------------------------------

    // --- Six base stats (1 byte each, unsigned) ---
    public static final int PC_STRENGTH     = 0x0F;  // dec 15
    public static final int PC_INTELLIGENCE = 0x10;  // dec 16
    public static final int PC_DEXTERITY    = 0x11;  // dec 17
    public static final int PC_CONSTITUTION = 0x12;  // dec 18
    public static final int PC_CHARISMA     = 0x13;  // dec 19
    public static final int PC_LUCK         = 0x14;  // dec 20

    // --- Identity (1 byte each) ---
    public static final int PC_JOB          = 0x15;  // dec 21  — Job bitmask (see Job enum)
    public static final int PC_RACE         = 0x16;  // dec 22  — Race ID (see Race enum, TBD)
    public static final int PC_LEVEL        = 0x17;  // dec 23  — 1–20

    /**
     * Sentinel value for PC_RACE indicating the "Undead Curse" state.
     *
     * When a character is raised as undead by the gods' judgment, their Race
     * byte is set to this value.  This reuses the existing PC_RACE field as
     * designed — the Race/Class offset at dec 22 carries undead status per
     * the original DataCore specification.
     *
     * Behaviour while cursed:
     *   - isUndead() returns true.
     *   - isAlive() returns false (undead are not counted as living).
     *   - setStat() is a no-op for all stat-block offsets 0x0F–0x1E
     *     (STR through MAX_MAGIC), enforcing the stat-lock.
     *   - Level is set to 20 (undead are "promoted" but can never advance).
     *   - HP stays at whatever value the character had when they died.
     *   - Cannot benefit from inn rest or standard healing spells.
     *
     * 0xFF (255) is safe to use as a sentinel because valid Race IDs
     * (Race enum ordinals) occupy the range 0–14.
     */
    public static final int PC_RACE_UNDEAD  = 0xFF;  // dec 255 — Undead Curse sentinel

    // --- Social class (1 byte — SocialClass ordinal, 0–3) ---
    public static final int PC_SOCIAL_CLASS = 0x2D;  // dec 45  — PEASANT/LABORER/CRAFTSMAN/NOBLE

    // --- Vitals (16-bit big-endian pairs) ---
    public static final int PC_HP           = 0x18;  // dec 24  — current Hit Points
    public static final int PC_MAX_HP       = 0x1A;  // dec 26  — maximum Hit Points
    public static final int PC_MAGIC_POWER  = 0x1C;  // dec 28  — current Magic Power
    public static final int PC_MAX_MAGIC    = 0x1E;  // dec 30  — maximum Magic Power

    // --- Progression (16-bit big-endian) ---
    public static final int PC_XP           = 0x20;  // dec 32  — Experience points
    // NOTE: PC_XP shares dec-32 with BODY_STATUS only on Monster records.
    //       On Player records offset 32 is XP. Never mix domains.

    // --- Equipment slots (1 byte each — item ID) ---
    public static final int PC_EQUIP_WEAPON  = 0x21;  // dec 33
    public static final int PC_EQUIP_SHIELD  = 0x22;  // dec 34
    public static final int PC_EQUIP_ARMOR   = 0x23;  // dec 35
    public static final int PC_EQUIP_BOW     = 0x24;  // dec 36

    // --- Inventory (8 × 1 byte item IDs) ---
    public static final int PC_INVENTORY     = 0x25;  // dec 37 — 8 bytes: 37–44
    public static final int PC_INVENTORY_LEN = 8;

    // Bytes 45–47 (0x2D–0x2F) are reserved for future use.

    // -------------------------------------------------------------------------
    // MONSTER domain  (0x0F – 0x17)
    // -------------------------------------------------------------------------
    public static final int MON_MAX_SPAWN   = 0x0F;  // dec 15
    public static final int MON_HP          = 0x10;  // dec 16  — single byte, 0–255
    public static final int MON_ITEM_1      = 0x11;  // dec 17
    public static final int MON_ITEM_2      = 0x12;  // dec 18
    public static final int MON_FLAGS       = 0x13;  // dec 19
    public static final int MON_TREASURE    = 0x14;  // dec 20  — 16-bit big-endian
    public static final int MON_XP          = 0x16;  // dec 22  — 16-bit big-endian

    // Flag masks for MON_FLAGS
    public static final int MON_FLAG_UNDEAD    = 0x01;
    public static final int MON_FLAG_AMORPHOUS = 0x02;

    // -------------------------------------------------------------------------
    // SPELL domain  (0x0F – 0x15)
    // -------------------------------------------------------------------------
    public static final int SPELL_MP_COST      = 0x0F;  // dec 15
    public static final int SPELL_POWER        = 0x10;  // dec 16
    public static final int SPELL_TARGET       = 0x11;  // dec 17
    public static final int SPELL_TYPE         = 0x12;  // dec 18
    public static final int SPELL_LEVEL_REQ    = 0x13;  // dec 19
    public static final int SPELL_EFFECT_TYPE  = 0x14;  // dec 20
    public static final int SPELL_ID           = 0x15;  // dec 21

    // -------------------------------------------------------------------------
    // Stat-lock range for the Undead Curse
    // -------------------------------------------------------------------------

    /**
     * First DataCore offset subject to the undead stat-lock (inclusive).
     * Maps to PC_STRENGTH (index 0 of the stat block, offset 0x0F).
     */
    public static final int PC_STAT_LOCK_START = 0x0F;  // dec 15

    /**
     * Last DataCore offset subject to the undead stat-lock (inclusive).
     * Maps to the high byte of PC_MAX_MAGIC (offset 0x1F, end of index 16).
     */
    public static final int PC_STAT_LOCK_END   = 0x1F;  // dec 31

    // -------------------------------------------------------------------------
    // Record size
    // -------------------------------------------------------------------------
    public static final int RECORD_SIZE = 48;
}