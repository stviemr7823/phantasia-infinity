// phantasia-core/src/main/java/com/phantasia/core/model/DataLayout.java
package com.phantasia.core.model;

/**
 * Binary record layout constants for all 64-byte DataCore records.
 *
 * <h3>Design rationale — 64-byte expansion</h3>
 * The record size was increased from 48 to 64 bytes in Save v4.
 * 64 bytes aligns with a standard CPU cache line, ensuring the entire
 * character state is fetched in a single memory access. The additional
 * 16 bytes (0x30–0x3F) provide expansion space without breaking the
 * existing binary layout — all offsets 0x00–0x2F are identical to v3.
 *
 * <h3>Domains</h3>
 * Three domains share the same 64-byte struct:
 * <ul>
 *   <li>PLAYER  — PlayerCharacter, offsets 0x0F–0x3F</li>
 *   <li>MONSTER — Monster,         offsets 0x0F–0x17</li>
 *   <li>SPELL   — Spell,           offsets 0x0F–0x15</li>
 * </ul>
 * Never cross-read domains. The NOTE on PC_XP / BODY_STATUS documents
 * the one known offset collision between PLAYER and MONSTER domains.
 *
 * <h3>Expansion zone — 0x30–0x3F</h3>
 * Bytes 48–63 are new in v4. Old saves (v1–v3) are zero-padded here
 * on load. Field definitions in this zone are additive — no existing
 * code breaks if these offsets read as zero.
 */
public final class DataLayout {

    private DataLayout() {}

    // =========================================================================
    // SHARED  (all domains)
    // =========================================================================

    /** Offset of the 15-byte ASCII name field. */
    public static final int NAME         = 0x00;   // dec  0
    /** Length of the name field in bytes. */
    public static final int NAME_LEN     = 15;

    /**
     * Shared status byte — semantics differ by domain.
     * PLAYER: not used at this offset.
     * MONSTER: alive flag (> 0 = alive).
     * Never read BODY_STATUS on a Player record.
     */
    public static final int BODY_STATUS  = 0x20;   // dec 32

    /** Maximum damage that can be dealt in a single hit. */
    public static final int DAMAGE_CAP   = 99;

    // =========================================================================
    // PLAYER domain  (0x0F – 0x3F)
    // =========================================================================

    // ── Six base stats (1 byte each, unsigned 0–255) ─────────────────────────

    public static final int PC_STRENGTH     = 0x0F;  // dec 15
    public static final int PC_INTELLIGENCE = 0x10;  // dec 16
    public static final int PC_DEXTERITY    = 0x11;  // dec 17
    public static final int PC_CONSTITUTION = 0x12;  // dec 18
    public static final int PC_CHARISMA     = 0x13;  // dec 19
    public static final int PC_LUCK         = 0x14;  // dec 20

    // ── Identity (1 byte each) ────────────────────────────────────────────────

    public static final int PC_JOB          = 0x15;  // dec 21  — Job bitmask (see Job enum)
    public static final int PC_RACE         = 0x16;  // dec 22  — Race ID (see Race enum)
    public static final int PC_LEVEL        = 0x17;  // dec 23  — 1–20

    /**
     * Sentinel written to PC_RACE when the Undead Curse is applied.
     *
     * <p>While this value is present:
     * <ul>
     *   <li>{@code isUndead()} returns true.</li>
     *   <li>{@code isAlive()} returns false.</li>
     *   <li>{@code setStat()} is a no-op for offsets 0x0F–0x1F (stat-lock range).</li>
     *   <li>The character cannot be healed by normal means.</li>
     * </ul>
     * 0xFF is safe as a sentinel because valid Race IDs occupy 0–14.
     */
    public static final int PC_RACE_UNDEAD  = 0xFF;  // dec 255

    // ── Social standing ───────────────────────────────────────────────────────

    /** SocialClass ordinal (PEASANT/LABORER/CRAFTSMAN/NOBLE). */
    public static final int PC_SOCIAL_CLASS = 0x2D;  // dec 45

    // ── Vitals (16-bit big-endian pairs) ─────────────────────────────────────

    public static final int PC_HP           = 0x18;  // dec 24  — current Hit Points
    public static final int PC_MAX_HP       = 0x1A;  // dec 26  — maximum Hit Points
    public static final int PC_MAGIC_POWER  = 0x1C;  // dec 28  — current Magic Power
    public static final int PC_MAX_MAGIC    = 0x1E;  // dec 30  — maximum Magic Power

    // ── Progression (16-bit big-endian) ──────────────────────────────────────

    /**
     * Experience points (16-bit, max 65535).
     *
     * NOTE: shares offset 0x20 (dec 32) with {@link #BODY_STATUS}.
     * On Player records offset 32 is XP. On Monster records it is the
     * alive flag. Never mix domains at this offset.
     */
    public static final int PC_XP           = 0x20;  // dec 32

