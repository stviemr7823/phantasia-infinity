package com.phantasia.core.logic;

import com.phantasia.core.model.CombatRank;
import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.Job;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerCreationV5;
import com.phantasia.core.model.PlayerRuntimeV5;
import com.phantasia.core.model.Spell;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatTacticsV5Test {
    private static PlayerRuntimeV5 player(String name) {
        return new PlayerRuntimeV5(PlayerCreationV5.create(
                name, 1, Job.WIZARD, 0, 20, 20, 0, 1500, 100));
    }

    private static Monster monster(String name) {
        DataCore data = new DataCore();
        data.setName(name);
        data.setStat(DataLayout.MON_HP, 200);
        return new Monster(data);
    }

    private static Spell areaSpell() {
        DataCore data = new DataCore();
        data.setName("Storm");
        data.setStat(DataLayout.SPELL_MP_COST, 7);
        data.setStat(DataLayout.SPELL_POWER, 15);
        data.setStat(DataLayout.SPELL_TARGET, Spell.TargetType.ALL_ENEMIES.ordinal());
        data.setStat(DataLayout.SPELL_EFFECT_TYPE, Spell.EffectType.DAMAGE.ordinal());
        return new Spell(data);
    }

    @Test
    void lungeFromFrontReachesMiddleButNotProtectedBackRank() {
        PlayerRuntimeV5 warrior = player("Warrior");
        Monster shield = monster("Shield");
        Monster middle = monster("Middle");
        Monster rear = monster("Rear");
        CombatRoundV5 combat = new CombatRoundV5(
                List.of(warrior), List.of(shield, middle, rear),
                Map.of(warrior, CombatRank.FRONT),
                Map.of(shield, CombatRank.FRONT, middle, CombatRank.MIDDLE,
                        rear, CombatRank.BACK));

        assertFalse(combat.canMeleeReach(warrior, middle));
        assertTrue(combat.canLungeReach(warrior, middle));
        assertFalse(combat.canLungeReach(warrior, rear));
        assertThrows(IllegalArgumentException.class, () -> combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.lunge(warrior, rear, CombatRank.FRONT))));
        RoundResult result = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.lunge(warrior, middle, CombatRank.FRONT)));
        assertEquals(1, result.roundNumber());
        assertEquals(200, shield.getHp());
        assertEquals(200, rear.getHp());
        assertTrue(result.events().stream().anyMatch(e ->
                (e instanceof CombatEvent.Hit h && h.attackerName().equals("Warrior")
                        && h.targetName().equals("Middle"))
                || (e instanceof CombatEvent.Miss m && m.attackerName().equals("Warrior")
                        && m.targetName().equals("Middle"))));
    }

    @Test
    void areaSpellCanSelectOneRankOrBothAndEveryTargetCanMiss() {
        PlayerRuntimeV5 mage = player("Mage");
        Monster front = monster("Front");
        Monster middle = monster("Middle");
        Monster rear = monster("Rear");
        CombatRoundV5 combat = new CombatRoundV5(
                List.of(mage), List.of(front, middle, rear),
                Map.of(), Map.of(front, CombatRank.FRONT, middle, CombatRank.MIDDLE,
                        rear, CombatRank.BACK));

        RoundResult first = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.cast(mage, areaSpell(), middle, null)));
        assertEquals(93, mage.mp());
        assertEquals(200, front.getHp(), "Choosing middle rank must not damage the front");
        assertEquals(200, rear.getHp(), "Choosing middle rank must not damage the rear");
        long middleResolved = first.events().stream().filter(e ->
                (e instanceof CombatEvent.SpellCast c && c.targetName().equals("Middle"))
                || (e instanceof CombatEvent.Miss m && m.attackerName().equals("Mage")
                        && m.targetName().equals("Middle"))).count();
        assertEquals(1, middleResolved);

        RoundResult second = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.cast(mage, areaSpell(), null, null)));
        assertEquals(86, mage.mp(), "Both-rank spell still spends MP once");
        long spellResolutions = second.events().stream().filter(e ->
                e instanceof CombatEvent.SpellCast
                || (e instanceof CombatEvent.Miss m && m.attackerName().equals("Mage"))).count();
        assertEquals(3, spellResolutions, "All three living enemies resolve hit or miss");
    }
}
