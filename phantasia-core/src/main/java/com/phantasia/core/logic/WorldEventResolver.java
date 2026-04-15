package com.phantasia.core.logic;

import com.phantasia.core.world.FeatureRecord;
import com.phantasia.core.world.FeatureRegistry;
import com.phantasia.core.world.Tile;
import com.phantasia.core.world.WorldFeature;

import java.util.Optional;

/**
 * Inspects a tile and returns the WorldEvent it should fire, or empty
 * if the tile has nothing significant to report.
 *
 * Priority order:
 *   1. ScriptedEncounter  — guardian battle on this specific tile
 *   2. TileEvent          — altar, chest, door, inscription
 *   3. WorldFeature       — TOWN and DUNGEON transitions
 *   4. Nothing            — plain traversal tile
 *
 * FEATURE NAME RESOLUTION:
 *   Feature names are resolved from FeatureRegistry (features.dat).
 *   If no registry is provided, names fall back to "TYPE_id" strings.
 *   Use the two-argument constructor for all runtime use once
 *   features.dat has been baked.
 *
 * USAGE:
 *   // Without registry (fallback names):
 *   WorldEventResolver resolver = new WorldEventResolver();
 *
 *   // With registry (real names from features.dat):
 *   WorldEventResolver resolver = new WorldEventResolver(featureRegistry);
 *
 *   Optional<WorldEvent> event = resolver.resolve(tile);
 */
public class WorldEventResolver {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** May be null — names fall back gracefully if not provided. */
    private final FeatureRegistry featureRegistry;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * No-registry constructor — feature names fall back to "TYPE_id".
     * Safe to use when features.dat has not yet been loaded.
     */
    public WorldEventResolver() {
        this.featureRegistry = null;
    }

    /**
     * Registry constructor — feature names resolved from features.dat.
     * Preferred for all runtime use once WorldMapBaker has been run.
     *
     * @param featureRegistry  the loaded registry, or null for fallback
     */
    public WorldEventResolver(FeatureRegistry featureRegistry) {
        this.featureRegistry = featureRegistry;
    }

    // -------------------------------------------------------------------------
    // Primary API
    // -------------------------------------------------------------------------

    /**
     * Inspects the given tile and returns the WorldEvent it should fire,
     * or empty if the tile has nothing significant to report.
     *
     * @param tile  the tile the party just stepped onto (never null)
     * @return      the event to fire, or Optional.empty() for plain tiles
     */
    public Optional<WorldEvent> resolve(Tile tile) {

        // Priority 1: scripted encounter (specific guardian on this tile)
        if (tile.hasScriptedEncounter()) {
            ScriptedEncounterResult result = resolveScriptedEncounter(tile);
            if (result != null) return Optional.of(result.event());
        }

        // Priority 2: interactive tile event (altar, chest, door, inscription)
        if (tile.hasTileEvent()) {
            return Optional.of(new WorldEvent.TileEventPrompt(tile.getTileEvent()));
        }

        // Priority 3: named feature (town or dungeon transition)
        if (tile.hasFeature()) {
            return resolveFeature(tile);
        }

        // Priority 4: nothing remarkable
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Scripted encounter resolution
    // -------------------------------------------------------------------------

    /**
     * Attempts to trigger the tile's ScriptedEncounter.
     * Returns null if the encounter has already been exhausted (one-shot
     * boss already defeated). This call advances the encounter's state.
     */
    private ScriptedEncounterResult resolveScriptedEncounter(Tile tile) {
        var scripted = tile.getScriptedEncounter();
        if (!scripted.trigger()) return null;

        WorldEvent event = new WorldEvent.ScriptedBattle(
                scripted.getMonsterName(),
                scripted.getCount(),
                scripted.isRepeatable()
        );
        return new ScriptedEncounterResult(event);
    }

    private record ScriptedEncounterResult(WorldEvent event) {}

    // -------------------------------------------------------------------------
    // Feature resolution
    // -------------------------------------------------------------------------

    /**
     * Translates a WorldFeature into the appropriate WorldEvent.
     * Returns empty for feature types not yet implemented so the
     * frontend falls through gracefully.
     */
    private Optional<WorldEvent> resolveFeature(Tile tile) {
        var feature = tile.getFeature();
        return switch (feature.getType()) {
            case TOWN    -> Optional.of(
                    new WorldEvent.EnterTown(feature.getId(), resolveName(feature)));
            case DUNGEON -> Optional.of(
                    new WorldEvent.EnterDungeon(feature.getId(), resolveName(feature)));
            case NPC     -> Optional.of(
                    new WorldEvent.NpcInteraction(
                            String.valueOf(feature.getId()), resolveName(feature)));
            default      -> Optional.empty();
        };
    }

    /**
     * Resolves the display name for a feature.
     * Uses FeatureRegistry if available, falls back to "TYPE_id" otherwise.
     */
    private String resolveName(WorldFeature feature) {
        if (featureRegistry != null) {
            FeatureRecord record = featureRegistry.get(feature.getType(), feature.getId());
            if (record != null) return record.getName();
        }
        return feature.getType().name() + "_" + feature.getId();
    }
}