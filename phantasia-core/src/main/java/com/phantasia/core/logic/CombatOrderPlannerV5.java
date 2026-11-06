package com.phantasia.core.logic;

import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerRuntimeV5;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Touchscreen-independent simultaneous-order planning for a v5 encounter.
 * Orders are edited individually, but only a confirmed, complete plan runs.
 * The latest selections remain visible for the next round; confirmation is
 * required again, and stale targets must be corrected.
 */
public final class CombatOrderPlannerV5 {
    private final CombatRoundV5 combat;
    private final List<PlayerRuntimeV5> party;
    private final Map<PlayerRuntimeV5, CombatRoundV5.PlayerOrder> selections =
            new IdentityHashMap<>();

    public CombatOrderPlannerV5(CombatRoundV5 combat) {
        this.combat = Objects.requireNonNull(combat, "combat");
        this.party = combat.partyMembers();
    }

    /** Assign or replace one character's order, without executing it. */
    public void setOrder(CombatRoundV5.PlayerOrder order) {
        Objects.requireNonNull(order, "order");
        if (!containsIdentity(party, order.actor()))
            throw new IllegalArgumentException("Order actor is not in this party");
        selections.put(order.actor(), order);
    }

    /** An empty slot remains a deliberate, unconfirmed choice, not automatic idle. */
    public void clearOrder(PlayerRuntimeV5 actor) {
        requireMember(actor);
        selections.remove(actor);
    }

    public CombatRoundV5.PlayerOrder orderFor(PlayerRuntimeV5 actor) {
        requireMember(actor);
        return selections.get(actor);
    }

    /** State of a single party row in the order-planning view. */
    public enum RowStatus { NEEDS_ORDER, READY, NEEDS_REVIEW, INACTIVE }

    /** Immutable, renderer-neutral snapshot of one character's current selection. */
    public record OrderRow(PlayerRuntimeV5 actor, CombatRoundV5.PlayerOrder order,
                           RowStatus status) {}

    /** One row per party member, in party order. No combat actions are performed. */
    public List<OrderRow> orderRows() {
        List<OrderRow> rows = new ArrayList<>();
        for (PlayerRuntimeV5 member : party) {
            CombatRoundV5.PlayerOrder order = selections.get(member);
            RowStatus status;
            if (!member.isAlive()) {
                status = RowStatus.INACTIVE;
            } else if (order == null) {
                status = RowStatus.NEEDS_ORDER;
            } else if (order.enemyTarget() != null && !order.enemyTarget().isAlive()) {
                status = RowStatus.NEEDS_REVIEW;
            } else {
                try {
                    combat.validateOrders(List.of(order));
                    status = RowStatus.READY;
                } catch (IllegalArgumentException exception) {
                    status = RowStatus.NEEDS_REVIEW;
                }
            }
            rows.add(new OrderRow(member, order, status));
        }
        return List.copyOf(rows);
    }

    /** Confirmation availability for a full, currently valid plan. */
    public boolean canConfirm() {
        if (combat.outcome() != CombatOutcome.ONGOING) return false;
        for (OrderRow row : orderRows()) {
            if (row.status() == RowStatus.NEEDS_ORDER
                    || row.status() == RowStatus.NEEDS_REVIEW)
                return false;
        }
        return true;
    }

    /** Preserves party-row order; omits unassigned slots. */
    public List<CombatRoundV5.PlayerOrder> selectedOrders() {
        List<CombatRoundV5.PlayerOrder> result = new ArrayList<>();
        for (PlayerRuntimeV5 member : party) {
            CombatRoundV5.PlayerOrder order = selections.get(member);
            if (order != null) result.add(order);
        }
        return List.copyOf(result);
    }

    /**
     * Review all active party slots and current battlefield reach before
     * committing. The underlying resolver retains its sparse-order API for
     * headless tests; this UI-facing planner deliberately does not.
     */
    public void validateReady() {
        for (PlayerRuntimeV5 member : party) {
            if (!member.isAlive()) continue;
            CombatRoundV5.PlayerOrder order = selections.get(member);
            if (order == null)
                throw new IllegalStateException("Missing order for " + member.name());
            Monster target = order.enemyTarget();
            if (target != null && !target.isAlive())
                throw new IllegalStateException("Defeated target must be reviewed for "
                        + member.name());
        }
        combat.validateOrders(selectedOrders());
    }

    /** Execute one round only after complete explicit confirmation. */
    public RoundResult confirmRound() {
        validateReady();
        return combat.runRound(selectedOrders());
    }

    private void requireMember(PlayerRuntimeV5 actor) {
        if (!containsIdentity(party, actor))
            throw new IllegalArgumentException("Not a party member");
    }

    private static <T> boolean containsIdentity(List<T> values, T value) {
        for (T element : values) if (element == value) return true;
        return false;
    }
}
