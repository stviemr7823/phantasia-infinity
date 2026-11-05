package com.phantasia.core.logic;

import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.Job;
import com.phantasia.core.model.PlayerCreationV5;
import com.phantasia.core.model.PlayerRuntimeV5;
import com.phantasia.core.model.Spell;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpellHealingV5Test {
    private static PlayerRuntimeV5 player(String name, int mp, int mind) {
        return new PlayerRuntimeV5(PlayerCreationV5.create(
                name, 1, Job.PRIEST, 0, 10, 10, mind, 40, mp));
    }

    private static Spell healingSpell(int cost, int power, Spell.TargetType targetType) {
        DataCore record = new DataCore();
        record.setName("Mend");
        record.setStat(DataLayout.SPELL_MP_COST, cost);
        record.setStat(DataLayout.SPELL_POWER, power);
        record.setStat(DataLayout.SPELL_TARGET, targetType.ordinal());
        record.setStat(DataLayout.SPELL_EFFECT_TYPE, Spell.EffectType.HEAL.ordinal());
        return new Spell(record);
    }

    @Test
    void areaHealChargesOnceRestoresAliveAlliesAndDoesNotResurrect() {
        PlayerRuntimeV5 caster = player("Sage", 20, 0);
        PlayerRuntimeV5 first = player("First", 0, 0);
        PlayerRuntimeV5 second = player("Second", 0, 0);
        PlayerRuntimeV5 defeated = player("Defeated", 0, 0);
        first.damage(15);
        second.damage(6);
        defeated.damage(40);

        SpellCastingV5.Result result = SpellCastingV5.castHeal(
                caster, healingSpell(7, 10, Spell.TargetType.ALL_ALLIES),
                List.of(first, defeated, second));

        assertTrue(result.cast());
        assertEquals(7, result.mpSpent());
        assertEquals(13, caster.mp());
        assertEquals(35, first.hp());
        assertEquals(40, second.hp());
        assertEquals(0, defeated.hp());
        assertEquals(2, result.impacts().size());
        assertEquals(-10, result.impacts().get(0).damage());
        assertEquals(-6, result.impacts().get(1).damage(),
                "Record the HP actually recovered, not overhealing");
    }

    @Test
    void insufficientMpOrNoLivingAlliesLeavesResourcesUntouched() {
        PlayerRuntimeV5 caster = player("Sage", 2, 0);
        PlayerRuntimeV5 ally = player("Ally", 0, 0);
        ally.damage(10);
        Spell expensive = healingSpell(3, 10, Spell.TargetType.SINGLE_ALLY);

        assertFalse(SpellCastingV5.castHeal(caster, expensive, List.of(ally)).cast());
        assertEquals(30, ally.hp());
        assertEquals(2, caster.mp());

        ally.damage(30);
        Spell cheap = healingSpell(1, 10, Spell.TargetType.ALL_ALLIES);
        assertFalse(SpellCastingV5.castHeal(caster, cheap, List.of(ally)).cast());
        assertEquals(2, caster.mp());
        assertFalse(SpellCastingV5.castHeal(caster, cheap, List.of()).cast());
        assertEquals(2, caster.mp());
    }

    @Test
    void validatesTargetShapeAndDuplicateIdentityBeforeSpending() {
        PlayerRuntimeV5 caster = player("Sage", 10, 0);
        PlayerRuntimeV5 ally = player("Ally", 0, 0);
        ally.damage(8);
        Spell single = healingSpell(3, 5, Spell.TargetType.SINGLE_ALLY);
        assertThrows(IllegalArgumentException.class,
                () -> SpellCastingV5.castHeal(caster, single, List.of(ally, caster)));
        assertThrows(IllegalArgumentException.class,
                () -> SpellCastingV5.castHeal(caster,
                        healingSpell(3, 5, Spell.TargetType.ALL_ALLIES),
                        List.of(ally, ally)));
        assertEquals(10, caster.mp());
        assertEquals(32, ally.hp());
        assertTrue(SpellCastingV5.castHeal(caster, single, List.of(ally)).cast());
        assertEquals(7, caster.mp());
        assertEquals(37, ally.hp());

        Spell wrongTarget = healingSpell(1, 5, Spell.TargetType.SINGLE_ENEMY);
        assertThrows(IllegalArgumentException.class,
                () -> SpellCastingV5.castHeal(caster, wrongTarget, List.of(ally)));
        assertEquals(7, caster.mp());
    }
}
