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
        assertEquals(23, caster.mp());
        long hits = result.events().stream()
                .filter(e -> e instanceof CombatEvent.SpellCast).count();
        long misses = result.events().stream()
                .filter(e -> e instanceof CombatEvent.Miss miss
                        && miss.attackerName().equals("Sage")).count();
        assertEquals(2, hits + misses, "Both monsters resolve independently");
        assertTrue(a.getHp() == 0 || a.getHp() == 15);
        assertTrue(b.getHp() == 0 || b.getHp() == 15);
        assertEquals(a.getHp() == 0 && b.getHp() == 0
                ? CombatOutcome.VICTORY : CombatOutcome.ONGOING, result.outcome());
        assertEquals(hits, result.events().stream()
                .filter(e -> e instanceof CombatEvent.Death d && !d.wasPartyMember()).count());
    }

    @Test
    void invalidOrdersRejectBeforeRoundChangesAndNoSpellIsCharged() {
        PlayerRuntimeV5 caster = player("Sage", 60, 10);
        Monster monster = monster("Orc", 25);
        Monster outsider = monster("Outsider", 25);
        CombatRoundV5 round = new CombatRoundV5(List.of(caster), List.of(monster));
        assertThrows(IllegalArgumentException.class, () -> round.runRound(List.of(
                CombatRoundV5.PlayerOrder.cast(caster, areaDamage(3, 10), outsider, null))));
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
        PlayerRuntimeV5 caster = player("Sage", 400, 200);
        Monster enemy = monster("Orc", 5);
        CombatRoundV5 combat = new CombatRoundV5(List.of(caster), List.of(enemy));

        // A damaging spell can legitimately miss. Repeat until victory,
        // never assuming a random hit on any particular round.
        for (int i = 0; i < 20 && combat.outcome() == CombatOutcome.ONGOING; i++)
            combat.runRound(List.of(CombatRoundV5.PlayerOrder.cast(
                    caster, areaDamage(4, 20), null, null)));
        assertEquals(CombatOutcome.VICTORY, combat.outcome());
        int remainingMp = caster.mp();
        assertTrue(remainingMp < 200);
        assertEquals(0, enemy.getHp());

        assertThrows(IllegalStateException.class,
                () -> combat.runRound(List.of(CombatRoundV5.PlayerOrder.cast(
                        caster, areaDamage(4, 20), null, null))));
        assertEquals(remainingMp, caster.mp(), "A completed encounter must not spend MP again");
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
    void singleTargetSpellOnPreviouslyDefeatedEnemyFailsWithoutMpCharge() {
        PlayerRuntimeV5 caster = player("Sage", 400, 20);
        Monster defeated = monster("Defeated", 0);
        Monster survivor = monster("Survivor", 30);
        CombatRoundV5 combat = new CombatRoundV5(
                List.of(caster), List.of(defeated, survivor));

        DataCore record = new DataCore();
        record.setName("Bolt");
        record.setStat(DataLayout.SPELL_MP_COST, 5);
        record.setStat(DataLayout.SPELL_POWER, 10);
        record.setStat(DataLayout.SPELL_TARGET, Spell.TargetType.SINGLE_ENEMY.ordinal());
        record.setStat(DataLayout.SPELL_EFFECT_TYPE, Spell.EffectType.DAMAGE.ordinal());

        RoundResult result = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.cast(caster, new Spell(record), defeated, null)));

        assertEquals(1, result.roundNumber());
        assertEquals(20, caster.mp());
        assertEquals(0, defeated.getHp());
        assertEquals(30, survivor.getHp());
        assertTrue(result.events().stream().anyMatch(e ->
                e instanceof CombatEvent.SpellCast cast
                && cast.casterName().equals("Sage") && !cast.succeeded()));
        assertEquals(CombatOutcome.ONGOING, result.outcome());
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
