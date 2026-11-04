// phantasia-core/src/main/java/com/phantasia/core/world/DecorationType.java
package com.phantasia.core.world;

/**
 * Renderer-agnostic catalogue of overworld decoration objects.
 *
 * <p>Produced by {@link WfcPlacementService} and consumed by frontends:
 * <ul>
 *   <li>j2d — draws coloured shapes as stand-ins until sprite assets exist</li>
 *   <li>JME — maps each type to a 3D model path (e.g. "Models/Tree/Oak.j3o")</li>
 *   <li>libGDX — maps to a TextureAtlas region name</li>
 * </ul>
 *
 * <h3>Biome affinity</h3>
 * Each value declares a preferred {@link TileType} via {@link #biome()}.
 * The placement service uses this to filter candidates per tile, but
 * the affinity is not exclusive — edge variants can appear on adjacent biomes.
 */
public enum DecorationType {

    // ── Forest ───────────────────────────────────────────────────────────────

    /** Large deciduous tree. Primary forest fill. */
    TREE_OAK    (TileType.FOREST, 0.9f, 1.4f),

    /** Tall conifer. Appears at higher-altitude forest. */
    TREE_PINE   (TileType.FOREST, 0.7f, 1.6f),

    /** Dead/twisted tree. Forest edges and cursed areas. */
    TREE_DEAD   (TileType.FOREST, 0.4f, 1.0f),

    /** Fallen log. Low profile, forest floor. */
    FALLEN_LOG  (TileType.FOREST, 0.3f, 0.6f),

    /** Mushroom cluster. Damp forest shade. */
    MUSHROOM    (TileType.FOREST, 0.2f, 0.3f),

    // ── Plains ───────────────────────────────────────────────────────────────

    /** Tall grass patch. Scattered across open plains. */
    GRASS_TALL  (TileType.PLAINS, 0.6f, 0.5f),

    /** Yellow wildflower. Bright accent. */
    FLOWER_YELLOW (TileType.PLAINS, 0.4f, 0.3f),

    /** Blue wildflower. Rare. */
    FLOWER_BLUE   (TileType.PLAINS, 0.3f, 0.3f),

    /** Small shrub. Plains/forest edge. */
    SHRUB       (TileType.PLAINS, 0.4f, 0.5f),

    // ── Mountain ─────────────────────────────────────────────────────────────

    /** Small scattered rock. Mountain approach. */
    ROCK_SMALL  (TileType.MOUNTAIN, 0.6f, 0.4f),

    /** Larger angular rock. Mid slope. */
    ROCK_LARGE  (TileType.MOUNTAIN, 0.4f, 0.8f),

    /** Boulder. Near cliff bases, rare. */
    BOULDER     (TileType.MOUNTAIN, 0.2f, 1.2f),

    // ── Swamp ────────────────────────────────────────────────────────────────

    /** Tall reed cluster. Water's edge. */
    REED        (TileType.SWAMP, 0.5f, 0.7f),

    /** Moss-covered rock. Damp surfaces. */
    MOSS_PATCH  (TileType.SWAMP, 0.4f, 0.4f),

    /** Gnarled dead tree rising from swamp water. */
    DEAD_TREE   (TileType.SWAMP, 0.3f, 1.1f);

    // ──────────────────────────────────────────────────────────────────────────

    /** The tile type this decoration most naturally appears on. */
    public final TileType biome;

    /**
     * Base appearance probability on the home biome tile [0.0, 1.0].
     * Lower values produce rarer, more impactful decorations.
     */
    public final float baseProbability;

    /**
     * Typical world-space height scale relative to one tile unit.
     * Used by frontends to set model/sprite scale for visual proportion.
     */
    public final float heightScale;

    DecorationType(TileType biome, float baseProbability, float heightScale) {
        this.biome           = biome;
        this.baseProbability = baseProbability;
        this.heightScale     = heightScale;
    }

    /**
     * Returns true if this decoration type can appear on the given tile type.
     * Allows edge blending — a forest-biome decoration may also appear on
     * adjacent plains tiles at reduced probability.
     */
    public boolean canAppearOn(TileType tile) {
        if (tile == biome) return true;
        // Edge blending rules
        return switch (this) {
            case TREE_OAK, FALLEN_LOG -> tile == TileType.PLAINS; // forest edge
            case SHRUB                -> tile == TileType.FOREST;  // plains/forest
            case ROCK_SMALL           -> tile == TileType.PLAINS;  // mountain foot
            case MOSS_PATCH           -> tile == TileType.FOREST;  // wet forest
            case REED                 -> tile == TileType.PLAINS;  // swamp edge
            default                  -> false;
        };
    }

    /**
     * Returns a j2d-friendly fallback colour for this decoration (0xRRGGBB).
     * Used when no sprite asset is loaded.
     */
    public int fallbackRgb() {
        return switch (this) {
            case TREE_OAK, TREE_PINE  -> 0x1A5C1A;
            case TREE_DEAD, DEAD_TREE -> 0x4A3A2A;
            case FALLEN_LOG           -> 0x6B4C1E;
            case MUSHROOM             -> 0x8B3A1A;
            case GRASS_TALL           -> 0x3A7A2A;
            case FLOWER_YELLOW        -> 0xD4AA00;
            case FLOWER_BLUE          -> 0x3A6AAA;
            case SHRUB                -> 0x2A5A1A;
            case ROCK_SMALL, ROCK_LARGE, BOULDER -> 0x7A7060;
            case REED                 -> 0x5A8A3A;
            case MOSS_PATCH           -> 0x2A5A2A;
        };
    }
    public TileType biome()           { return biome;           }
    public float    baseProbability() { return baseProbability; }
    public float    heightScale()     { return heightScale;     }

}
