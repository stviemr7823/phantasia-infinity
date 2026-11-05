package com.phantasia.core.logic;

import com.phantasia.core.model.CombatRank;
import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.Job;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerCreationV5;
import com.phantasia.core.model.PlayerRuntimeV5;
import com.phantasia.core.model.item.EquipSlot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatRangedV5Test {
    private static PlayerRuntimeV5 player(String name) {
        return new PlayerRuntimeV5(PlayerCreationV5.create(
                name, 1, Job.RANGER, 0, 12, 30, 8, 1000, 0));
    }

    private static Monster monster(String name) {
        DataCore record = new DataCore();
        record.setName(name);
        record.setStat(DataLayout.MON_HP, 200);
        return new Monster(record);
    }

    @Test
    void equippedBowCanTargetProtectedRearEnemyFromBackRank() {
        PlayerRuntimeV5 archer = player("Archer");
        archer.equip(EquipSlot.BOW, 44);
        Monster front = monster("Front");
        Monster rear = monster("Rear");
        CombatRoundV5 round = new CombatRoundV5(
                List.of(archer), List.of(front, rear),
                Map.of(archer, CombatRank.BACK),
                Map.of(front, CombatRank.FRONT, rear, CombatRank.BACK));

        assertFalse(round.canMeleeReach(archer, front));
        assertTrue(round.canRangedReach(archer, rear));
        RoundResult result = round.runRound(List.of(
                CombatRoundV5.PlayerOrder.ranged(archer, rear)));
        assertEquals(1, result.roundNumber());
        assertTrue(result.events().stream().anyMatch(event ->
                (event instanceof CombatEvent.Hit hit
                    && hit.attackerName().equals("Archer")
                    && hit.targetName().equals("Rear"))
                || (event instanceof CombatEvent.Miss miss
                    && miss.attackerName().equals("Archer")
                    && miss.targetName().equals("Rear"))));
        assertEquals(200, front.getHp(), "Protected front monster was not the selected target");
        assertEquals(44, archer.equippedItemId(EquipSlot.BOW));
        assertEquals(0L, archer.experience());
    }

    @Test
    void missingBowAndForeignTargetRejectBeforeRoundProgress() {
        PlayerRuntimeV5 archer = player("Archer");
        Monster front = monster("Front");
        Monster stranger = monster("Stranger");
        CombatRoundV5 round = new CombatRoundV5(List.of(archer), List.of(front));

        assertFalse(round.canRangedReach(archer, front));
        assertThrows(IllegalArgumentException.class, () ->
                round.runRound(List.of(CombatRoundV5.PlayerOrder.ranged(archer, front))));
        assertEquals(200, front.getHp());

        archer.equip(EquipSlot.BOW, 41);
        assertThrows(IllegalArgumentException.class, () ->
                round.runRound(List.of(CombatRoundV5.PlayerOrder.ranged(archer, stranger))));
        assertThrows(IllegalArgumentException.class, () ->
                round.runRound(List.of(
                    CombatRoundV5.PlayerOrder.ranged(archer, front),
                    CombatRoundV5.PlayerOrder.ranged(archer, front))));
        assertEquals(200, front.getHp());
        RoundResult result = round.runRound(List.of(CombatRoundV5.PlayerOrder.idle(archer)));
        assertEquals(1, result.roundNumber(), "Rejected plans cannot consume a round");
    }

    @Test
    void rangedAttackRejectsMeleeEquipmentAsBow() {
        PlayerRuntimeV5 archer = player("Archer");
        Monster target = monster("Target");
        archer.equip(EquipSlot.WEAPON, 72);
        CombatRoundV5 round = new CombatRoundV5(List.of(archer), List.of(target));
        assertFalse(round.canRangedReach(archer, target));
        assertThrows(IllegalArgumentException.class, () ->
                round.runRound(List.of(CombatRoundV5.PlayerOrder.ranged(archer, target))));
        assertEquals(200, target.getHp());
    }
}
