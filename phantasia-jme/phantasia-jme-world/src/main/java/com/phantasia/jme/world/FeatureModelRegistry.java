// phantasia-jme/phantasia-jme-world/src/main/java/com/phantasia/jme/world/FeatureModelRegistry.java
package com.phantasia.jme.world;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.phantasia.core.world.FeatureRecord;
import com.phantasia.core.world.FeatureRegistry;
import com.phantasia.core.world.FeatureType;
import com.phantasia.core.world.WorldFeature;

import java.util.logging.Logger;

/**
 * Resolves WorldFeature tile references into JME scene graph nodes.
 *
 * ASSET RESOLUTION — three tiers in priority order:
 *
 *   1. Authored .j3o model — path comes from FeatureAssetManifest.
 *      The manifest maps TYPE_ID keys (e.g. "TOWN_0") to asset paths.
 *      When a hand-authored model is ready, add it to features.manifest
 *      and it will be picked up automatically with no code changes.
 *
 *   2. Procedural geometry — built by TownModelBuilder or
 *      DungeonModelBuilder, parameterised by feature id so each
 *      feature gets a distinct silhouette deterministically.
 *
 *   3. Colored box fallback — only if the procedural builder throws.
 *
 * DEPENDENCIES:
 *   - FeatureAssetManifest — loaded from assets/features.manifest
 *   - FeatureRegistry      — loaded from data/features.dat, provides
 *                            display names to builders
 *
 * USAGE (from WorldState.initialize()):
 *   featureModelRegistry = new FeatureModelRegistry(app.getAssetManager());
 *   featureModelRegistry.loadManifest("assets/features.manifest");
 *   featureModelRegistry.setCoreRegistry(coreRegistry);
 *
 * USAGE (from WorldState.buildFeatureMarkers()):
 *   Node marker = featureModelRegistry.buildMarker(feature, TILE_SIZE);
 *   if (marker != null) {
 *       marker.setLocalTranslation(wx, wy, wz);
 *       featureNode.attachChild(marker);
 *   }
 */
public final class FeatureModelRegistry {

