// phantasia-core/src/main/java/com/phantasia/core/model/Spell.java
package com.phantasia.core.model;

/**
 * A wrapper for a 48-byte spell record.
 *
 * Offset map (all fields are 1 byte, unsigned):
 *   0x00–0x0E  Name        (15-byte ASCII, shared domain)
 *   0x0F       MP cost
 *   0x10       Power       (base damage or heal magnitude)
 *   0x11       Target      (TargetType ordinal)
 *   0x12       Spell type  (SpellType ordinal: 0=WIZARD, 1=PRIEST)
 *   0x13       Level req   (minimum caster level)
 *   0x14       Effect type (EffectType ordinal)
 *
 * Always read spell records through SPELL_* constants from DataLayout.
 * Never substitute PC_* or MON_* names, even if the raw offset matches.
 */
public class Spell {

    private final DataCore core;

    // ------------------------------------------------------------------
    // Target type — who the spell lands on
    // ------------------------------------------------------------------
    public enum TargetType {
        SINGLE_ENEMY,
        ALL_ENEMIES,
        SINGLE_ALLY,
        ALL_ALLIES;

        public static TargetType fromId(int id) {
            return values()[Math.clamp(id, 0, values().length - 1)];
        }
    }

    // ------------------------------------------------------------------
    // Spell type — determines which job classes can learn this spell
    // ------------------------------------------------------------------
    public enum SpellType {
        WIZARD,   // 0 — learnable by Wizard (and Ranger at higher levels)
        PRIEST;   // 1 — learnable by Priest (and Monk at higher levels)

        public static SpellType fromId(int id) {
            return values()[Math.clamp(id, 0, values().length - 1)];
        }
    }

    // ------------------------------------------------------------------
    // Effect type — drives dispatch in AttackResolver
    // ------------------------------------------------------------------
    public enum EffectType {
        DAMAGE,      // 0 — deals HP damage to target(s)
        HEAL,        // 1 — restores HP to target(s)
        SLEEP,       // 2 — applies sleep status
        AWAKEN,      // 3 — clears sleep status
        BUFF,        // 4 — raises a stat temporarily
        DEBUFF,      // 5 — lowers a stat temporarily
        UTILITY;     // 6 — light, detect, teleport, etc.

        public static EffectType fromId(int id) {
            return values()[Math.clamp(id, 0, values().length - 1)];
        }
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    public Spell(DataCore core) {
        this.core = core;
    }

    // ------------------------------------------------------------------
    // Accessors — all routed through SPELL_* constants
    // ------------------------------------------------------------------

    /** 15-character ASCII name from the shared name block (offset 0x00). */
    public String getName() {
        return core.getName();
    }

    /** MP cost to cast this spell (1 byte, unsigned). */
    public int getMpCost() {
        return core.getStat(DataLayout.SPELL_MP_COST);
    }

    /** Base damage or heal magnitude before any modifiers (1 byte, unsigned). */
    public int getPower() {
        return core.getStat(DataLayout.SPELL_POWER);
    }

    /** Who this spell targets. */
    public TargetType getTargetType() {
        return TargetType.fromId(core.getStat(DataLayout.SPELL_TARGET));
    }

    /** Whether this is a wizard or priest spell — used for job eligibility checks. */
    public SpellType getSpellType() {
        return SpellType.fromId(core.getStat(DataLayout.SPELL_TYPE));
    }

    /** Minimum caster level required to cast (1 byte, unsigned). */
    public int getLevelRequirement() {
        return core.getStat(DataLayout.SPELL_LEVEL_REQ);
    }

    /** The mechanical effect category — drives dispatch in AttackResolver. */
    public EffectType getEffectType() {
        return EffectType.fromId(core.getStat(DataLayout.SPELL_EFFECT_TYPE));
    }

    /** Direct access to the underlying record — for tools and bakers only. */
    public DataCore getDataCore() {
        return core;
    }
}