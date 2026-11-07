package com.phantasia.core.logic;

import com.phantasia.core.data.*;
import com.phantasia.core.model.*;
import com.phantasia.core.model.item.*;
import com.phantasia.core.world.*;
import com.phantasia.core.world.ServiceData.TrapData;
import java.util.*;
import java.util.random.RandomGenerator;

/** Chest/trap effects shared by every authored area. Uses existing party facades;
 * does not change combat, inventory capacity or training rules.
 */
public final class LocalFeatureEffects {
    public enum Status { OPENED, TRIGGERED, AVOIDED, UNAVAILABLE, INVENTORY_FULL, GOLD_LIMIT, DEFEATED }
    public record Damage(PlayerCharacter member, int before, int after) { public int amount() { return before - after; } }
    public record Result(Status status, int gold, List<LootTable.LootEntry> items, List<Damage> damage, String message) {
        public Result { items = List.copyOf(items); damage = List.copyOf(damage); }
    }
    private record Slot(PlayerCharacter member, int slot) {}
    private LocalFeatureEffects() {}
    public static boolean partyDefeated(GameSession session) {
        return !session.getParty().isEmpty() && session.getParty().stream().noneMatch(PlayerCharacter::isActiveInParty);
    }
    public static Result unavailable(String text) { return result(Status.UNAVAILABLE, text); }
    private static Result result(Status status, String text) { return new Result(status, 0, List.of(), List.of(), text); }
    private static List<PlayerCharacter> members(GameSession session) {
        Set<PlayerCharacter> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        return session.getParty().stream().filter(seen::add).toList();
    }
    public static Result apply(GameSession session, int mapId, PlacedFeature feature, RandomGenerator random) {
        Objects.requireNonNull(session); Objects.requireNonNull(random);
        if (partyDefeated(session)) return result(Status.DEFEATED, "The party has fallen.");
        if (session.getParty().isEmpty()) return unavailable("There is no party to interact with this feature.");
        if (feature == null || !feature.isActive(session::hasFlag)
                || feature.consumedFlag() != null && session.hasFlag(feature.consumedFlag())) return unavailable("This interaction is already resolved or unavailable.");
        if (feature.featureType() == PlacedFeatureType.CHEST && feature.serviceData() instanceof LootTable loot)
            return chest(session, mapId, feature, loot, random);
        if (feature.featureType() == PlacedFeatureType.TRAP && feature.serviceData() instanceof TrapData trap)
            return trap(session, feature, trap, random);
        return unavailable("This feature has no chest or trap effect.");
    }
    private static Result chest(GameSession session, int mapId, PlacedFeature feature, LootTable table, RandomGenerator random) {
        if (feature.consumedFlag() == null) return unavailable("Treasure needs an authored completion flag.");
        var state = session.getLocalInteractions(); var key = new LocalInteractionState.Key(mapId, feature.x(), feature.y());
        var loot = state.pending(key);
        if (loot == null) {
            try { loot = table.evaluate(random); }
            catch (IllegalArgumentException ex) { return unavailable("Invalid treasure: " + ex.getMessage()); }
            state.remember(key, loot);
        }
        var slots = new ArrayList<Slot>();
        for (var member : members(session)) for (int slot = 0; slot < Inventory.MAX_ITEMS; slot++)
            if (member.getInventoryItemId(slot) == 0) slots.add(new Slot(member, slot));
        long needed = 0;
        for (var entry : loot.items()) needed += entry.quantity();
        if (needed > slots.size()) return result(Status.INVENTORY_FULL, "Make room for " + needed + " item(s). The chest is unchanged.");
        if (session.getLedger().getPartyGold() < 0 || (long) session.getLedger().getPartyGold() + loot.gold() > Integer.MAX_VALUE)
            return result(Status.GOLD_LIMIT, "The party cannot carry this much gold. The chest is unchanged.");
        // All payload, capacity and overflow checks precede mutation. Consume before
        // the gold notification so reentrant UI listeners cannot claim the chest twice.
        int next = 0;
        for (var entry : loot.items()) for (int quantity = 0; quantity < entry.quantity(); quantity++) {
            var slot = slots.get(next++); slot.member().setInventoryItem(slot.slot(), entry.itemId());
        }
        session.setFlag(feature.consumedFlag()); state.forget(key);
        session.getLedger().addGold(loot.gold());
        return new Result(Status.OPENED, loot.gold(), loot.items(), List.of(), "Treasure collected.");
    }
    private static Result trap(GameSession session, PlacedFeature feature, TrapData trap, RandomGenerator random) {
        if (trap.damageMin() < 0 || trap.damageMax() < trap.damageMin() || trap.detectSkill() < 0)
            return unavailable("Invalid trap damage range or detection threshold.");
        var living = members(session).stream().filter(PlayerCharacter::isAlive).toList();
        if (living.isEmpty()) return unavailable("No living party member is affected by this trap.");
        if (trap.detectSkill() > 0 && living.stream().anyMatch(p -> p.getStat(Stat.DEXTERITY) >= trap.detectSkill())) {
            consume(session, feature);
            return result(Status.AVOIDED, "Trap detected and avoided.");
        }
        var damage = new ArrayList<Damage>();
        for (var member : living) {
            int rolled = (int) (trap.damageMin() + (trap.damageMax() > trap.damageMin()
                    ? random.nextLong((long) trap.damageMax() - trap.damageMin() + 1) : 0));
            damage.add(new Damage(member, member.getHp(), Math.max(0, member.getHp() - rolled)));
        }
        for (var hit : damage) hit.member().setHp(hit.after());
        consume(session, feature);
        return new Result(partyDefeated(session) ? Status.DEFEATED : Status.TRIGGERED, 0, List.of(), damage,
                trap.message() == null || trap.message().isBlank() ? "A trap springs!" : trap.message());
    }
    private static void consume(GameSession session, PlacedFeature feature) {
        if (feature.consumedFlag() != null) session.setFlag(feature.consumedFlag());
    }
}
