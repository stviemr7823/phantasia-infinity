// phantasia-core/src/main/java/com/phantasia/core/world/TerrainMetadataService.java
package com.phantasia.core.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * The "Imagination Bridge" — converts the flat 2D tile grid into rich
 * terrain metadata that 3D and 2.5D frontends consume.
 *
 * <h2>The Stravinsky Algorithm</h2>
 *
 * Named after the composer's technique of building a coherent structure from
 * three distinct layers — theme, harmony, and dissonance — the pipeline
 * produces organic mountain terrain from purely semantic tile data.
 *
 * <h3>Phase 1 — The Theme: Distance Transform</h3>
 * A BFS flood-fill from every OCEAN tile computes the minimum distance
 * to the nearest ocean for every land tile. This raw distance forms the
 * baseline topographic mass: tiles deep inland (far from ocean) are tall;
 * coastal tiles are flat.
 *
 * <h3>Phase 2 — The Harmony: Logarithmic Scaling</h3>
 * The raw distance (linear) is remapped through a logarithmic curve:
 * <pre>
 *   height = log(1 + raw × k) / log(1 + k),  k = LOG_STEEPNESS
 * </pre>
 * This produces steep bases (the curve rises quickly from sea level)
 * and organic high-altitude plateaus (the curve flattens near the top).
 * MOUNTAIN tiles receive an additional boost before the log is applied.
 *
 * <h3>Phase 3 — The Dissonance: Worley / Cellular Noise</h3>
 * A set of random feature points is scattered across the land mass.
 * For each tile, the distance to the nearest feature point (F1) drives
 * a perturbation that introduces "beautiful mistakes" — sheer fault lines
 * where the height drops suddenly, and obsidian spires where it rises.
 * Only land tiles within a height range are perturbed (deep ocean and
 * very high peaks are left clean).
 *
 * <h3>Phase 4 — Slope Computation</h3>
 * For each tile, the maximum height difference with its eight neighbours
 * gives the slope. Normalised to [0, 1].
 *
 * <h3>Phase 5 — Surface Classification</h3>
 * Each tile receives a {@link SurfaceType} based on slope and height:
 * <ul>
 *   <li>Slope &gt; {@value #CLIFF_SLOPE} → OBSIDIAN (cliff face)</li>
 *   <li>Slope &gt; {@value #ROCK_SLOPE} → ROCK (steep approach)</li>
 *   <li>Height &gt; {@value #SNOW_LINE} → SNOW (high peak)</li>
 *   <li>Near ocean ({@link TileType#SWAMP} or coastal) → MOSS</li>
 *   <li>Height &lt; {@value #SAND_CEILING} (beach fringe) → SAND</li>
 *   <li>Otherwise → GRASS</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * {@code TerrainMetadataService} is stateless. {@link #compute(WorldMap)} is
 * safe to call from any thread with no synchronisation required.
 */
public class TerrainMetadataService {

    // -------------------------------------------------------------------------
    // Tuning constants
    // -------------------------------------------------------------------------

    /** Logarithmic steepness parameter (k). Higher = steeper base, flatter top. */
    private static final float LOG_STEEPNESS   = 6.0f;

    /** Extra height boost applied to MOUNTAIN tiles before log scaling. */
    private static final float MOUNTAIN_BOOST  = 0.4f;

    /** Weight of the Worley noise perturbation (0 = none, 1 = full). */
    private static final float WORLEY_WEIGHT   = 0.28f;

    /** Slope threshold (normalised) for OBSIDIAN surface classification. */
    private static final float CLIFF_SLOPE     = 0.52f;

    /** Slope threshold for ROCK surface classification. */
    private static final float ROCK_SLOPE      = 0.28f;

    /** Height threshold above which SNOW replaces other surfaces. */
    private static final float SNOW_LINE       = 0.82f;

    /** Height below which coastal fringe is classified as SAND. */
    private static final float SAND_CEILING    = 0.12f;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Runs the full Stravinsky pipeline on the given world map.
     *
     * <p>The result is deterministic for the same map input — the RNG is
     * seeded from the map dimensions so Worley feature points land in the
     * same positions every time.</p>
     *
     * @param map  the world map to process
     * @return a {@link TerrainMetadata} covering every tile in the map
     */
    public TerrainMetadata compute(WorldMap map) {
        final int W = map.getWidth();
        final int H = map.getHeight();

        // Phase 1 — Distance transform (raw height 0–1, linear)
        float[][] raw = distanceTransform(map, W, H);

        // Phase 2 — Logarithmic scaling + MOUNTAIN boost
        float[][] height = applyLogScale(raw, map, W, H);

        // Phase 3 — Worley noise dissonance
        applyWorleyNoise(height, map, W, H, (long)(W * H));

        // Phase 4 — Slope computation (normalised 0–1)
        float[][] slope = computeSlope(height, W, H);

        // Phase 5 — Surface classification
        TerrainCell[][] cells = classifySurfaces(height, slope, map, W, H);

        System.out.printf(
                "[TerrainMetadataService] Computed %dx%d terrain. " +
                        "Max height=%.3f  Avg height=%.3f%n",
                W, H,
                maxOf(height, W, H), avgOf(height, W, H));

        return new TerrainMetadata(cells, W, H);
    }

    // -------------------------------------------------------------------------
    // Phase 1 — Distance transform
    // -------------------------------------------------------------------------

    private float[][] distanceTransform(WorldMap map, int W, int H) {
        int[][] dist = new int[W][H];
        final int INF = Integer.MAX_VALUE / 2;

        // Seed the BFS with all OCEAN tiles at distance 0
        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (map.getTile(x, y).getType() == TileType.OCEAN) {
                    dist[x][y] = 0;
                    queue.add(new int[]{x, y});
                } else {
                    dist[x][y] = INF;
                }
            }
        }

        // 4-directional BFS
        int[] DX = {1, -1, 0, 0};
        int[] DY = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int cx = cell[0], cy = cell[1];
            for (int d = 0; d < 4; d++) {
                int nx = cx + DX[d], ny = cy + DY[d];
                if (nx < 0 || nx >= W || ny < 0 || ny >= H) continue;
                int nd = dist[cx][cy] + 1;
                if (nd < dist[nx][ny]) {
                    dist[nx][ny] = nd;
                    queue.add(new int[]{nx, ny});
                }
            }
        }

        // Find max distance for normalisation
        int maxDist = 1;
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++)
                if (dist[x][y] != INF && dist[x][y] > maxDist)
                    maxDist = dist[x][y];

        // Normalise to [0, 1]
        float[][] raw = new float[W][H];
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++)
                raw[x][y] = (dist[x][y] == INF)
                        ? 0f
                        : (float) dist[x][y] / maxDist;

        return raw;
    }

    // -------------------------------------------------------------------------
    // Phase 2 — Logarithmic scaling
    // -------------------------------------------------------------------------

    private float[][] applyLogScale(float[][] raw, WorldMap map, int W, int H) {
        float[][] height = new float[W][H];
        float logDenom = (float) Math.log1p(LOG_STEEPNESS);

        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                TileType type = map.getTile(x, y).getType();

                if (type == TileType.OCEAN) {
                    height[x][y] = 0f;
                    continue;
                }

                float r = raw[x][y];

                // MOUNTAIN tiles receive a pre-scale boost
                if (type == TileType.MOUNTAIN) {
                    r = Math.min(1f, r + MOUNTAIN_BOOST);
                }

                // Log curve: steep base, flat plateau
                height[x][y] = (float)(Math.log1p(r * LOG_STEEPNESS) / logDenom);
            }
        }
        return height;
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Worley / Cellular noise
    // -------------------------------------------------------------------------

    private void applyWorleyNoise(float[][] height, WorldMap map,
                                  int W, int H, long seed) {
        // Number of Worley feature points — roughly one per 8 tiles of land
        int numPoints = Math.max(6, (W * H) / 8);
        Random rng    = new Random(seed);

        float[] fpx = new float[numPoints];
        float[] fpy = new float[numPoints];

        // Place feature points on land tiles only
        int placed = 0;
        int attempts = 0;
        while (placed < numPoints && attempts < numPoints * 10) {
            float fx = rng.nextFloat() * W;
            float fy = rng.nextFloat() * H;
            int ix = (int) fx, iy = (int) fy;
            if (ix >= 0 && ix < W && iy >= 0 && iy < H
                    && map.getTile(ix, iy).getType() != TileType.OCEAN) {
                fpx[placed] = fx;
                fpy[placed] = fy;
                placed++;
            }
            attempts++;
        }
        // Fill any unplaced points with random land positions
        float maxDiag = (float) Math.sqrt(W * W + H * H);

        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (map.getTile(x, y).getType() == TileType.OCEAN) continue;

                float h = height[x][y];
                // Only perturb mid-range heights — leave very flat and very
                // high tiles clean (beach fringes and plateau peaks)
                if (h < 0.08f || h > 0.88f) continue;

                // F1 — distance to nearest feature point
                float f1 = Float.MAX_VALUE;
                for (int i = 0; i < placed; i++) {
                    float dx = x - fpx[i], dy = y - fpy[i];
                    float d  = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d < f1) f1 = d;
                }

                // Normalise F1 to [0, 1] using a local radius
                float localRadius = maxDiag * 0.25f;
                float f1Norm      = Math.min(1f, f1 / localRadius);

                // Perturbation: dips near feature points, rises further away
                // This creates fault valleys and subtle ridges between them
                float perturbation = (f1Norm - 0.5f) * 2f * WORLEY_WEIGHT;
                height[x][y] = Math.max(0f, Math.min(1f, h + perturbation * h));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 4 — Slope computation
    // -------------------------------------------------------------------------

    private float[][] computeSlope(float[][] height, int W, int H) {
        float[][] slope = new float[W][H];
        float maxSlope  = 0f;

        int[] DX = {1, -1, 0, 0, 1, -1, 1, -1};
        int[] DY = {0, 0, 1, -1, 1, -1, -1, 1};

        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                float h   = height[x][y];
                float max = 0f;
                for (int d = 0; d < 8; d++) {
                    int nx = x + DX[d], ny = y + DY[d];
                    if (nx < 0 || nx >= W || ny < 0 || ny >= H) continue;
                    float diff = Math.abs(h - height[nx][ny]);
                    if (diff > max) max = diff;
                }
                slope[x][y] = max;
                if (max > maxSlope) maxSlope = max;
            }
        }

        // Normalise
        if (maxSlope > 0f) {
            for (int x = 0; x < W; x++)
                for (int y = 0; y < H; y++)
                    slope[x][y] /= maxSlope;
        }

        return slope;
    }

    // -------------------------------------------------------------------------
    // Phase 5 — Surface classification
    // -------------------------------------------------------------------------

    private TerrainCell[][] classifySurfaces(float[][] height, float[][] slope,
                                             WorldMap map, int W, int H) {
        TerrainCell[][] cells = new TerrainCell[W][H];

        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                float     h   = height[x][y];
                float     s   = slope[x][y];
                TileType  tt  = map.getTile(x, y).getType();

                SurfaceType surface;

                if (tt == TileType.OCEAN) {
                    // Ocean tiles — no surface (use SAND as a sentinel)
                    surface = SurfaceType.SAND;
                } else if (s > CLIFF_SLOPE) {
                    surface = SurfaceType.OBSIDIAN;
                } else if (h > SNOW_LINE) {
                    surface = SurfaceType.SNOW;
                } else if (s > ROCK_SLOPE) {
                    surface = SurfaceType.ROCK;
                } else if (h < SAND_CEILING) {
                    surface = SurfaceType.SAND;
                } else if (tt == TileType.SWAMP
                        || tt == TileType.FOREST) {
                    surface = SurfaceType.MOSS;
                } else {
                    surface = SurfaceType.GRASS;
                }

                cells[x][y] = new TerrainCell(h, s, surface);
            }
        }

        return cells;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private static float maxOf(float[][] arr, int W, int H) {
        float m = 0f;
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++)
                if (arr[x][y] > m) m = arr[x][y];
        return m;
    }

    private static float avgOf(float[][] arr, int W, int H) {
        double s = 0;
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++)
                s += arr[x][y];
        return (float)(s / (W * H));
    }
}
