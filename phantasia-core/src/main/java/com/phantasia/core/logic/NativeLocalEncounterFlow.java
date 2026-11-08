package com.phantasia.core.logic;

import com.phantasia.core.model.Monster;
import java.util.*;
import java.util.function.*;

/** Per-visit pacing that suspends traversal while the native campaign owns combat. */
public final class NativeLocalEncounterFlow {
    private final LocalMapTraversal traversal;
    private final NativeCampaignV5 campaign;
    private final Supplier<List<Monster>> enemies;
    private final ToIntFunction<Monster> agility;
    private final Predicate<com.phantasia.core.model.PlayerRuntimeV5> escapeRoll;
    private final Map<Integer, Integer> paces = new HashMap<>();
    private long lastMovement;
    private NativeCampaignV5.Battle active;
    public NativeLocalEncounterFlow(LocalMapTraversal traversal, NativeCampaignV5 campaign,
                                    Supplier<List<Monster>> enemies, ToIntFunction<Monster> agility) {
        this(traversal, campaign, enemies, agility, FormulaEngine::rollEscape);
    }
    NativeLocalEncounterFlow(LocalMapTraversal traversal, NativeCampaignV5 campaign,
                             Supplier<List<Monster>> enemies, ToIntFunction<Monster> agility,
                             Predicate<com.phantasia.core.model.PlayerRuntimeV5> escapeRoll) {
        this.traversal = Objects.requireNonNull(traversal); this.campaign = Objects.requireNonNull(campaign);
        this.enemies = Objects.requireNonNull(enemies); this.agility = Objects.requireNonNull(agility);
        this.escapeRoll = Objects.requireNonNull(escapeRoll);
        if (traversal.session() != campaign) throw new IllegalArgumentException("Traversal belongs to another party");
    }
    public NativeCampaignV5 campaign() { return campaign; }
    public NativeCampaignV5.Battle active() { return active; }
    public record Snapshot(Map<Integer, Integer> paces, long lastMovement) {
        public Snapshot { paces = Map.copyOf(paces); }
    }
    public Snapshot snapshot() {
        if (active != null || campaign.activeBattle() != null) throw new IllegalStateException("Acknowledge combat before saving");
        return new Snapshot(paces, lastMovement);
    }
    public static NativeLocalEncounterFlow restore(LocalMapTraversal traversal, NativeCampaignV5 campaign,
            Supplier<List<Monster>> enemies, ToIntFunction<Monster> agility, Snapshot saved,
            com.phantasia.core.data.InteriorMapRegistry registry) {
        if (saved.lastMovement() < 0 || saved.lastMovement() > traversal.movementCount() || saved.paces().size() > 10000)
            throw new IllegalArgumentException("Invalid encounter checkpoint");
        var flow = new NativeLocalEncounterFlow(traversal, campaign, enemies, agility);
        saved.paces().forEach((id, count) -> {
            var map = registry.get(id);
            if (map == null || count < 0 || count >= Math.max(1, map.getSettings().getEncounterInterval()))
                throw new IllegalArgumentException("Invalid encounter pace count");
            flow.paces.put(id, count);
        });
        flow.lastMovement = saved.lastMovement(); return flow;
    }
    public NativeCampaignV5.Battle afterStep(LocalMapTraversal.Result step) {
        if (active != null || campaign.activeBattle() != null || traversal.hasExited()
                || LocalFeatureEffects.partyDefeated(campaign) || step.kind() != LocalMapTraversal.Kind.MOVED
                || lastMovement == traversal.movementCount()) return null;
        lastMovement = traversal.movementCount(); campaign.retryGold();
        var settings = traversal.map().getSettings();
        if (!settings.isEncountersEnabled()) return null;
        if (settings.getEncounterInterval() <= 0) throw new IllegalArgumentException("Encounter interval must be positive");
        int id = traversal.map().getId(), count = paces.getOrDefault(id, 0) + 1;
        if (count < settings.getEncounterInterval()) { paces.put(id, count); return null; }
        active = campaign.startBattle(enemies.get(), agility, escapeRoll);
        paces.put(id, 0); traversal.suspendForEncounter(true); return active;
    }
    /** Caller retains the receipt identity; stale UI results cannot release a newer battle. */
    public void returnFromBattle(NativeCampaignV5.Battle battle) {
        if (active == null) return;
        if (active != battle) throw new IllegalStateException("Result belongs to another encounter");
        battle.acknowledge(); active = null; traversal.suspendForEncounter(false);
    }
}