    // ── Equipment slots (1 byte each — item ID) ───────────────────────────────

    public static final int PC_EQUIP_WEAPON  = 0x21;  // dec 33
    public static final int PC_EQUIP_SHIELD  = 0x22;  // dec 34
    public static final int PC_EQUIP_ARMOR   = 0x23;  // dec 35
    public static final int PC_EQUIP_BOW     = 0x24;  // dec 36

    // ── Inventory (8 × 1-byte item IDs) ──────────────────────────────────────

    /** Start of the primary inventory block. */
    public static final int PC_INVENTORY     = 0x25;  // dec 37  — 8 bytes: 37–44
    /** Number of primary inventory slots. */
    public static final int PC_INVENTORY_LEN = 8;

    // Bytes 0x2E–0x2F (46–47) remain reserved (were reserved in v3).

    // =========================================================================
    // PLAYER EXPANSION ZONE  (0x30–0x3F) — new in v4
    //
    // All bytes in this zone read as 0 on records loaded from v1–v3 saves.
    // Field definitions are additive — reading 0 is a safe default for all.
    // =========================================================================

    /**
     * Portrait sprite index (0–31).
     * Maps to a portrait sheet row used by DialogueScreen and character UI.
     * 0 = default portrait for the character's job class.
     */
    public static final int PC_PORTRAIT_ID   = 0x30;  // dec 48

    /**
     * Personality trait bitfield.
     * Reserved for the narrative personality system (future).
     * All bits 0 = no special traits (safe default).
     */
    public static final int PC_TRAITS        = 0x31;  // dec 49

    /**
     * Resonance stat — 16-bit big-endian.
     * Reserved for the resonance/affinity system described in the design doc.
     * Reads as 0 until the system is implemented (safe default).
     */
    public static final int PC_RESONANCE     = 0x32;  // dec 50  — 2 bytes: 50–51

    /**
     * Extended inventory — 9th item slot.
     * Expands carrying capacity from 8 to 10 when {@code PC_EXTENDED_INV_LEN > 0}.
     * 0 = empty slot.
     */
    public static final int PC_EXTENDED_INV  = 0x34;  // dec 52  — 2 bytes: 52–53
    /** Number of extended inventory slots (currently 2). */
    public static final int PC_EXTENDED_INV_LEN = 2;

    // Bytes 0x36–0x3F (54–63) — reserved expansion space (10 bytes, always 0).

    // =========================================================================
    // MONSTER domain  (0x0F – 0x17)
    // =========================================================================

    public static final int MON_MAX_SPAWN   = 0x0F;  // dec 15
    public static final int MON_HP          = 0x10;  // dec 16  — single byte, 0–255
    public static final int MON_ITEM_1      = 0x11;  // dec 17
    public static final int MON_ITEM_2      = 0x12;  // dec 18
    public static final int MON_FLAGS       = 0x13;  // dec 19
    public static final int MON_TREASURE    = 0x14;  // dec 20  — 16-bit big-endian
    public static final int MON_XP          = 0x16;  // dec 22  — 16-bit big-endian

    /** Flag mask: this monster is undead (immune to some damage types). */
    public static final int MON_FLAG_UNDEAD    = 0x01;
    /** Flag mask: this monster is amorphous (immune to slash/pierce). */
    public static final int MON_FLAG_AMORPHOUS = 0x02;

    // =========================================================================
    // SPELL domain  (0x0F – 0x15)
    // =========================================================================

    public static final int SPELL_MP_COST      = 0x0F;  // dec 15
    public static final int SPELL_POWER        = 0x10;  // dec 16
    public static final int SPELL_TARGET       = 0x11;  // dec 17
    public static final int SPELL_TYPE         = 0x12;  // dec 18
    public static final int SPELL_LEVEL_REQ    = 0x13;  // dec 19
    public static final int SPELL_EFFECT_TYPE  = 0x14;  // dec 20
    public static final int SPELL_ID           = 0x15;  // dec 21

    // =========================================================================
    // Undead stat-lock range
    // =========================================================================

    /**
     * First offset subject to the undead stat-lock (inclusive).
     * Maps to PC_STRENGTH (0x0F, dec 15).
     */
    public static final int PC_STAT_LOCK_START = 0x0F;  // dec 15

    /**
     * Last offset subject to the undead stat-lock (inclusive).
     * Maps to the high byte of PC_MAX_MAGIC (0x1F, dec 31).
     */
    public static final int PC_STAT_LOCK_END   = 0x1F;  // dec 31

    // =========================================================================
    // Record size
    // =========================================================================

    /**
     * Size of a single DataCore record in bytes.
     * Increased from 48 (v1–v3) to 64 (v4) to align with CPU cache lines.
     * The additional 16 bytes occupy the expansion zone 0x30–0x3F.
     */
    public static final int RECORD_SIZE = 64;
}
