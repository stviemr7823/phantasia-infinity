package com.phantasia.core.logic;

import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerRuntimeV5;
import com.phantasia.core.model.PlayerStateV5.Attribute;
import com.phantasia.core.model.Spell;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * First native-v5 magic execution seam: damage spells against monsters.
 * A cast validates its targets, spends MP once, then resolves all targets.
 * It intentionally does not modify the legacy AttackResolver/CombatManager.
 */
public final class SpellCastingV5 {
    private SpellCastingV5() {}

    public record Impact(String target, int damage) {}
    public record Result(boolean cast, int mpSpent, List<Impact> impacts) {
        public Result {
            impacts = List.copyOf(impacts);
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
        if (!caster.isAlive())
            return new Result(false, 0, List.of());

        List<Monster> living = new ArrayList<>();
        for (Monster target : targets) {
            Objects.requireNonNull(target, "target");
            if (target.isAlive()) living.add(target);
        }
        if (spell.getTargetType() == Spell.TargetType.SINGLE_ENEMY && living.size() != 1)
            throw new IllegalArgumentException("Single-enemy cast requires one living target");
        if (living.isEmpty())
            return new Result(false, 0, List.of());

        int cost = spell.getMpCost();
        if (!caster.spendMp(cost))
            return new Result(false, 0, List.of());

        List<Impact> impacts = new ArrayList<>();
        for (Monster target : living) {
            int damage = magicDamage(caster.attribute(Attribute.MIND), spell.getPower());
            target.applyDamage(damage);
            impacts.add(new Impact(target.getName(), damage));
        }
        return new Result(true, cost, impacts);
    }

    /** Same Mind-based variance convention as legacy Intelligence spells. */
    static int magicDamage(int mind, int power) {
        int variance = mind > 0 ? Dice.nextInt(Math.max(1, mind / 4)) : 0;
        return Math.min(power + variance, 99);
    }
}
