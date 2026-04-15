// phantasia-core/src/main/java/com/phantasia/core/logic/AttackResolver.java
package com.phantasia.core.logic;

import com.phantasia.core.data.SpellFactory;
import com.phantasia.core.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Resolves combat actions — melee attacks and spell casts — and returns
 * an ordered list of CombatEvents describing exactly what happened.
 *
 * SpellFactory is injected at construction time. If no factory is supplied
 * (the no-arg constructor), spell lookups return null and every cast is
 * recorded as a failed unknown spell — safe for unit tests that don't
 * need a loaded spellbook.
 */
public class AttackResolver {

    private final SpellFactory spellFactory;
    private final Random       rand = new Random();

    /** Production constructor — requires a loaded SpellFactory. */
    public AttackResolver(SpellFactory spellFactory) {
        this.spellFactory = spellFactory;
    }

    /** Test/legacy constructor — spell resolution degrades gracefully. */
    public AttackResolver() {
        this.spellFactory = null;
    }

    // -------------------------------------------------------------------------
    // Melee
    // -------------------------------------------------------------------------

    public void resolveMelee(Entity attacker, Entity target, Action action) {
        int swings = (action == Action.SLASH) ? 3 : 1;
        for (int i = 1; i <= swings; i++) {
            boolean hit = rollToHit(attacker, target);
            if (hit) {
                int dmg = calculateDamage(attacker);
                target.applyDamage(dmg);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Spells
    // -------------------------------------------------------------------------

    /**
     * Resolves a spell cast by ID.
     *
     * Flow:
     *   1. Look up the Spell record from SpellFactory.
     *   2. Check the caster has enough MP; deduct cost.
     *   3. Dispatch on EffectType — data-driven, no hardcoded IDs.
     *   4. Return the full CombatEvent list for the renderer.
     *
     * The call signature is intentionally unchanged so CombatManager
     * requires no edits.
     */
    public List<CombatEvent> resolveSpell(Entity caster, int spellId,
                                          Entity primaryTarget) {
        List<CombatEvent> events = new ArrayList<>();

        // Only player characters cast spells
        if (!(caster instanceof PlayerCharacter pc)) return events;

        // --- 1. Look up the spell record ---
        Spell spell = (spellFactory != null) ? spellFactory.getSpell(spellId) : null;

        if (spell == null) {
            events.add(new CombatEvent.SpellCast(
                    caster.getName(), "Unknown spell #" + spellId,
                    primaryTarget != null ? primaryTarget.getName() : "?",
                    0, false));
            return events;
        }

        // --- 2. MP check and deduction ---
        int currentMp = pc.getStat(Stat.MAGIC_POWER);
        int cost       = spell.getMpCost();

        if (currentMp < cost) {
            events.add(new CombatEvent.StatusChange(
                    caster.getName(), "No Magic Power", true));
            return events;
        }

        pc.setStat(Stat.MAGIC_POWER, currentMp - cost);

        // --- 3. Dispatch on effect type ---
        switch (spell.getEffectType()) {

            case DAMAGE -> resolveDamageSpell(pc, spell, primaryTarget, events);

            case HEAL   -> resolveHealSpell(pc, spell, primaryTarget, events);

            case SLEEP  -> resolveSleepSpell(pc, spell, primaryTarget, events);

            case AWAKEN -> resolveAwakenSpell(pc, spell, primaryTarget, events);

            case BUFF, DEBUFF, UTILITY -> {
                // Placeholder — these effect types are defined but not yet
                // mechanically implemented. Emit the cast event so the
                // renderer can display the spell name; add logic here when
                // the buff/debuff system is built out.
                events.add(new CombatEvent.SpellCast(
                        caster.getName(), spell.getName(),
                        primaryTarget != null ? primaryTarget.getName() : "?",
                        0, true));
            }
        }

        return events;
    }

    // -------------------------------------------------------------------------
    // Effect handlers
    // -------------------------------------------------------------------------

    /**
     * Damage spell — scales power by caster's Intelligence.
     * Final damage = spell.getPower() + rand(0..INT/4), capped at DAMAGE_CAP.
     */
    private void resolveDamageSpell(PlayerCharacter caster, Spell spell,
                                    Entity target, List<CombatEvent> events) {
        if (target == null || !target.isAlive()) return;

        int intel     = caster.getStat(Stat.INTELLIGENCE);
        int variance  = (intel > 0) ? rand.nextInt(Math.max(1, intel / 4)) : 0;
        int damage    = Math.min(spell.getPower() + variance, DataLayout.DAMAGE_CAP);

        if (spell.getTargetType() == Spell.TargetType.ALL_ENEMIES) {
            // ALL_ENEMIES: apply to every living enemy
            // The caller (CombatManager) passes the primary target;
            // for area spells we note the target as "All enemies" and
            // delegate full group resolution back to CombatManager via
            // the event. The magnitude field carries per-target damage.
            events.add(new CombatEvent.SpellCast(
                    caster.getName(), spell.getName(), "All enemies",
                    damage, true));
            // Still apply to the single target passed in — CombatManager
            // is responsible for iterating the rest of the group.
            target.applyDamage(damage);
        } else {
            target.applyDamage(damage);
            events.add(new CombatEvent.SpellCast(
                    caster.getName(), spell.getName(), target.getName(),
                    damage, true));
        }

        if (!target.isAlive()) {
            events.add(new CombatEvent.Death(
                    target.getName(), target instanceof PlayerCharacter));
        }
    }

    /**
     * Heal spell — scales power by caster's Intelligence.
     * Heal amount = spell.getPower() + rand(0..INT/4).
     * Negative magnitude in SpellCast event signals a heal to the renderer.
     */
    private void resolveHealSpell(PlayerCharacter caster, Spell spell,
                                  Entity target, List<CombatEvent> events) {
        if (target == null) return;

        int intel    = caster.getStat(Stat.INTELLIGENCE);
        int variance = (intel > 0) ? rand.nextInt(Math.max(1, intel / 4)) : 0;
        int amount   = spell.getPower() + variance;

        target.applyDamage(-amount);  // negative damage = healing
        events.add(new CombatEvent.SpellCast(
                caster.getName(), spell.getName(), target.getName(),
                -amount, true));     // negative magnitude = heal signal to renderer
    }

    /**
     * Sleep spell — attempts to put the target to sleep.
     * Success chance: 50% base + caster INT - target level (clamped 5–95%).
     */
    private void resolveSleepSpell(PlayerCharacter caster, Spell spell,
                                   Entity target, List<CombatEvent> events) {
        if (target == null || !target.isAlive()) return;

        int intel      = caster.getStat(Stat.INTELLIGENCE);
        int targetLevel = (target instanceof PlayerCharacter tpc)
                ? tpc.getStat(Stat.LEVEL) : 1;
        int chance     = Math.clamp(50 + intel - targetLevel, 5, 95);
        boolean hit    = rand.nextInt(100) < chance;

        if (hit) {
            target.setAsleep(true);
            events.add(new CombatEvent.StatusChange(
                    target.getName(), "Asleep", true));
        }

        events.add(new CombatEvent.SpellCast(
                caster.getName(), spell.getName(), target.getName(),
                0, hit));
    }

    /**
     * Awaken spell — clears sleep status on the target unconditionally.
     */
    private void resolveAwakenSpell(PlayerCharacter caster, Spell spell,
                                    Entity target, List<CombatEvent> events) {
        if (target != null && target.isAsleep()) {
            target.setAsleep(false);
            events.add(new CombatEvent.StatusChange(
                    target.getName(), "Asleep", false));
        }
        events.add(new CombatEvent.SpellCast(
                caster.getName(), spell.getName(),
                target != null ? target.getName() : "?",
                0, true));
    }

    // -------------------------------------------------------------------------
    // Melee helpers
    // -------------------------------------------------------------------------

    public boolean rollToHit(Entity attacker, Entity target) {
        if (attacker instanceof PlayerCharacter pc && target instanceof Monster m)
            return FormulaEngine.playerRollToHit(pc, m);
        if (attacker instanceof Monster m && target instanceof PlayerCharacter pc)
            return FormulaEngine.monsterRollToHit(m, pc);
        return true;
    }

    public int calculateDamage(Entity attacker) {
        if (attacker instanceof PlayerCharacter pc)
            return FormulaEngine.calculatePlayerDamage(pc);
        if (attacker instanceof Monster m)
            return FormulaEngine.calculateMonsterDamage(m);
        return 1;
    }

    public Spell getSpell(int spellId) {
        return spellFactory != null ? spellFactory.getSpell(spellId) : null;
    }
}