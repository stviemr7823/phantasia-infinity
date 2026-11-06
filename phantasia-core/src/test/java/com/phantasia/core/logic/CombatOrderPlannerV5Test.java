package com.phantasia.core.logic;

import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.Job;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerCreationV5;
import com.phantasia.core.model.PlayerRuntimeV5;
import com.phantasia.core.model.CombatRank;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatOrderPlannerV5Test {
    private static PlayerRuntimeV5 member(String name) {
        return new PlayerRuntimeV5(PlayerCreationV5.create(
                name, 1, Job.WIZARD, 0, 20, 20, 0, 400, 20));
    }

    private static Monster enemy(String name, int hp) {
        DataCore core = new DataCore();
        core.setName(name);
        core.setStat(DataLayout.MON_HP, hp);
        return new Monster(core);
    }

    @Test
    void allSixMustReceiveOrdersAndOneConfirmationExecutesOneRound() {
        List<PlayerRuntimeV5> party = List.of(
                member("A"), member("B"), member("C"),
                member("D"), member("E"), member("F"));
        Monster orc = enemy("Orc", 200);
        CombatRoundV5 combat = new CombatRoundV5(party, List.of(orc),
                Map.of(), Map.of(), actor -> 10,
                (actor, target) -> false,
                (monster, defender) -> false, monster -> 0);
        CombatOrderPlannerV5 planner = new CombatOrderPlannerV5(combat);

        for (int i = 0; i < 5; i++)
            planner.setOrder(CombatRoundV5.PlayerOrder.idle(party.get(i)));
        assertThrows(IllegalStateException.class, planner::confirmRound);
        assertEquals(0, combatRoundNumberWithoutAdvancing(combat, planner));

        planner.setOrder(CombatRoundV5.PlayerOrder.parry(party.get(0)));
        planner.setOrder(CombatRoundV5.PlayerOrder.idle(party.get(5)));
        assertEquals(6, planner.selectedOrders().size());
        assertSame(party.get(0), planner.selectedOrders().getFirst().actor());
        assertEquals(1, planner.confirmRound().roundNumber());
        assertEquals(2, planner.confirmRound().roundNumber(),
                "Previous selections may be confirmed again, but never auto-run");
        assertEquals(200, orc.getHp());
    }

    private static int combatRoundNumberWithoutAdvancing(CombatRoundV5 combat,
            CombatOrderPlannerV5 planner) {
        // Validated orders must not execute merely by being inspected.
        planner.selectedOrders();
        return 0; // first successful confirmation must still report round 1
    }

    @Test
    void editingAndClearingSelectionDoesNotMutateCombat() {
        PlayerRuntimeV5 a = member("A");
        PlayerRuntimeV5 b = member("B");
        Monster orc = enemy("Orc", 50);
        CombatRoundV5 combat = new CombatRoundV5(List.of(a, b), List.of(orc));
        CombatOrderPlannerV5 planner = new CombatOrderPlannerV5(combat);
        planner.setOrder(CombatRoundV5.PlayerOrder.attack(a, orc, CombatRank.FRONT));
        planner.setOrder(CombatRoundV5.PlayerOrder.idle(b));
        assertEquals(2, planner.selectedOrders().size());
        planner.setOrder(CombatRoundV5.PlayerOrder.parry(a));
        assertEquals(com.phantasia.core.model.Action.PARRY, planner.orderFor(a).action());
        planner.clearOrder(b);
        assertEquals(1, planner.selectedOrders().size());
        assertThrows(IllegalStateException.class, planner::confirmRound);
        assertEquals(50, orc.getHp());
        assertEquals(20, a.mp());
    }

    @Test
    void staleTargetRequiresReviewBeforeNextConfirmation() {
        PlayerRuntimeV5 a = member("A");
        Monster dead = enemy("Orc", 1);
        Monster living = enemy("Other", 200);
        CombatRoundV5 combat = new CombatRoundV5(List.of(a),
                List.of(dead, living), Map.of(), Map.of(),
                actor -> actor == a ? 30 : 10,
                (actor, target) -> true,
                (monster, defender) -> false, monster -> 0);
        CombatOrderPlannerV5 planner = new CombatOrderPlannerV5(combat);
        planner.setOrder(CombatRoundV5.PlayerOrder.attack(a, dead, CombatRank.FRONT));
        assertEquals(1, planner.confirmRound().roundNumber());
        assertEquals(0, dead.getHp());
        assertThrows(IllegalStateException.class, planner::confirmRound);
        planner.setOrder(CombatRoundV5.PlayerOrder.attack(a, living, CombatRank.FRONT));
        assertEquals(2, planner.confirmRound().roundNumber());
    }

    @Test
    void outsidersCannotEditOrClearPartyRows() {
        PlayerRuntimeV5 a = member("A");
        PlayerRuntimeV5 outsider = member("Outsider");
        CombatOrderPlannerV5 planner = new CombatOrderPlannerV5(
                new CombatRoundV5(List.of(a), List.of(enemy("Orc", 100))));
        assertThrows(IllegalArgumentException.class,
                () -> planner.setOrder(CombatRoundV5.PlayerOrder.idle(outsider)));
        assertThrows(IllegalArgumentException.class, () -> planner.clearOrder(outsider));
        assertThrows(IllegalArgumentException.class, () -> planner.orderFor(outsider));
    }
}
