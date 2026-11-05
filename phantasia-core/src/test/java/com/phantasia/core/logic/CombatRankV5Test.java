package com.phantasia.core.logic;

import com.phantasia.core.model.CombatRank;
import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.Job;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerCreationV5;
import com.phantasia.core.model.PlayerRuntimeV5;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatRankV5Test {
    private static PlayerRuntimeV5 player(String name, int hp) {
        return new PlayerRuntimeV5(PlayerCreationV5.create(
                name, 1, Job.FIGHTER, 0, 14, 12, 8, hp, 0));
    }

    private static Monster monster(int hp) {
        DataCore record = new DataCore();
        record.setName("Ogre");
        record.setStat(DataLayout.MON_HP, hp);
        return new Monster(record);
    }

    @Test
    void assignedRanksCannotBeSpoofedByAttackOrders() {
        PlayerRuntimeV5 front = player("Front", 100);
        PlayerRuntimeV5 back = player("Back", 100);
        Monster enemy = monster(100);
        CombatRoundV5 round = new CombatRoundV5(
                List.of(back, front), List.of(enemy),
                Map.of(back, CombatRank.BACK, front, CombatRank.FRONT));

        assertEquals(CombatRank.BACK, round.rankOf(back));
        assertEquals(CombatRank.FRONT, round.rankOf(front));
        assertThrows(IllegalArgumentException.class, () -> round.runRound(List.of(
                CombatRoundV5.PlayerOrder.attack(back, enemy, CombatRank.FRONT))));
        assertEquals(100, enemy.getHp(), "Rejected orders must not deal damage");

        // An idle round is accepted after the invalid order, proving that
        // validation did not increment the round counter.
        RoundResult result = round.runRound(List.of(
                CombatRoundV5.PlayerOrder.idle(front),
                CombatRoundV5.PlayerOrder.idle(back)));
        assertEquals(1, result.roundNumber());
    }

    @Test
    void monstersPreferFrontThenFallBackAfterFrontIsDefeated() {
        PlayerRuntimeV5 rear = player("Rear", 100);
        PlayerRuntimeV5 front = player("Front", 1);
        Monster enemy = monster(255);
        CombatRoundV5 round = new CombatRoundV5(
                List.of(rear, front), List.of(enemy),
                Map.of(rear, CombatRank.BACK, front, CombatRank.FRONT));

        for (int i = 0; i < 100 && front.isAlive(); i++)
            round.runRound(List.of());
        assertFalse(front.isAlive(), "The monster must eventually defeat the front guard");
        assertEquals(100, rear.hp(),
                "The rear fighter should not be attacked while the front is alive");

        boolean attackedRear = false;
        for (int i = 0; i < 100 && !attackedRear; i++) {
            round.runRound(List.of());
            attackedRear = rear.hp() < 100;
        }
        assertTrue(attackedRear, "The monster must move to the rear when the front falls");
    }

    @Test
    void invalidRankAssignmentsAreRejectedBeforeCombat() {
        PlayerRuntimeV5 member = player("Party", 30);
        PlayerRuntimeV5 stranger = player("Stranger", 30);
        Monster enemy = monster(20);
        assertThrows(IllegalArgumentException.class, () ->
                new CombatRoundV5(List.of(member), List.of(enemy),
                        Map.of(stranger, CombatRank.FRONT)));
        CombatRoundV5 defaults = new CombatRoundV5(List.of(member), List.of(enemy));
        assertEquals(CombatRank.FRONT, defaults.rankOf(member));
        assertThrows(IllegalArgumentException.class, () -> defaults.rankOf(stranger));
    }
}
