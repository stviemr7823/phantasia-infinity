// phantasia-core/src/main/java/com/phantasia/core/world/WfcPlacementService.java
package com.phantasia.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Wave Function Collapse-inspired placement service for overworld decorations.
 *
 * <h2>Algorithm</h2>
 *
 * Standard WFC operates on a grid where each cell has a set of possible
 * states and adjacency constraints. This service adapts the core ideas
 * to the specific problem of <em>decoration placement</em>: choosing whether
 * to place a decoration, what type, and where within each tile — producing
 * organic clustering without a separate authored tileset of adjacency rules.
 *
 * <h3>Phase 1 — Base probability map</h3>
 * For each land tile, compute a base decoration probability from:
 * <ul>
 *   <li>The tile's {@link TileType} biome affinity</li>
 *   <li>{@link TerrainMetadata} slope — steep tiles get rock instead of
 *       vegetation; very steep tiles get nothing (cliffs)</li>
 * </ul>
 * OCEAN, ROAD, TOWN and DUNGEON tiles always have probability 0.
 *
 * <h3>Phase 2 — Neighbourhood smoothing (WFC-like propagation)</h3>
 * The base probability at each tile is adjusted by its 8 neighbours.
 * A tile surrounded by high-probability tiles gets a density boost,
 * clustering decorations naturally (forest cores are denser than edges).
 * A single isolated tile retains its base probability.
 *
 * <h3>Phase 3 — Sampling with sub-tile jitter</h3>
 * Each tile is sampled against its final probability. On a hit:
 * <ul>
 *   <li>Choose the decoration type appropriate to biome + slope</li>
 *   <li>Apply sub-tile jitter so objects don't sit on grid centres</li>
 *   <li>Vary scale and rotation for visual interest</li>
 *   <li>High-density tiles (forest) may spawn 1–3 items</li>
 * </ul>
 *
 * <h3>Determinism</h3>
 * The RNG is seeded from {@code worldWidth * worldHeight * seed}, so the
 * same map always produces the same decorations across sessions.
 *
 * <h3>Thread safety</h3>
 * Stateless — {@link #compute(WorldMap, TerrainMetadata)} is safe to call
 * from any thread.
 */
public class WfcPlacementService {

    // -------------------------------------------------------------------------
    // Tuning constants
    // -------------------------------------------------------------------------

    /** Multiplier applied from high-density neighbours during smoothing. */
    private static final float NEIGHBOUR_BOOST = 0.12f;

    /** Maximum boost from neighbours (8 × NEIGHBOUR_BOOST = 0.96 — capped). */
    private static final float MAX_BOOST       = 0.40f;

    /** Slope above which no vegetation is placed (cliff face). */
    private static final float CLIFF_SLOPE     = 0.55f;

    /** Slope above which only rocks appear (no trees/grass). */
    private static final float ROCKY_SLOPE     = 0.30f;

    /** Maximum sub-tile jitter in tile units (keeps objects inside their tile). */
    private static final float JITTER          = 0.38f;

    /** Scale variation half-range — objects range from (1-VAR) to (1+VAR). */
    private static final float SCALE_VAR       = 0.25f;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Computes the full decoration placement for the given world map.
     *
     * @param map      the overworld tile grid
     * @param terrain  pre-computed terrain metadata (slope + surface type);
     *                 pass {@code null} to skip slope-aware selection
     * @return an immutable {@link PlacementData} containing all placements
     */
    public PlacementData compute(WorldMap map, TerrainMetadata terrain) {
        return compute(map, terrain, 0L);
    }

    /**
     * Seeded variant — useful for testing or deterministic map generation.
     */
    public PlacementData compute(WorldMap map, TerrainMetadata terrain, long seed) {
        int W = map.getWidth();
        int H = map.getHeight();

        Random rng = new Random((long)(W * H) ^ (seed * 6364136223846793005L + 1));

        // Phase 1 — base probability per tile
        float[][] prob = buildBaseProbabilities(map, terrain, W, H);

        // Phase 2 — neighbourhood smoothing (WFC clustering pass)
        prob = smoothProbabilities(prob, W, H);

        // Phase 3 — sample and generate placements
        List<PlacementEntry> entries = sample(prob, map, terrain, rng, W, H);

        System.out.printf("[WfcPlacementService] Placed %d decorations on %dx%d map.%n",
                entries.size(), W, H);

        return new PlacementData(entries);
    }

    // -------------------------------------------------------------------------
    // Phase 1 — Base probability map
    // -------------------------------------------------------------------------

    private float[][] buildBaseProbabilities(WorldMap map, TerrainMetadata terrain,
                                             int W, int H) {
        float[][] prob = new float[W][H];

        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                TileType type = map.getTile(x, y).getType();

                // Non-decoratable tiles
                if (type == TileType.OCEAN || type == TileType.ROAD
                        || type == TileType.TOWN || type == TileType.DUNGEON) {
                    prob[x][y] = 0f;
                    continue;
                }

                // Slope gating from terrain metadata
                float slope = (terrain != null) ? terrain.getSlope(x, y) : 0f;
                if (slope >= CLIFF_SLOPE) {
                    prob[x][y] = 0f;    // bare cliff — nothing grows here
                    continue;
                }

                prob[x][y] = baseProbabilityFor(type, slope);
            }
        }

        return prob;
    }

    private static float baseProbabilityFor(TileType type, float slope) {
        float base = switch (type) {
            case FOREST   -> 0.72f;
            case PLAINS   -> 0.18f;
            case MOUNTAIN -> 0.35f;
            case SWAMP    -> 0.28f;
            default       -> 0f;
        };

        // Rocky slope: reduce vegetation probability, keep rock probability
        if (slope > ROCKY_SLOPE) {
            if (type == TileType.MOUNTAIN) {
                // More rocks on steep mountain
                base = 0.50f;
            } else {
                // Vegetation reduces on slopes
                base *= (1f - (slope - ROCKY_SLOPE) / (CLIFF_SLOPE - ROCKY_SLOPE));
            }
        }

        return base;
    }

    // -------------------------------------------------------------------------
    // Phase 2 — Neighbourhood smoothing
    // -------------------------------------------------------------------------

    private float[][] smoothProbabilities(float[][] prob, int W, int H) {
        float[][] smoothed = new float[W][H];
        int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] DY = {-1, -1, -1, 0, 0, 1, 1, 1};

        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (prob[x][y] == 0f) {
                    smoothed[x][y] = 0f;
                    continue;
                }

                // Sum non-zero neighbour probabilities for boost
                float neighbourSum = 0f;
                for (int d = 0; d < 8; d++) {
                    int nx = x + DX[d], ny = y + DY[d];
                    if (nx >= 0 && nx < W && ny >= 0 && ny < H) {
                        if (prob[nx][ny] > 0f) neighbourSum += prob[nx][ny];
                    }
                }

                float boost = Math.min(MAX_BOOST, neighbourSum * NEIGHBOUR_BOOST);
                smoothed[x][y] = Math.min(0.95f, prob[x][y] + boost);
            }
        }

        return smoothed;
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Sampling
    // -------------------------------------------------------------------------

    private List<PlacementEntry> sample(float[][] prob, WorldMap map,
                                        TerrainMetadata terrain, Random rng,
                                        int W, int H) {
        List<PlacementEntry> entries = new ArrayList<>();

        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                float p = prob[x][y];
                if (p <= 0f) continue;

                TileType type  = map.getTile(x, y).getType();
                float    slope = (terrain != null) ? terrain.getSlope(x, y) : 0f;

                // Determine how many items this tile might spawn
                // Forest tiles can hold 1–3, others at most 1
                int maxCount = (type == TileType.FOREST) ? 3 : 1;

                for (int i = 0; i < maxCount; i++) {
                    // Each attempt is an independent roll — decreasing
                    // probability for subsequent items in the same tile
                    float roll = rng.nextFloat();
                    float threshold = p * (1f - i * 0.35f);
                    if (roll > threshold) break;

                    DecorationType dType = chooseType(type, slope, rng);
                    if (dType == null) continue;

                    // Sub-tile jitter (Y-up: y increases northward)
                    float jx = x + 0.5f + (rng.nextFloat() * 2f - 1f) * JITTER;
                    float jy = y + 0.5f + (rng.nextFloat() * 2f - 1f) * JITTER;

                    float scale = 1f + (rng.nextFloat() * 2f - 1f) * SCALE_VAR;
                    float rot   = rng.nextFloat() * 360f;

                    entries.add(new PlacementEntry(jx, jy, dType, scale, rot));
                }
            }
        }

        return entries;
    }

    /**
     * Selects the most appropriate decoration type for a given tile's biome
     * and slope. Returns {@code null} if no suitable type exists (shouldn't
     * normally happen for non-zero probability tiles).
     */
    private static DecorationType chooseType(TileType tile, float slope, Random rng) {
        return switch (tile) {
            case FOREST -> {
                // Steep forest → dead trees; mid → pine; flat → oak mix
                if (slope > ROCKY_SLOPE)  yield pick(rng, DecorationType.TREE_DEAD, DecorationType.FALLEN_LOG);
                float r = rng.nextFloat();
                if (r < 0.55f) yield DecorationType.TREE_OAK;
                if (r < 0.80f) yield DecorationType.TREE_PINE;
                if (r < 0.92f) yield DecorationType.FALLEN_LOG;
                yield DecorationType.MUSHROOM;
            }
            case PLAINS -> {
                float r = rng.nextFloat();
                if (r < 0.45f) yield DecorationType.GRASS_TALL;
                if (r < 0.65f) yield DecorationType.SHRUB;
                if (r < 0.82f) yield DecorationType.FLOWER_YELLOW;
                yield DecorationType.FLOWER_BLUE;
            }
            case MOUNTAIN -> {
                // Steep → larger rocks; gentler → small scattered rocks
                if (slope > ROCKY_SLOPE) yield pick(rng, DecorationType.ROCK_LARGE, DecorationType.BOULDER);
                yield pick(rng, DecorationType.ROCK_SMALL, DecorationType.ROCK_LARGE);
            }
            case SWAMP -> {
                float r = rng.nextFloat();
                if (r < 0.45f) yield DecorationType.REED;
                if (r < 0.75f) yield DecorationType.MOSS_PATCH;
                yield DecorationType.DEAD_TREE;
            }
            default -> null;
        };
    }

    /** Randomly picks one of two options with equal probability. */
    private static DecorationType pick(Random rng, DecorationType a, DecorationType b) {
        return rng.nextBoolean() ? a : b;
    }
}
