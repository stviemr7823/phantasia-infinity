package com.phantasia.core.logic;

import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.Job;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerCreationV5;
import com.phantasia.core.model.PlayerRuntimeV5;
import com.phantasia.core.model.PlayerStateV5;
import com.phantasia.core.model.Spell;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpellStatusV5Test {
    private static PlayerRuntimeV5 player(String name, int mp, int mind) {
        return new PlayerRuntimeV5(PlayerCreationV5.create(
                name, 1, Job.WIZARD, 0, 10, 10, mind, 30, mp));
    }

    private static Monster monster(String name, int hp) {
        DataCore data = new DataCore();
        data.setName(name);
        data.setStat(DataLayout.MON_HP, hp);
        return new Monster(data);
    }

    private static Spell spell(Spell.EffectType effect, Spell.TargetType target, int cost) {
        DataCore data = new DataCore();
        data.setName(effect.name());
        data.setStat(DataLayout.SPELL_EFFECT_TYPE, effect.ordinal());
        data.setStat(DataLayout.SPELL_TARGET, target.ordinal());
        data.setStat(DataLayout.SPELL_MP_COST, cost);
        return new Spell(data);
    }

    @Test
    void sleepChargesOnceForMultipleEnemiesAndNeverTouchesDeadTargets() {
        PlayerRuntimeV5 caster = player("Sage", 20, 255);
        Monster first = monster("First", 30);
        Monster second = monster("Second", 20);
        Monster dead = monster("Dead", 0);
        Spell area = spell(Spell.EffectType.SLEEP, Spell.TargetType.ALL_ENEMIES, 6);
        SpellCastingV5.Result result = SpellCastingV5.castSleep(
                caster, area, List.of(first, dead, second));
        assertTrue(result.cast());
        assertEquals(6, result.mpSpent());
        assertEquals(14, caster.mp());
        assertFalse(dead.isAsleep());
        assertEquals(30, first.getHp());
        assertEquals(20, second.getHp());
        assertTrue(SpellCastingV5.sleepChance(255, 1) <= 95);
        assertTrue(result.impacts().size() <= 2);
        assertEquals(first.isAsleep() ? 1 : 0,
                result.impacts().stream().filter(i -> i.target().equals("First")).count());
        assertEquals(second.isAsleep() ? 1 : 0,
                result.impacts().stream().filter(i -> i.target().equals("Second")).count());
    }

    @Test
    void sleepRejectsBadTargetsAndCannotSpendWhenInsufficient() {
        PlayerRuntimeV5 caster = player("Sage", 2, 10);
        Monster orc = monster("Orc", 30);
        Spell single = spell(Spell.EffectType.SLEEP, Spell.TargetType.SINGLE_ENEMY, 3);
        assertThrows(IllegalArgumentException.class,
                () -> SpellCastingV5.castSleep(caster, single, List.of(orc, orc)));
        assertEquals(2, caster.mp());
        assertFalse(SpellCastingV5.castSleep(caster, single, List.of(orc)).cast());
        assertEquals(2, caster.mp());
        assertFalse(orc.isAsleep());
        assertEquals(5, SpellCastingV5.sleepChance(0, 255));
        assertEquals(95, SpellCastingV5.sleepChance(255, 0));
    }

    @Test
    void awakenClearsPersistentSleepAndPreservesUnrelatedStatusBits() {
        PlayerRuntimeV5 caster = player("Sage", 12, 10);
        PlayerRuntimeV5 first = player("First", 0, 1);
        PlayerRuntimeV5 second = player("Second", 0, 1);
        PlayerRuntimeV5 defeated = player("Defeated", 0, 1);
        PlayerStateV5 state = first.snapshot();
        state.setStatusFlags(0x80);
        first = new PlayerRuntimeV5(state);
        first.setAsleep(true);
        second.setAsleep(true);
        defeated.damage(30);
        defeated.setAsleep(true);
        Spell all = spell(Spell.EffectType.AWAKEN, Spell.TargetType.ALL_ALLIES, 5);
        SpellCastingV5.Result result = SpellCastingV5.castAwaken(
                caster, all, List.of(first, second, defeated));
        assertTrue(result.cast());
        assertEquals(5, result.mpSpent());
        assertEquals(7, caster.mp());
        assertFalse(first.isAsleep());
        assertFalse(second.isAsleep());
        assertTrue(defeated.isAsleep(), "Ordinary spells do not change defeated allies");
        assertEquals(0x80, first.snapshot().statusFlags());
        assertEquals(2, result.impacts().size());
        assertEquals(0x80, PlayerStateV5.fromBytes(first.snapshot().toBytes()).statusFlags());
    }

    @Test
    void awakenInvalidSelectionOrNoResourcesLeavesStatusAlone() {
        PlayerRuntimeV5 caster = player("Sage", 1, 0);
        PlayerRuntimeV5 ally = player("Ally", 0, 0);
        ally.setAsleep(true);
        Spell single = spell(Spell.EffectType.AWAKEN, Spell.TargetType.SINGLE_ALLY, 2);
        assertThrows(IllegalArgumentException.class,
                () -> SpellCastingV5.castAwaken(caster, single, List.of(ally, ally)));
        assertTrue(ally.isAsleep());
        assertFalse(SpellCastingV5.castAwaken(caster, single, List.of(ally)).cast());
        assertTrue(ally.isAsleep());
        assertEquals(1, caster.mp());
    }
}
