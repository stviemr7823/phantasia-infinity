// phantasia-editor/src/main/java/com/phantasia/editor/generators/WorldMapGenerator.java
package com.phantasia.editor.generators;

import com.phantasia.core.world.*;

import java.util.*;

/**
 * Procedural overworld map generator.
 *
 * Creates a starting world map with realistic terrain distribution:
 * ocean borders, continental landmasses, mountain ranges, forests,
 * swamps, roads connecting placed towns, and feature placement for
 * towns and dungeons.
 *
 * Algorithm:
 * <ol>
 *   <li>Fill with ocean</li>
 *   <li>Generate continental landmass using cellular automata</li>
 *   <li>Apply elevation noise to distribute mountains</li>
 *   <li>Apply moisture noise to distribute forests and swamps</li>
 *   <li>Place towns at desirable locations (near coast, inland)</li>
 *   <li>Place dungeons in remote/dangerous areas</li>
 *   <li>Connect towns with roads (A* pathfinding)</li>
 *   <li>Set the party start position near the first town</li>
 * </ol>
 */
public class WorldMapGenerator {

    private static final Random RNG = new Random();

    /**
     * Configuration for world generation.
     */
    public record Config(
            int    width,          // map width (32–128)
            int    height,         // map height (32–128)
            double landMass,       // fraction of map that is land (0.3–0.7)
            int    townCount,      // number of towns to place (2–8)
            int    dungeonCount,   // number of dungeons (1–6)
            double forestDensity,  // forest coverage on land (0.0–0.5)
            double mountainDensity,// mountain coverage on land (0.0–0.3)
            double swampDensity,   // swamp coverage on land (0.0–0.2)
            long   seed            // random seed (-1 for random)
    ) {
        /** Reasonable defaults for a Phantasia world. */
        public static Config defaults() {
            return new Config(64, 64, 0.45, 4, 2, 0.20, 0.12, 0.08, -1);
        }
    }

    /**
     * Generation result — the tile grid plus placed feature metadata.
     */
    public record Result(
            TileType[][]     terrain,
            WorldFeature[][] features,
            WorldPosition    startPos,
            int              width,
            int              height,
            List<PlacedTown> towns,
            List<PlacedDungeon> dungeons
    ) {}

    public record PlacedTown(int id, int x, int y, String name) {}
    public record PlacedDungeon(int id, int x, int y, String name) {}

    // ── Town name pool ───────────────────────────────────────────────────
    private static final String[] TOWN_NAMES = {
            "Pendragon", "Scandor", "Loftwood", "Ironhaven", "Brighthollow",
            "Ashveil", "Thornwall", "Crystalford", "Stormhaven", "Duskmeadow"
    };
    private static final String[] DUNGEON_NAMES = {
            "Frostpeak Cavern", "Sunken Vault", "Shadow Crypt", "Dragon's Maw",
            "The Howling Pit", "Tomb of Whispers", "Iron Mines", "Abyssal Rift"
    };

    /**
     * Generates a complete world map.
     */
    public static Result generate(Config cfg) {
        if (cfg.seed() >= 0) RNG.setSeed(cfg.seed());

        int w = cfg.width(), h = cfg.height();
        TileType[][] terrain = new TileType[w][h];
        WorldFeature[][] features = new WorldFeature[w][h];

        // Step 1: Generate land/ocean using cellular automata
        boolean[][] land = generateLandMass(w, h, cfg.landMass());

        // Step 2: Base terrain — ocean or plains
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                terrain[x][y] = land[x][y] ? TileType.PLAINS : TileType.OCEAN;

        // Step 3: Elevation noise → mountains
        double[][] elevation = generateNoise(w, h);
        double mountainThreshold = 1.0 - cfg.mountainDensity();
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                if (land[x][y] && elevation[x][y] > mountainThreshold)
                    terrain[x][y] = TileType.MOUNTAIN;

        // Step 4: Moisture noise → forests and swamps
        double[][] moisture = generateNoise(w, h);
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++) {
                if (!land[x][y] || terrain[x][y] == TileType.MOUNTAIN) continue;
                double m = moisture[x][y];
                if (m > 1.0 - cfg.forestDensity())
                    terrain[x][y] = TileType.FOREST;
                else if (m < cfg.swampDensity() && elevation[x][y] < 0.3)
                    terrain[x][y] = TileType.SWAMP;
            }

