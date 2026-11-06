package com.phantasia.core.logic;

import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerRuntimeV5;
import com.phantasia.core.model.PlayerStateV5.Attribute;
import com.phantasia.core.model.Spell;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Native-v5 damage, healing and basic status execution, isolated from legacy combat.
 * A cast validates its targets, spends MP once, then resolves all targets.
 * It intentionally does not modify the legacy AttackResolver/CombatManager.
 */
public final class SpellCastingV5 {
    private SpellCastingV5() {}

    public record Impact(String target, int damage) {}
    public record Result(boolean cast, int mpSpent, List<Impact> impacts,
                         List<String> missedTargets) {
        public Result {
            impacts = List.copyOf(impacts);
            missedTargets = List.copyOf(missedTargets);
        }
        public Result(boolean cast, int mpSpent, List<Impact> impacts) {
            this(cast, mpSpent, impacts, List.of());
        }
    }

    /** Applies a damage spell to one or all living enemies, as its targeting demands. */
    public static Result castDamage(
            PlayerRuntimeV5 caster, Spell spell, List<Monster> targets) {
        Objects.requireNonNull(caster, "caster");
        Objects.requireNonNull(spell, "spell");
        Objects.requireNonNull(targets, "targets");
        if (spell.getEffectType() != Spell.EffectType.DAMAGE)
            throw new IllegalArgumentException("Not a damage spell");
        if (spell.getTargetType() != Spell.TargetType.SINGLE_ENEMY
                && spell.getTargetType() != Spell.TargetType.ALL_ENEMIES)
            throw new IllegalArgumentException("Damage spell requires enemy targets");
        // Validate selection independently of whether targets survive until
        // execution: another party member may have defeated them earlier.
        java.util.Set<Monster> seen =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<Monster> living = new ArrayList<>();
        for (Monster target : targets) {
            Objects.requireNonNull(target, "target");
            if (!seen.add(target))
                throw new IllegalArgumentException("Duplicate enemy target");
            if (target.isAlive()) living.add(target);
        }
        if (spell.getTargetType() == Spell.TargetType.SINGLE_ENEMY && targets.size() != 1)
            throw new IllegalArgumentException("Single-enemy cast requires one selected target");
        if (!caster.isAlive() || living.isEmpty())
            return new Result(false, 0, List.of());

        int cost = spell.getMpCost();
        if (!caster.spendMp(cost))
            return new Result(false, 0, List.of());

        List<Impact> impacts = new ArrayList<>();
        List<String> missed = new ArrayList<>();
        int mind = caster.attribute(Attribute.MIND);
        for (Monster target : living) {
            // Each enemy rolls separately, including enemies in an area spell.
            // A resisted hit still belongs to the same once-paid cast.
            int chance = Math.clamp(50 + mind - FormulaEngine.monsterDefenseValue(target), 5, 95);
            if (!Dice.chance(chance)) {
                missed.add(target.getName());
                continue;
            }
            int damage = magicDamage(mind, spell.getPower());
            target.applyDamage(damage);
            impacts.add(new Impact(target.getName(), damage));
        }
        return new Result(true, cost, impacts, missed);
    }

    /**
     * Heals one or all living allies, paying MP once for the complete cast.
     * A defeated ally is not revived by ordinary healing. The Result's impact
     * magnitude is negative for healing, matching legacy spell event semantics.
     * This API accepts an already-selected group, not a whole party roster.
     */
    public static Result castHeal(
            PlayerRuntimeV5 caster, Spell spell, List<PlayerRuntimeV5> targets) {
        Objects.requireNonNull(caster, "caster");
        Objects.requireNonNull(spell, "spell");
        Objects.requireNonNull(targets, "targets");
        if (spell.getEffectType() != Spell.EffectType.HEAL)
            throw new IllegalArgumentException("Not a healing spell");
        if (spell.getTargetType() != Spell.TargetType.SINGLE_ALLY
                && spell.getTargetType() != Spell.TargetType.ALL_ALLIES)
            throw new IllegalArgumentException("Healing spell requires ally targets");

        // Validate the entire selection before spending MP or changing HP.
        java.util.Set<PlayerRuntimeV5> unique =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<PlayerRuntimeV5> living = new ArrayList<>();
        for (PlayerRuntimeV5 target : targets) {
            Objects.requireNonNull(target, "target");
            if (!unique.add(target))
                throw new IllegalArgumentException("Duplicate ally target");
            if (target.isAlive()) living.add(target);
        }
        if (spell.getTargetType() == Spell.TargetType.SINGLE_ALLY
                && targets.size() != 1)
            throw new IllegalArgumentException("Single-ally cast requires one selected target");
        if (!caster.isAlive() || living.isEmpty())
            return new Result(false, 0, List.of());

        int cost = spell.getMpCost();
        if (!caster.spendMp(cost))
            return new Result(false, 0, List.of());

        List<Impact> impacts = new ArrayList<>();
        for (PlayerRuntimeV5 target : living) {
            int before = target.hp();
            int healing = magicDamage(caster.attribute(Attribute.MIND), spell.getPower());
            target.heal(healing);
            impacts.add(new Impact(target.name(), -(target.hp() - before)));
        }
        return new Result(true, cost, impacts);
    }

