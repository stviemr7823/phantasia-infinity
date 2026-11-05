package com.phantasia.core.logic;

import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.Job;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerCreationV5;
import com.phantasia.core.model.PlayerRuntimeV5;
import com.phantasia.core.model.Spell;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpellCastingV5Test {
    private static PlayerRuntimeV5 caster(int mp, int mind) {
        return new PlayerRuntimeV5(PlayerCreationV5.create(
                "Sage", 20, Job.WIZARD, 0, 4, 8, mind, 24, mp));
    }

    private static Monster monster(String name, int hp) {
        DataCore record = new DataCore();
        record.setName(name);
        record.setStat(DataLayout.MON_HP, hp);
        return new Monster(record);
    }

    private static Spell damageSpell(int cost, int power, Spell.TargetType type) {
        DataCore record = new DataCore();
        record.setName("Flame");
        record.setStat(DataLayout.SPELL_MP_COST, cost);
        record.setStat(DataLayout.SPELL_POWER, power);
        record.setStat(DataLayout.SPELL_TARGET, type.ordinal());
        record.setStat(DataLayout.SPELL_EFFECT_TYPE, Spell.EffectType.DAMAGE.ordinal());
        return new Spell(record);
    }

    @Test
    void areaDamageChargesOnceAndAffectsEveryLivingMonster() {
        PlayerRuntimeV5 mage = caster(20, 0); // deterministic variance = 0
        Monster first = monster("First", 50);
        Monster second = monster("Second", 50);
        Monster defeated = monster("Defeated", 0);
        Spell spell = damageSpell(7, 11, Spell.TargetType.ALL_ENEMIES);

        SpellCastingV5.Result result = SpellCastingV5.castDamage(
                mage, spell, List.of(first, defeated, second));

        assertTrue(result.cast());
        assertEquals(7, result.mpSpent());
        assertEquals(13, mage.mp(), "Area spells charge MP once, not per target");
        assertTrue(first.getHp() == 39 || first.getHp() == 50);
        assertTrue(second.getHp() == 39 || second.getHp() == 50);
        assertEquals(0, defeated.getHp());
        assertEquals(2, result.impacts().size() + result.missedTargets().size(),
                "Each living enemy must either be hit or miss");
        for (SpellCastingV5.Impact impact : result.impacts())
            assertEquals(11, impact.damage());
    }

    @Test
    void insufficientMpOrNoLivingTargetsCannotSpendResources() {
        PlayerRuntimeV5 mage = caster(3, 0);
        Monster target = monster("Orc", 30);
        Spell expensive = damageSpell(4, 10, Spell.TargetType.ALL_ENEMIES);

        assertFalse(SpellCastingV5.castDamage(mage, expensive, List.of(target)).cast());
        assertEquals(3, mage.mp());
        assertEquals(30, target.getHp());

        Spell affordable = damageSpell(2, 10, Spell.TargetType.ALL_ENEMIES);
        assertFalse(SpellCastingV5.castDamage(
                mage, affordable, List.of(monster("Dead", 0))).cast());
        assertEquals(3, mage.mp());
        assertFalse(SpellCastingV5.castDamage(mage, affordable, List.of()).cast());
        assertEquals(3, mage.mp());
    }

    @Test
    void singleTargetShapeAndUnsupportedEffectsAreRejectedBeforeSpending() {
        PlayerRuntimeV5 mage = caster(10, 0);
        Monster a = monster("A", 30);
        Monster b = monster("B", 30);
        Spell single = damageSpell(3, 7, Spell.TargetType.SINGLE_ENEMY);

        assertThrows(IllegalArgumentException.class,
                () -> SpellCastingV5.castDamage(mage, single, List.of(a, b)));
        assertEquals(10, mage.mp());
        assertEquals(30, a.getHp());

        SpellCastingV5.Result result = SpellCastingV5.castDamage(
                mage, single, List.of(a));
        assertTrue(result.cast());
        assertEquals(7, mage.mp());
        assertTrue(a.getHp() == 23 || a.getHp() == 30);
        assertEquals(1, result.impacts().size() + result.missedTargets().size());

        DataCore healing = new DataCore();
        healing.setStat(DataLayout.SPELL_EFFECT_TYPE, Spell.EffectType.HEAL.ordinal());
        assertThrows(IllegalArgumentException.class,
                () -> SpellCastingV5.castDamage(mage, new Spell(healing), List.of(a)));
        assertEquals(7, mage.mp());
    }

    @Test
    void mindVarianceAndDamageCapAreBounded() {
        assertEquals(11, SpellCastingV5.magicDamage(0, 11));
        for (int i = 0; i < 100; i++) {
            int damage = SpellCastingV5.magicDamage(80, 90);
            assertTrue(damage >= 90 && damage <= 99);
        }
    }
}
