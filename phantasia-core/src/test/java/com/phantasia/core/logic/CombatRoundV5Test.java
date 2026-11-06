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
    void defeatedMeleeTargetSkipsWithoutRetargetingOrFalseMiss() {
        PlayerRuntimeV5 attacker = player("Fighter", 400, 0);
        Monster defeated = monster("Orc", 0);
        Monster survivor = monster("Orc", 100);
        CombatRoundV5 combat = new CombatRoundV5(
                List.of(attacker), List.of(defeated, survivor));

        RoundResult result = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.attack(attacker, defeated,
                        com.phantasia.core.model.CombatRank.FRONT)));

        assertEquals(100, survivor.getHp());
        assertEquals(0, defeated.getHp());
        assertTrue(result.events().stream().anyMatch(e ->
                e instanceof CombatEvent.ActionSkipped skipped
                && skipped.actorName().equals("Fighter")
                && skipped.reason().equals("TARGET_DEFEATED")));
        assertFalse(result.events().stream().anyMatch(e ->
                e instanceof CombatEvent.Miss miss
                && miss.attackerName().equals("Fighter")));
        assertFalse(result.events().stream().anyMatch(e ->
                e instanceof CombatEvent.Death));
    }

    @Test
    void newlyShieldedMeleeTargetSkipsWithReachReason() {
        PlayerRuntimeV5 attacker = player("Fighter", 400, 0);
        Monster front = monster("Front", 100);
        Monster middle = monster("Middle", 100);
        CombatRoundV5 combat = new CombatRoundV5(
                List.of(attacker), List.of(front, middle), java.util.Map.of(),
                java.util.Map.of(front, com.phantasia.core.model.CombatRank.FRONT,
                        middle, com.phantasia.core.model.CombatRank.MIDDLE));

        // Middle is protected by the living front rank. Planning rejects
        // attacks that are already out of reach before the round begins.
        assertThrows(IllegalArgumentException.class, () -> combat.runRound(
                List.of(CombatRoundV5.PlayerOrder.attack(attacker, middle,
                        com.phantasia.core.model.CombatRank.FRONT))));
        assertEquals(100, middle.getHp());
    }


    @Test
    void laterAttackSkippedWhenEarlierAttackerDefeatsTarget() {
        PlayerRuntimeV5 first = player("First", 400, 0);
        PlayerRuntimeV5 second = player("Second", 400, 0);
        Monster target = monster("Orc", 1);
        Monster remaining = monster("Orc", 100);
        CombatRoundV5 combat = new CombatRoundV5(
                List.of(first, second), List.of(target, remaining),
                java.util.Map.of(), java.util.Map.of(),
                actor -> actor == first ? 30 : actor == second ? 20 : 10,
                (attacker, defender) -> true);
        RoundResult result = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.attack(first, target,
                        com.phantasia.core.model.CombatRank.FRONT),
                CombatRoundV5.PlayerOrder.attack(second, target,
                        com.phantasia.core.model.CombatRank.FRONT)));
        assertEquals(0, target.getHp());
        assertEquals(100, remaining.getHp());
        assertEquals(1, result.events().stream().filter(e ->
                e instanceof CombatEvent.ActionSkipped skipped
                && skipped.actorName().equals("Second")
                && skipped.reason().equals("TARGET_DEFEATED")).count());
        assertEquals(1, result.events().stream().filter(e ->
                e instanceof CombatEvent.Death d && !d.wasPartyMember()).count());
    }


    @Test
    void spellDeathDoesNotDuplicateAlreadyDeadSameNamedMonster() {
        PlayerRuntimeV5 caster = player("Sage", 400, 100);
        Monster alreadyDead = monster("Orc", 0);
        Monster living = monster("Orc", 1);
        CombatRoundV5 combat = new CombatRoundV5(
                List.of(caster), List.of(alreadyDead, living));
        Spell burst = areaDamage(1, 20);

        // Hits are random. A miss spends MP but cannot generate a death.
        // Repeat until a hit, without expecting a 95% roll to always land.
        int deaths = 0;
        for (int attempt = 0; attempt < 30 && combat.outcome() == CombatOutcome.ONGOING; attempt++) {
            RoundResult result = combat.runRound(List.of(
                    CombatRoundV5.PlayerOrder.cast(caster, burst, null, null)));
            deaths += (int) result.events().stream().filter(e ->
                    e instanceof CombatEvent.Death d && !d.wasPartyMember()).count();
        }

        assertEquals(CombatOutcome.VICTORY, combat.outcome());
        assertEquals(0, alreadyDead.getHp());
        assertEquals(0, living.getHp());
        assertEquals(1, deaths,
                "An already-dead monster with the same name must not be killed again");
    }


    @Test
    void failedCastReportsInsufficientMpWithoutChangingResources() {
        PlayerRuntimeV5 caster = player("Sage", 400, 2);
        Monster target = monster("Orc", 40);
        CombatRoundV5 combat = new CombatRoundV5(List.of(caster), List.of(target));
        RoundResult result = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.cast(caster, areaDamage(5, 20), target, null)));
        assertEquals(2, caster.mp());
        assertEquals(40, target.getHp());
        assertTrue(result.events().stream().anyMatch(e ->
                e instanceof CombatEvent.SpellCast cast
                && !cast.succeeded()
                && cast.targetName().equals("Insufficient MP")));
    }

    @Test
    void failedCastReportsNoLivingTargetWithoutChargingMp() {
        PlayerRuntimeV5 caster = player("Sage", 400, 10);
        Monster defeated = monster("Orc", 0);
        Monster remaining = monster("Other", 40);
        CombatRoundV5 combat = new CombatRoundV5(List.of(caster),
                List.of(defeated, remaining), java.util.Map.of(),
                java.util.Map.of(defeated, com.phantasia.core.model.CombatRank.FRONT,
                        remaining, com.phantasia.core.model.CombatRank.BACK));
        RoundResult result = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.cast(caster, areaDamage(5, 20),
                        defeated, null)));
        assertEquals(10, caster.mp());
        assertEquals(40, remaining.getHp());
        assertTrue(result.events().stream().anyMatch(e ->
                e instanceof CombatEvent.SpellCast cast
                && !cast.succeeded()
                && cast.targetName().equals("No valid target")));
    }


    @Test
    void awakenCastAnnouncementPrecedesStatusChanges() {
        PlayerRuntimeV5 caster = player("Sage", 400, 20);
        PlayerRuntimeV5 ally = player("Ally", 400, 0);
        ally.setAsleep(true);
        Monster enemy = monster("Orc", 100);
        DataCore data = new DataCore();
        data.setName("Awaken");
        data.setStat(DataLayout.SPELL_MP_COST, 3);
        data.setStat(DataLayout.SPELL_TARGET, Spell.TargetType.SINGLE_ALLY.ordinal());
        data.setStat(DataLayout.SPELL_EFFECT_TYPE, Spell.EffectType.AWAKEN.ordinal());
        CombatRoundV5 combat = new CombatRoundV5(List.of(caster, ally), List.of(enemy),
                java.util.Map.of(), java.util.Map.of(),
                actor -> actor == caster ? 30 : 10, (actor, target) -> true);

        RoundResult result = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.cast(caster, new Spell(data), null, ally)));

        assertEquals(17, caster.mp());
        assertFalse(ally.isAsleep());
        assertTrue(result.events().get(1) instanceof CombatEvent.SpellCast sc
                && sc.succeeded() && sc.spellName().equals("Awaken"));
        assertTrue(result.events().get(2) instanceof CombatEvent.StatusChange change
                && change.entityName().equals("Ally") && !change.applied());
        assertEquals(1, result.events().stream()
                .filter(e -> e instanceof CombatEvent.SpellCast).count());
    }

    @Test
    void awakenAlreadyAwakeAllyHasOneCastAnnouncementAndNoStatusChange() {
        PlayerRuntimeV5 caster = player("Sage", 400, 20);
        PlayerRuntimeV5 ally = player("Ally", 400, 0);
        Monster enemy = monster("Orc", 100);
        DataCore data = new DataCore();
        data.setName("Awaken");
        data.setStat(DataLayout.SPELL_MP_COST, 3);
        data.setStat(DataLayout.SPELL_TARGET, Spell.TargetType.SINGLE_ALLY.ordinal());
        data.setStat(DataLayout.SPELL_EFFECT_TYPE, Spell.EffectType.AWAKEN.ordinal());
        CombatRoundV5 combat = new CombatRoundV5(List.of(caster, ally), List.of(enemy),
                java.util.Map.of(), java.util.Map.of(),
                actor -> actor == caster ? 30 : 10, (actor, target) -> true);

        RoundResult result = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.cast(caster, new Spell(data), null, ally)));
        assertEquals(17, caster.mp());
        assertFalse(ally.isAsleep());
        assertEquals(1, result.events().stream()
                .filter(e -> e instanceof CombatEvent.SpellCast sc && sc.succeeded()).count());
        assertEquals(0, result.events().stream()
                .filter(e -> e instanceof CombatEvent.StatusChange).count());
    }

    @Test
    void sleepCastAnnouncesBeforeEitherResistanceOrStatusChange() {
        PlayerRuntimeV5 caster = player("Sage", 400, 20);
        Monster enemy = monster("Orc", 20);
        DataCore data = new DataCore();
        data.setName("Sleep");
        data.setStat(DataLayout.SPELL_MP_COST, 3);
        data.setStat(DataLayout.SPELL_TARGET, Spell.TargetType.SINGLE_ENEMY.ordinal());
        data.setStat(DataLayout.SPELL_EFFECT_TYPE, Spell.EffectType.SLEEP.ordinal());
        CombatRoundV5 combat = new CombatRoundV5(
                List.of(caster), List.of(enemy), java.util.Map.of(), java.util.Map.of(),
                actor -> actor == caster ? 30 : 10, (actor, target) -> true);

        RoundResult result = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.cast(caster, new Spell(data), enemy, null)));

        assertEquals(17, caster.mp(), "Sleep consumes MP exactly once");
        assertTrue(result.events().get(0) instanceof CombatEvent.RoundHeader);
        assertTrue(result.events().get(1) instanceof CombatEvent.SpellCast cast
                && cast.succeeded() && cast.spellName().equals("Sleep"));
        boolean resisted = result.events().get(2) instanceof CombatEvent.Miss miss
                && miss.attackerName().equals("Sage")
                && miss.targetName().equals("Orc");
        boolean applied = result.events().get(2) instanceof CombatEvent.StatusChange change
                && change.entityName().equals("Orc")
                && change.applied() && change.condition().equals("Asleep");
        assertTrue(resisted || applied, "Each target must resolve as resisted or asleep");
        assertEquals(applied, enemy.isAsleep());
        assertEquals(1, result.events().stream()
                .filter(e -> e instanceof CombatEvent.SpellCast).count());
        assertEquals(20, enemy.getHp(), "Sleep cannot deal physical damage");
    }


    @Test
    void parryIsPreplannedAndExpiresAtEndOfRound() {
        PlayerRuntimeV5 guard = player("Guard", 400, 0);
        Monster enemy = monster("Orc", 20);
        CombatRoundV5 combat = new CombatRoundV5(
                List.of(guard), List.of(enemy), java.util.Map.of(), java.util.Map.of(),
                actor -> actor == enemy ? 30 : 10, (actor, target) -> true);

        RoundResult first = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.parry(guard)));
        assertTrue(first.events().get(0) instanceof CombatEvent.RoundHeader);
        assertTrue(first.events().get(1) instanceof CombatEvent.StatusChange sc
                && sc.entityName().equals("Guard")
                && sc.condition().equals("Parrying") && sc.applied());
        assertTrue(first.events().getLast() instanceof CombatEvent.StatusChange sc
                && sc.entityName().equals("Guard")
                && sc.condition().equals("Parrying") && !sc.applied());
        assertEquals(0, first.events().stream()
                .filter(e -> e instanceof CombatEvent.Hit hit
                        && hit.attackerName().equals("Guard")).count());

        RoundResult second = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.idle(guard)));
        assertEquals(0, second.events().stream()
                .filter(e -> e instanceof CombatEvent.StatusChange sc
                        && sc.condition().equals("Parrying")).count());
        assertEquals(0, guard.mp());
    }

    @Test
    void parryReductionRoundsUpAndRejectsInvalidOrders() {
        assertEquals(0, CombatRoundV5.parriedDamage(0));
        assertEquals(1, CombatRoundV5.parriedDamage(1));
        assertEquals(1, CombatRoundV5.parriedDamage(2));
        assertEquals(2, CombatRoundV5.parriedDamage(3));
        assertEquals(50, CombatRoundV5.parriedDamage(99));
        assertThrows(IllegalArgumentException.class,
                () -> CombatRoundV5.parriedDamage(-1));

        PlayerRuntimeV5 guard = player("Guard", 400, 0);
        Monster enemy = monster("Orc", 20);
        CombatRoundV5 combat = new CombatRoundV5(List.of(guard), List.of(enemy));
        CombatRoundV5.PlayerOrder invalid = new CombatRoundV5.PlayerOrder(
                guard, com.phantasia.core.model.Action.PARRY,
                enemy, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> combat.runRound(List.of(invalid)));
        assertEquals(20, enemy.getHp());
        assertEquals(0, guard.mp());
        assertEquals(1, combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.parry(guard))).roundNumber());
    }

    @Test
    void parryHalvesActualMonsterHitEvenWhenMonsterActsFirst() {
        PlayerRuntimeV5 guard = player("Guard", 400, 0);
        Monster enemy = monster("Orc", 20);
        CombatRoundV5 combat = new CombatRoundV5(
                List.of(guard), List.of(enemy), java.util.Map.of(), java.util.Map.of(),
                actor -> actor == enemy ? 30 : 10,
                (actor, target) -> true,
                (monster, defender) -> true,
                monster -> 9);

        RoundResult defended = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.parry(guard)));
        assertEquals(395, guard.hp(), "Nine incoming damage rounds up to five");
        assertTrue(defended.events().get(0) instanceof CombatEvent.RoundHeader);
        assertTrue(defended.events().get(1) instanceof CombatEvent.StatusChange start
                && start.applied() && start.condition().equals("Parrying"));
        assertTrue(defended.events().get(2) instanceof CombatEvent.Hit hit
                && hit.attackerName().equals("Orc")
                && hit.targetName().equals("Guard") && hit.damage() == 5);
        assertTrue(defended.events().getLast() instanceof CombatEvent.StatusChange end
                && !end.applied() && end.condition().equals("Parrying"));

        RoundResult unguarded = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.idle(guard)));
        assertEquals(386, guard.hp(), "Parry must not carry into the next round");
        assertTrue(unguarded.events().stream().anyMatch(event ->
                event instanceof CombatEvent.Hit hit && hit.damage() == 9
                        && hit.targetName().equals("Guard")));
        assertFalse(unguarded.events().stream().anyMatch(event ->
                event instanceof CombatEvent.StatusChange status
                        && status.condition().equals("Parrying")));
    }

    @Test
    void sleepingCharacterCannotGainParryStance() {
        PlayerRuntimeV5 guard = player("Guard", 400, 0);
        guard.setAsleep(true);
        Monster enemy = monster("Orc", 20);
        CombatRoundV5 combat = new CombatRoundV5(
                List.of(guard), List.of(enemy), java.util.Map.of(), java.util.Map.of(),
                actor -> actor == enemy ? 30 : 10,
                (actor, target) -> true,
                (monster, defender) -> true,
                monster -> 7);

        RoundResult result = combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.parry(guard)));
        assertEquals(393, guard.hp());
        assertFalse(result.events().stream().anyMatch(event ->
                event instanceof CombatEvent.StatusChange status
                        && status.condition().equals("Parrying")));
        assertEquals(0, guard.mp());
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