    /**
     * Sleep targets enemy monsters. Living targets get independent Mind-based
     * resistance rolls, but the spell spends MP once for the selected group.
     * Result impacts report only successful status changes, with zero damage.
     */
    public static Result castSleep(
            PlayerRuntimeV5 caster, Spell spell, List<Monster> targets) {
        Objects.requireNonNull(caster, "caster");
        Objects.requireNonNull(spell, "spell");
        Objects.requireNonNull(targets, "targets");
        if (spell.getEffectType() != Spell.EffectType.SLEEP)
            throw new IllegalArgumentException("Not a sleep spell");
        if (spell.getTargetType() != Spell.TargetType.SINGLE_ENEMY
                && spell.getTargetType() != Spell.TargetType.ALL_ENEMIES)
            throw new IllegalArgumentException("Sleep requires enemy targets");

        java.util.Set<Monster> seen =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<Monster> living = new ArrayList<>();
        for (Monster target : targets) {
            Objects.requireNonNull(target, "target");
            if (!seen.add(target))
                throw new IllegalArgumentException("Duplicate enemy target");
            if (target.isAlive()) living.add(target);
        }
        if (spell.getTargetType() == Spell.TargetType.SINGLE_ENEMY
                && targets.size() != 1)
            throw new IllegalArgumentException("Single-enemy sleep requires one selected target");
        if (!caster.isAlive() || living.isEmpty())
            return new Result(false, 0, List.of());
        int cost = spell.getMpCost();
        if (!caster.spendMp(cost))
            return new Result(false, 0, List.of());

        List<Impact> impacts = new ArrayList<>();
        List<String> misses = new ArrayList<>();
        int chance = sleepChance(caster.attribute(Attribute.MIND), 1);
        for (Monster target : living) {
            if (Dice.chance(chance)) {
                target.setAsleep(true);
                impacts.add(new Impact(target.getName(), 0));
            } else {
                misses.add(target.getName());
            }
        }
        return new Result(true, cost, impacts, misses);
    }

    /** Mirrors the legacy resistance formula using v5 Mind. */
    static int sleepChance(int mind, int targetLevel) {
        return Math.clamp(50 + mind - targetLevel, 5, 95);
    }

    /**
     * Awakening clears sleep on selected living allies; it cannot resurrect.
     * Successful or already-awake targets may be selected and cost MP once.
     * Impacts describe only allies whose sleep status actually changed.
     */
    public static Result castAwaken(
            PlayerRuntimeV5 caster, Spell spell, List<PlayerRuntimeV5> targets) {
        Objects.requireNonNull(caster, "caster");
        Objects.requireNonNull(spell, "spell");
        Objects.requireNonNull(targets, "targets");
        if (spell.getEffectType() != Spell.EffectType.AWAKEN)
            throw new IllegalArgumentException("Not an awakening spell");
        if (spell.getTargetType() != Spell.TargetType.SINGLE_ALLY
                && spell.getTargetType() != Spell.TargetType.ALL_ALLIES)
            throw new IllegalArgumentException("Awakening requires ally targets");

        java.util.Set<PlayerRuntimeV5> seen =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<PlayerRuntimeV5> living = new ArrayList<>();
        for (PlayerRuntimeV5 target : targets) {
            Objects.requireNonNull(target, "target");
            if (!seen.add(target))
                throw new IllegalArgumentException("Duplicate ally target");
            if (target.isAlive()) living.add(target);
        }
        if (spell.getTargetType() == Spell.TargetType.SINGLE_ALLY
                && targets.size() != 1)
            throw new IllegalArgumentException("Single-ally awakening requires one selected target");
        if (!caster.isAlive() || living.isEmpty())
            return new Result(false, 0, List.of());
        int cost = spell.getMpCost();
        if (!caster.spendMp(cost))
            return new Result(false, 0, List.of());

        List<Impact> impacts = new ArrayList<>();
        for (PlayerRuntimeV5 target : living) {
            if (target.isAsleep()) {
                target.setAsleep(false);
                impacts.add(new Impact(target.name(), 0));
            }
        }
        return new Result(true, cost, impacts);
    }

    /** Same Mind-based variance convention as legacy Intelligence spells. */
    static int magicDamage(int mind, int power) {
        int variance = mind > 0 ? Dice.nextInt(Math.max(1, mind / 4)) : 0;
        return Math.min(power + variance, 99);
    }
}
