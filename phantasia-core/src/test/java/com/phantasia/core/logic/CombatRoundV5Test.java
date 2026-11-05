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

class CombatRoundV5Test {
    private static PlayerRuntimeV5 player(String name, int hp, int mp) {
        return new PlayerRuntimeV5(PlayerCreationV5.create(
                name, 1, Job.WIZARD, 0, 20, 20, 0, hp, mp));
    }

    private static Monster monster(String name, int hp) {
        DataCore core = new DataCore();
        core.setName(name);
        core.setStat(DataLayout.MON_HP, hp);
        return new Monster(core);
    }

    private static Spell areaDamage(int cost, int damage) {
        DataCore core = new DataCore();
        core.setName("Burst");
        core.setStat(DataLayout.SPELL_MP_COST, cost);
        core.setStat(DataLayout.SPELL_POWER, damage);
        core.setStat(DataLayout.SPELL_TARGET, Spell.TargetType.ALL_ENEMIES.ordinal());
        core.setStat(DataLayout.SPELL_EFFECT_TYPE, Spell.EffectType.DAMAGE.ordinal());
        return new Spell(core);
    }

    @Test
    void roundEmitsHeaderSpellEventsAndVictoryWithoutMultipleCharges() {
        PlayerRuntimeV5 caster = player("Sage", 400, 30);
        Monster a = monster("A", 15);
        Monster b = monster("B", 15);
        CombatRoundV5 round = new CombatRoundV5(List.of(caster), List.of(a, b));

        RoundResult result = round.runRound(List.of(CombatRoundV5.PlayerOrder.cast(
                caster, areaDamage(7, 80), null, null)));

        assertEquals(1, result.roundNumber());
        assertTrue(result.events().getFirst() instanceof CombatEvent.RoundHeader);
        assertEquals(CombatOutcome.VICTORY, result.outcome());
        assertEquals(23, caster.mp());
        assertEquals(0, a.getHp());
        assertEquals(0, b.getHp());
        assertEquals(2, result.events().stream()
                .filter(e -> e instanceof CombatEvent.SpellCast).count());
        assertEquals(2, result.events().stream()
                .filter(e -> e instanceof CombatEvent.Death d && !d.wasPartyMember()).count());
    }

    @Test
    void invalidOrdersRejectBeforeRoundChangesAndNoSpellIsCharged() {
        PlayerRuntimeV5 caster = player("Sage", 60, 10);
        Monster monster = monster("Orc", 25);
        CombatRoundV5 round = new CombatRoundV5(List.of(caster), List.of(monster));
        assertThrows(IllegalArgumentException.class, () -> round.runRound(List.of(
                CombatRoundV5.PlayerOrder.cast(caster, areaDamage(3, 10), monster, null))));
        assertEquals(10, caster.mp());
        assertEquals(25, monster.getHp());
        assertThrows(IllegalArgumentException.class, () -> round.runRound(List.of(
                CombatRoundV5.PlayerOrder.idle(caster),
                CombatRoundV5.PlayerOrder.idle(caster))));
        assertEquals(10, caster.mp());
        assertEquals(25, monster.getHp());

        RoundResult valid = round.runRound(List.of(CombatRoundV5.PlayerOrder.idle(caster)));
        assertEquals(1, valid.roundNumber());
        assertTrue(valid.events().getFirst() instanceof CombatEvent.RoundHeader);
    }

    @Test
    void monsterRetaliationUsesTypedV5HpAndCanCauseDefeat() {
        PlayerRuntimeV5 character = player("Sage", 1, 0);
        Monster enemy = monster("Orc", 255);
        CombatRoundV5 round = new CombatRoundV5(List.of(character), List.of(enemy));

        boolean died = false;
        for (int i = 0; i < 100 && !died; i++) {
            RoundResult result = round.runRound(List.of());
            died = result.outcome() == CombatOutcome.DEFEAT;
        }
        assertTrue(died, "A powerful monster should eventually hit a one-HP player");
        assertEquals(0, character.hp());
        assertEquals(CombatOutcome.DEFEAT, round.outcome());
    }

    @Test
    void victoryClosesCombatAndRejectsFurtherRoundsWithoutSpendingMp() {
        PlayerRuntimeV5 caster = player("Sage", 400, 20);
        Monster enemy = monster("Orc", 5);
        CombatRoundV5 combat = new CombatRoundV5(List.of(caster), List.of(enemy));

        RoundResult victory = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.cast(caster, areaDamage(4, 20), null, null)));
        assertEquals(CombatOutcome.VICTORY, victory.outcome());
        assertEquals(16, caster.mp());
        assertEquals(0, enemy.getHp());

        assertThrows(IllegalStateException.class,
                () -> combat.runRound(List.of(CombatRoundV5.PlayerOrder.cast(
                        caster, areaDamage(4, 20), null, null))));
        assertEquals(16, caster.mp(), "A completed encounter must not spend MP again");
        assertEquals(CombatOutcome.VICTORY, combat.outcome());
    }

    @Test
    void alreadyDefeatedPartyCannotStartCombatRound() {
        PlayerRuntimeV5 caster = player("Sage", 10, 15);
        Monster enemy = monster("Orc", 50);
        CombatRoundV5 combat = new CombatRoundV5(List.of(caster), List.of(enemy));
        caster.damage(10);
        assertEquals(CombatOutcome.DEFEAT, combat.outcome());
        assertThrows(IllegalStateException.class,
                () -> combat.runRound(List.of(CombatRoundV5.PlayerOrder.cast(
                        caster, areaDamage(5, 30), null, null))));
        assertEquals(15, caster.mp());
        assertEquals(50, enemy.getHp());
    }

    @Test
    void rejectsDuplicateCombatantsAndUnsupportedActions() {
        PlayerRuntimeV5 character = player("Sage", 50, 5);
        Monster enemy = monster("Orc", 10);
        assertThrows(IllegalArgumentException.class, () ->
                new CombatRoundV5(List.of(character, character), List.of(enemy)));
        assertThrows(IllegalArgumentException.class, () ->
                new CombatRoundV5(List.of(character), List.of(enemy, enemy)));
        assertThrows(IllegalArgumentException.class, () ->
                new CombatRoundV5(List.of(), List.of(enemy)));
        assertThrows(IllegalArgumentException.class, () ->
                new CombatRoundV5.PlayerOrder(character,
                        com.phantasia.core.model.Action.SLASH, enemy, null, null, null));
    }
}