        // Step 5: Place towns at good locations
        List<PlacedTown> towns = placeTowns(terrain, land, w, h, cfg.townCount());
        for (PlacedTown t : towns) {
            terrain[t.x][t.y] = TileType.TOWN;
            features[t.x][t.y] = WorldFeature.town(t.id);
        }

        // Step 6: Place dungeons in remote areas
        List<PlacedDungeon> dungeons = placeDungeons(terrain, land, towns, w, h, cfg.dungeonCount());
        for (PlacedDungeon d : dungeons) {
            terrain[d.x][d.y] = TileType.DUNGEON;
            features[d.x][d.y] = WorldFeature.dungeon(d.id);
        }

        // Step 7: Connect towns with roads
        connectWithRoads(terrain, towns, w, h);

        // Step 8: Start position near first town
        WorldPosition startPos = towns.isEmpty()
                ? new WorldPosition(w / 2, h / 2)
                : new WorldPosition(towns.get(0).x + 1, towns.get(0).y);

        return new Result(terrain, features, startPos, w, h, towns, dungeons);
    }

    // ── Cellular automata landmass ───────────────────────────────────────

    private static boolean[][] generateLandMass(int w, int h, double fill) {
        boolean[][] grid = new boolean[w][h];

        // Random seed — slightly more land than fill to account for erosion
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                grid[x][y] = RNG.nextDouble() < fill + 0.05;

        // Ocean border (3 tiles)
        for (int i = 0; i < 3; i++) {
            for (int x = 0; x < w; x++) { grid[x][i] = false; grid[x][h - 1 - i] = false; }
            for (int y = 0; y < h; y++) { grid[i][y] = false; grid[w - 1 - i][y] = false; }
        }

        // Run cellular automata smoothing (4-5 rule)
        for (int pass = 0; pass < 5; pass++) {
            boolean[][] next = new boolean[w][h];
            for (int x = 1; x < w - 1; x++) {
                for (int y = 1; y < h - 1; y++) {
                    int neighbors = countNeighbors(grid, x, y, w, h);
                    next[x][y] = neighbors >= 5 || (grid[x][y] && neighbors >= 4);
                }
            }
            grid = next;
        }

        return grid;
    }

    private static int countNeighbors(boolean[][] grid, int cx, int cy, int w, int h) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                int nx = cx + dx, ny = cy + dy;
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && grid[nx][ny])
                    count++;
            }
        return count;
    }

    // ── Noise generation (simple value noise) ────────────────────────────

    private static double[][] generateNoise(int w, int h) {
        double[][] noise = new double[w][h];
        int scale = 8;
        // Generate coarse grid
        double[][] coarse = new double[(w / scale) + 2][(h / scale) + 2];
        for (double[] row : coarse) for (int i = 0; i < row.length; i++) row[i] = RNG.nextDouble();

        // Bilinear interpolation
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                double gx = (double) x / scale;
                double gy = (double) y / scale;
                int ix = (int) gx, iy = (int) gy;
                double fx = gx - ix, fy = gy - iy;

                double v00 = coarse[ix][iy];
                double v10 = coarse[ix + 1][iy];
                double v01 = coarse[ix][iy + 1];
                double v11 = coarse[ix + 1][iy + 1];

                double top    = v00 + (v10 - v00) * fx;
                double bottom = v01 + (v11 - v01) * fx;
                noise[x][y] = top + (bottom - top) * fy;
            }
        }
        return noise;
    }

    // ── Town placement ───────────────────────────────────────────────────

    private static List<PlacedTown> placeTowns(TileType[][] terrain, boolean[][] land,
                                               int w, int h, int count) {
        List<PlacedTown> towns = new ArrayList<>();
        int nameIdx = 0;

        for (int attempt = 0; attempt < count * 50 && towns.size() < count; attempt++) {
            int x = 4 + RNG.nextInt(w - 8);
            int y = 4 + RNG.nextInt(h - 8);

            if (!land[x][y]) continue;
            if (terrain[x][y] == TileType.MOUNTAIN) continue;

            // Minimum distance from other towns
            boolean tooClose = false;
            for (PlacedTown t : towns) {
                if (Math.abs(t.x - x) + Math.abs(t.y - y) < 8) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) continue;

            String name = nameIdx < TOWN_NAMES.length
                    ? TOWN_NAMES[nameIdx] : "Town " + (nameIdx + 1);
            towns.add(new PlacedTown(nameIdx, x, y, name));
            nameIdx++;
        }
        return towns;
    }

    // ── Dungeon placement ────────────────────────────────────────────────

    private static List<PlacedDungeon> placeDungeons(TileType[][] terrain, boolean[][] land,
                                                     List<PlacedTown> towns,
                                                     int w, int h, int count) {
        List<PlacedDungeon> dungeons = new ArrayList<>();
        int nameIdx = 0;

        for (int attempt = 0; attempt < count * 80 && dungeons.size() < count; attempt++) {
            int x = 3 + RNG.nextInt(w - 6);
            int y = 3 + RNG.nextInt(h - 6);

            if (!land[x][y]) continue;

            // Prefer mountains and forests for dungeons
            if (terrain[x][y] != TileType.MOUNTAIN && terrain[x][y] != TileType.FOREST
                    && RNG.nextDouble() < 0.6) continue;

            // Not too close to towns (at least 5 tiles)
            boolean nearTown = false;
            for (PlacedTown t : towns)
                if (Math.abs(t.x - x) + Math.abs(t.y - y) < 5) { nearTown = true; break; }
            if (nearTown) continue;

            // Not too close to other dungeons
            boolean nearDungeon = false;
            for (PlacedDungeon d : dungeons)
                if (Math.abs(d.x - x) + Math.abs(d.y - y) < 6) { nearDungeon = true; break; }
            if (nearDungeon) continue;

            String name = nameIdx < DUNGEON_NAMES.length
                    ? DUNGEON_NAMES[nameIdx] : "Dungeon " + (nameIdx + 1);
            dungeons.add(new PlacedDungeon(nameIdx + 1, x, y, name));
            nameIdx++;
        }
        return dungeons;
    }

    // ── Road connection ──────────────────────────────────────────────────

    private static void connectWithRoads(TileType[][] terrain,
                                         List<PlacedTown> towns, int w, int h) {
        // Connect each town to its nearest neighbor with a road
        for (int i = 0; i < towns.size(); i++) {
            PlacedTown from = towns.get(i);
            PlacedTown closest = null;
            int bestDist = Integer.MAX_VALUE;

            for (int j = 0; j < towns.size(); j++) {
                if (i == j) continue;
                PlacedTown to = towns.get(j);
                int dist = Math.abs(from.x - to.x) + Math.abs(from.y - to.y);
                if (dist < bestDist) { bestDist = dist; closest = to; }
            }

            if (closest != null) {
                carveRoad(terrain, from.x, from.y, closest.x, closest.y, w, h);
            }
        }
    }

    /**
     * Carves a road between two points using an L-shaped path.
     * Only overwrites plains, forests, and swamps — not mountains or features.
     */
    private static void carveRoad(TileType[][] terrain, int x1, int y1,
                                  int x2, int y2, int w, int h) {
        int cx = x1, cy = y1;

        // Horizontal first, then vertical
        while (cx != x2) {
            cx += (x2 > cx) ? 1 : -1;
            if (cx >= 0 && cx < w && cy >= 0 && cy < h) {
                TileType t = terrain[cx][cy];
                if (t == TileType.PLAINS || t == TileType.FOREST || t == TileType.SWAMP)
                    terrain[cx][cy] = TileType.ROAD;
            }
        }
        while (cy != y2) {
            cy += (y2 > cy) ? 1 : -1;
            if (cx >= 0 && cx < w && cy >= 0 && cy < h) {
                TileType t = terrain[cx][cy];
                if (t == TileType.PLAINS || t == TileType.FOREST || t == TileType.SWAMP)
                    terrain[cx][cy] = TileType.ROAD;
            }
        }
    }
}