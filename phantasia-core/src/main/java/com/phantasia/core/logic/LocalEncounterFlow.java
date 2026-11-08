package com.phantasia.core.logic;

import com.phantasia.core.data.*;
import com.phantasia.core.model.Monster;
import java.util.*;
import java.util.function.Supplier;

/** Per-visit encounter pacing and battle ownership, independent of screens. */
public final class LocalEncounterFlow {
    public enum Outcome { ACTIVE, VICTORY, ESCAPED, DEFEAT }
    private final LocalMapTraversal traversal;
    private final GameSession session;
    private final Supplier<List<Monster>> factory;
    private final Map<Integer,Integer> paces = new HashMap<>();
    private Battle active;
    private long lastMovement;
    public LocalEncounterFlow(LocalMapTraversal traversal, GameSession session, Supplier<List<Monster>> factory) {
        this.traversal=Objects.requireNonNull(traversal); this.session=Objects.requireNonNull(session); this.factory=Objects.requireNonNull(factory);
    }
    public Battle afterStep(LocalMapTraversal.Result step) {
        if (active != null || step.kind() != LocalMapTraversal.Kind.MOVED || traversal.hasExited() || LocalFeatureEffects.partyDefeated(session)) return null;
        long movement=traversal.movementCount();
        if (movement == lastMovement || session.getParty().stream().noneMatch(p -> p.isActiveInParty())) return null;
        lastMovement=movement;
        retryPendingRewards();
        var settings=traversal.map().getSettings(); if (!settings.isEncountersEnabled()) return null;
        if (settings.getEncounterInterval() <= 0) throw new IllegalArgumentException("Encounter interval must be positive");
        int mapId=traversal.map().getId(), next=paces.getOrDefault(mapId,0)+1;
        if (next < settings.getEncounterInterval()) { paces.put(mapId,next); return null; }
        List<Monster> enemies=List.copyOf(factory.get());
        if (enemies.isEmpty() || enemies.stream().anyMatch(e -> !e.isAlive())) throw new IllegalArgumentException("Encounter factory must return living enemies");
        var rewards=new CombatRewards(session,enemies);
        active=new Battle(enemies,rewards); paces.put(mapId,0); traversal.suspendForEncounter(true); return active;
    }
    public int retryPendingRewards() { int claimed=0; for (var reward:session.getPendingCombatRewards()) if (reward.retryGold()) claimed++; return claimed; }
    public Battle active() { return active; }
    public final class Battle {
        private final List<Monster> enemies;
        private final CombatRewards rewards;
        private Outcome outcome=Outcome.ACTIVE;
        private boolean resumed;
        private Battle(List<Monster> enemies,CombatRewards rewards) { this.enemies=enemies;this.rewards=rewards; }
        public List<Monster> enemies() { return enemies; }
        public Outcome outcome() { return outcome; }
        public CombatRewards.Receipt victory() {
            if (outcome == Outcome.ESCAPED || outcome == Outcome.DEFEAT) throw new IllegalStateException("Battle cannot award victory");
            rewards.validateVictory(); outcome=Outcome.VICTORY; return rewards.victory();
        }
        public void resumeVictory() { if (outcome != Outcome.VICTORY) throw new IllegalStateException("Victory has not been resolved"); resume(); }
        public void escape() { finish(Outcome.ESCAPED); }
        public void defeat() { finish(Outcome.DEFEAT); }
        private void finish(Outcome result) {
            if (outcome != Outcome.ACTIVE && outcome != result) throw new IllegalStateException("Battle already resolved");
            outcome=result; resume();
        }
        private void resume() { if (resumed) return; resumed=true; active=null; traversal.suspendForEncounter(false); }
    }
}
