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

        planner.setOrder(CombatRoundV5.PlayerOrder.parry(party.get(0)));
        planner.setOrder(CombatRoundV5.PlayerOrder.idle(party.get(5)));
        assertEquals(6, planner.selectedOrders().size());
        assertSame(party.get(0), planner.selectedOrders().getFirst().actor());
        assertEquals(1, planner.confirmRound().roundNumber());
        assertEquals(2, planner.confirmRound().roundNumber(),
                "Previous selections may be confirmed again, but never auto-run");
        assertEquals(200, orc.getHp());
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
    void statusRowsTrackMissingReadyAndStaleSelectionsWithoutRunningCombat() {
        PlayerRuntimeV5 first = member("First");
        PlayerRuntimeV5 second = member("Second");
        Monster target = enemy("Orc", 50);
        Monster survivor = enemy("Survivor", 100);
        CombatRoundV5 combat = new CombatRoundV5(
                List.of(first, second), List.of(target, survivor));
        CombatOrderPlannerV5 planner = new CombatOrderPlannerV5(combat);

        assertEquals(List.of(CombatOrderPlannerV5.RowStatus.NEEDS_ORDER,
                        CombatOrderPlannerV5.RowStatus.NEEDS_ORDER),
                planner.orderRows().stream().map(CombatOrderPlannerV5.OrderRow::status).toList());
        assertFalse(planner.canConfirm());

        planner.setOrder(CombatRoundV5.PlayerOrder.attack(first, target, CombatRank.FRONT));
        planner.setOrder(CombatRoundV5.PlayerOrder.parry(second));
        assertEquals(CombatOrderPlannerV5.RowStatus.READY, planner.orderRows().get(0).status());
        assertEquals(CombatOrderPlannerV5.RowStatus.READY, planner.orderRows().get(1).status());
        assertTrue(planner.canConfirm());

        target.applyDamage(50);
        assertEquals(CombatOrderPlannerV5.RowStatus.NEEDS_REVIEW,
                planner.orderRows().get(0).status());
        assertFalse(planner.canConfirm());
        assertEquals(CombatOrderPlannerV5.RowStatus.READY, planner.orderRows().get(1).status());
        planner.setOrder(CombatRoundV5.PlayerOrder.attack(first, survivor, CombatRank.FRONT));
        assertTrue(planner.canConfirm());
        assertEquals(20, first.mp());
        assertEquals(100, survivor.getHp());
        assertSame(first, planner.orderRows().get(0).actor());
    }

    @Test
    void invalidReachNeedsReviewAndEndedCombatHasInactiveRows() {
        PlayerRuntimeV5 fighter = member("Fighter");
        Monster front = enemy("Front", 100);
        Monster rear = enemy("Rear", 100);
        CombatRoundV5 combat = new CombatRoundV5(
                List.of(fighter), List.of(front, rear), Map.of(),
                Map.of(front, CombatRank.FRONT, rear, CombatRank.BACK),
                actor -> actor == fighter ? 30 : 10,
                (actor, target) -> true,
                (monster, defender) -> false,
                monster -> 0);
        CombatOrderPlannerV5 planner = new CombatOrderPlannerV5(combat);
        planner.setOrder(CombatRoundV5.PlayerOrder.attack(fighter, rear, CombatRank.FRONT));
        assertEquals(CombatOrderPlannerV5.RowStatus.NEEDS_REVIEW,
                planner.orderRows().getFirst().status());
        assertFalse(planner.canConfirm());
        planner.setOrder(CombatRoundV5.PlayerOrder.parry(fighter));
        assertTrue(planner.canConfirm());

        // A separate completed encounter still yields a readable status snapshot.
        PlayerRuntimeV5 winner = member("Winner");
        Monster weak = enemy("Weak", 1);
        CombatRoundV5 shortCombat = new CombatRoundV5(
                List.of(winner), List.of(weak), Map.of(), Map.of(),
                actor -> actor == winner ? 30 : 10,
                (actor, target) -> true,
                (monster, defender) -> false,
                monster -> 0);
        CombatOrderPlannerV5 finished = new CombatOrderPlannerV5(shortCombat);
        finished.setOrder(CombatRoundV5.PlayerOrder.attack(winner, weak, CombatRank.FRONT));
        assertEquals(CombatOutcome.VICTORY, finished.confirmRound().outcome());
        assertEquals(CombatOrderPlannerV5.RowStatus.INACTIVE,
                finished.orderRows().getFirst().status());
        assertFalse(finished.canConfirm());
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
