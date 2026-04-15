// phantasia-core/src/main/java/com/phantasia/core/logic/FormulaEngine.java
package com.phantasia.core.logic;

import com.phantasia.core.model.Entity;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.model.Stat;

/**
 * Combat math — faithful to Phantasie III mechanics.
 *
 * Initiative: Dexterity + d10 roll (no SPEED stat exists in this game).
 * Hit chance: base 50% modified by attacker DEX vs defender DEX.
 * Damage:     Strength-based with random variance.
 */
public class FormulaEngine {

    private static final int DAMAGE_CAP = 99;

    private static final String[] LIMBS = {
            "Head", "Left Arm", "Right Arm", "Torso", "Left Leg", "Right Leg"
    };

    // -------------------------------------------------------------------------
    // Monster derived values
    // -------------------------------------------------------------------------

    /** Monster attack value IS its HP — no divisor. */
    public static int monsterAttackValue(Monster m) {
        return m.getHp();
    }

    /**
     * Monster defense: scaled down so players can reasonably hit.
     * HP/6 gives Dragon King defense=22, which against a DEX-16 player
     * yields ~44% hit chance — challenging but fair.
     */
    public static int monsterDefenseValue(Monster m) {
        return Math.max(1, m.getHp() / 6);
    }

    // -------------------------------------------------------------------------
    // Damage
    // -------------------------------------------------------------------------

    /** Monster damage — capped at 99. */
    public static int calculateMonsterDamage(Monster attacker) {
        int atk = Math.max(2, monsterAttackValue(attacker));
        int raw = Dice.nextInt(atk / 2) + (atk / 4) + 1;
        return Math.min(raw, DAMAGE_CAP);
    }

    /** Player damage — also capped at 99. */
    public static int calculatePlayerDamage(PlayerCharacter attacker) {
        int str = Math.max(2, attacker.getStat(Stat.STRENGTH));
        int raw = Dice.nextInt(str / 2) + (str / 4) + 1;
        float mod = attacker.getCombatRank().damageMod;
        return Math.min(Math.round(raw * mod), DAMAGE_CAP);
    }

    // -------------------------------------------------------------------------
    // Hit resolution
    // -------------------------------------------------------------------------

    /** Player attacking a Monster. */
    public static boolean playerRollToHit(PlayerCharacter attacker,
                                          Monster defender) {
        int atkDex = attacker.getStat(Stat.DEXTERITY);
        int defVal = monsterDefenseValue(defender);
        int chance = Math.clamp(50 + (atkDex - defVal), 5, 95);
        return Dice.chance(chance);
    }

    /** Monster attacking a Player. */
    public static boolean monsterRollToHit(Monster attacker,
                                           PlayerCharacter defender) {
        int atkVal = monsterAttackValue(attacker);
        int defDex = defender.getStat(Stat.DEXTERITY);
        int chance = Math.clamp(50 + (atkVal - defDex), 5, 95);
        return Dice.chance(chance);
    }

    public static int applyCritical(int baseDamage) {
        return Math.min(baseDamage * 2, DAMAGE_CAP);
    }

    // -------------------------------------------------------------------------
    // Initiative
    // -------------------------------------------------------------------------

    /** d10 roll — no stat influence. */
    public static int rollInitiative() {
        return Dice.d10();
    }

    // -------------------------------------------------------------------------
    // Critical hits
    // -------------------------------------------------------------------------

    /**
     * 10% critical hit chance.
     * CombatManager doubles damage on a critical, still capped at 99.
     */
    public static boolean rollCritical() {
        return Dice.chance(10);
    }

    // -------------------------------------------------------------------------
    // Hit location
    // -------------------------------------------------------------------------

    /** Returns a random limb name for hit location flavour. */
    public static String rollHitLocation() {
        return LIMBS[Dice.nextInt(LIMBS.length)];
    }

    // -------------------------------------------------------------------------
    // Escape
    // -------------------------------------------------------------------------

    /**
     * Dexterity-based escape attempt.
     * Players: 30% base + DEX, clamped 5–85%.
     * Monsters never flee — always returns false.
     */
    public static boolean rollEscape(Entity entity) {
        if (entity instanceof PlayerCharacter pc) {
            int dex    = pc.getStat(Stat.DEXTERITY);
            int chance = Math.clamp(30 + dex, 5, 85);
            return Dice.chance(chance);
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Encounter
    // -------------------------------------------------------------------------

    public static EncounterCondition rollEncounterCondition() {
        int r = Dice.nextInt(100);
        if      (r < 15) return EncounterCondition.MONSTERS_SURPRISE;
        else if (r < 25) return EncounterCondition.PARTY_ASLEEP;
        else             return EncounterCondition.NORMAL;
    }

    public static boolean rollEncounter(PlayerCharacter partyLead) {
        int luck   = partyLead.getStat(Stat.LUCK);
        int chance = Math.clamp(15 - (luck / 10), 5, 25);
        return Dice.chance(chance);
    }
}