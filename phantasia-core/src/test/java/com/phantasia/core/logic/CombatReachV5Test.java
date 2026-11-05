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

class CombatReachV5Test {
    private static PlayerRuntimeV5 player(String name) {
        return new PlayerRuntimeV5(PlayerCreationV5.create(
                name, 1, Job.FIGHTER, 0, 20, 20, 10, 120, 10));
    }

    private static Monster monster(String name) {
        DataCore record = new DataCore();
        record.setName(name);
        record.setStat(DataLayout.MON_HP, 100);
        return new Monster(record);
    }

    @Test
    void exposedEnemyRankBlocksDeeperMeleeUntilGuardFalls() {
        PlayerRuntimeV5 front = player("Guard");
        PlayerRuntimeV5 middle = player("Sage");
        PlayerRuntimeV5 back = player("Archer");
        Monster shield = monster("Shield");
        Monster rear = monster("Rear");
        CombatRoundV5 combat = new CombatRoundV5(
                List.of(front, middle, back), List.of(shield, rear),
                Map.of(front, CombatRank.FRONT, middle, CombatRank.MIDDLE,
                        back, CombatRank.BACK),
                Map.of(shield, CombatRank.FRONT, rear, CombatRank.BACK));

        assertEquals(CombatRank.BACK, combat.enemyRankOf(rear));
        assertTrue(combat.canMeleeReach(front, shield));
        assertTrue(combat.canMeleeReach(middle, shield));
        assertFalse(combat.canMeleeReach(front, rear));
        assertFalse(combat.canMeleeReach(back, shield));

        assertThrows(IllegalArgumentException.class, () -> combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.attack(front, rear, CombatRank.FRONT))));
        assertThrows(IllegalArgumentException.class, () -> combat.runRound(List.of(
                CombatRoundV5.PlayerOrder.attack(back, shield, CombatRank.BACK))));
        assertEquals(100, shield.getHp());
        assertEquals(100, rear.getHp());

        shield.setHp(0);
        assertTrue(combat.canMeleeReach(front, rear));
        assertTrue(combat.canMeleeReach(middle, rear));
        assertFalse(combat.canMeleeReach(back, rear));
    }

    @Test
    void invalidEnemyRankOrNonEncounterTargetIsRejected() {
        PlayerRuntimeV5 front = player("Guard");
        Monster actual = monster("Actual");
        Monster stranger = monster("Stranger");
        assertThrows(IllegalArgumentException.class, () ->
                new CombatRoundV5(List.of(front), List.of(actual),
                        Map.of(), Map.of(stranger, CombatRank.MIDDLE)));
        CombatRoundV5 defaults = new CombatRoundV5(List.of(front), List.of(actual));
        assertEquals(CombatRank.FRONT, defaults.enemyRankOf(actual));
        assertThrows(IllegalArgumentException.class, () -> defaults.enemyRankOf(stranger));
        assertThrows(IllegalArgumentException.class,
                () -> defaults.runRound(List.of(
                        CombatRoundV5.PlayerOrder.attack(front, stranger, CombatRank.FRONT))));
        assertEquals(100, actual.getHp());
    }
}