    private static final Logger LOG =
            Logger.getLogger(FeatureModelRegistry.class.getName());

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final AssetManager         assetManager;
    private final FeatureAssetManifest manifest    = new FeatureAssetManifest();
    private       FeatureRegistry      coreRegistry = null;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public FeatureModelRegistry(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    /**
     * Loads the frontend manifest that maps TYPE_ID keys to .j3o asset paths.
     * Safe to call with a path that doesn't exist — falls back silently.
     */
    public void loadManifest(String manifestPath) {
        manifest.load(manifestPath);
    }

    /**
     * Wires in the core FeatureRegistry loaded from features.dat.
     * Used to resolve display names for procedural builders.
     * Optional — builders fall back to generic names if not set.
     */
    public void setCoreRegistry(FeatureRegistry registry) {
        this.coreRegistry = registry;
    }

    // -------------------------------------------------------------------------
    // Primary API
    // -------------------------------------------------------------------------

    /**
     * Builds and returns a Node representing the given feature, positioned
     * at the local origin. The caller is responsible for translating it to
     * the correct world position above the terrain.
     *
     * Returns null for NONE features and unrecognised types — caller skips.
     *
     * @param feature  the WorldFeature tile reference (type + id)
     * @param tileSize the current TILE_SIZE from WorldState, used to scale
     *                 procedural geometry proportionally
     */
    public Node buildMarker(WorldFeature feature, float tileSize) {
        if (feature == null || feature.isNone()) return null;
        if (feature.getType() == FeatureType.NPC)     return null;

        // Resolve display name from core registry if available
        String displayName = resolveDisplayName(feature);

        // --- Tier 1: authored .j3o from manifest ---
        String assetHint = manifest.getAssetHint(feature);
        if (assetHint != null) {
            Spatial authored = tryLoadModel(assetHint);
            if (authored != null) {
                LOG.info("[FeatureModelRegistry] Loaded authored model: " + assetHint
                        + " for " + displayName);
                Node node = new Node("Feature_" + feature.getType() + "_" + feature.getId());
                node.attachChild(authored);
                return node;
            } else {
                LOG.warning("[FeatureModelRegistry] Manifest listed '"
                        + assetHint + "' but asset not found — trying procedural.");
            }
        }

        // --- Tier 2: convention-based .j3o path (no manifest entry needed) ---
        String conventionPath = conventionAssetPath(feature);
        Spatial convention = tryLoadModel(conventionPath);
        if (convention != null) {
            LOG.info("[FeatureModelRegistry] Loaded convention model: "
                    + conventionPath + " for " + displayName);
            Node node = new Node("Feature_" + feature.getType() + "_" + feature.getId());
            node.attachChild(convention);
            return node;
        }

        // --- Tier 3: procedural geometry ---
        LOG.info("[FeatureModelRegistry] Building procedural marker for "
                + feature.getType() + " id=" + feature.getId() + " (" + displayName + ")");
        try {
            return switch (feature.getType()) {
                case TOWN    -> TownModelBuilder.build(feature, tileSize, assetManager);
                case DUNGEON -> DungeonModelBuilder.build(feature, tileSize, assetManager);
                default      -> null;
            };
        } catch (Exception e) {
            LOG.warning("[FeatureModelRegistry] Procedural builder failed for "
                    + feature + ": " + e.getMessage()
                    + " — using box fallback.");
            return buildBoxFallback(feature, tileSize);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the display name for a feature from the core FeatureRegistry.
     * Falls back to a generic "TYPE_ID" string if the registry is not loaded
     * or the record is not found.
     */
    private String resolveDisplayName(WorldFeature feature) {
        if (coreRegistry != null) {
            FeatureRecord record = coreRegistry.get(feature.getType(), feature.getId());
            if (record != null) return record.getName();
        }
        return feature.getType().name() + "_" + feature.getId();
    }

    /**
     * Convention-based asset path — allows dropping models into the asset
     * folder without adding a manifest entry.
     * Convention: Models/Features/TYPE_ID.j3o
     * e.g. Models/Features/TOWN_0.j3o for Pendragon.
     */
    private static String conventionAssetPath(WorldFeature feature) {
        return "Models/Features/" + feature.getType().name() + "_" + feature.getId() + ".j3o";
    }

    /**
     * Attempts to load a model from the asset manager.
     * Returns null if the asset does not exist or fails to load —
     * never throws, so the caller can safely fall through to the next tier.
     */
    private Spatial tryLoadModel(String path) {
        try {
            return assetManager.loadModel(path);
        } catch (Exception e) {
            return null; // expected when asset doesn't exist yet
        }
    }

    /**
     * Last-resort colored box fallback — matches the old placeholder behavior.
     * Gold for towns, purple for dungeons.
     */
    private Node buildBoxFallback(WorldFeature feature, float tileSize) {
        com.jme3.scene.shape.Box mesh =
                new com.jme3.scene.shape.Box(
                        tileSize * 0.35f,
                        tileSize * 0.35f,
                        tileSize * 0.35f);
        com.jme3.scene.Geometry geom =
                new com.jme3.scene.Geometry(
                        "FallbackMarker_" + feature.getId(), mesh);
        com.jme3.material.Material mat =
                new com.jme3.material.Material(
                        assetManager, "Common/MatDefs/Misc/Unshaded.j3md");

        com.jme3.math.ColorRGBA color = feature.isTown()
                ? new com.jme3.math.ColorRGBA(1.0f, 0.90f, 0.20f, 1f)
                : new com.jme3.math.ColorRGBA(0.70f, 0.10f, 0.80f, 1f);
        mat.setColor("Color", color);
        geom.setMaterial(mat);

        Node node = new Node("FallbackFeature_" + feature.getType() + "_" + feature.getId());
        node.attachChild(geom);
        geom.setLocalTranslation(0, tileSize * 0.35f, 0);
        return node;
    }
}