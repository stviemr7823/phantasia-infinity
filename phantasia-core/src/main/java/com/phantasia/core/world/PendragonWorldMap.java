// phantasia-core/src/main/java/com/phantasia/core/world/PendragonWorldMap.java
package com.phantasia.core.world;

import com.phantasia.core.data.DataPaths;
import com.phantasia.core.world.FeatureRecord;
import com.phantasia.core.world.FeatureRegistry;

import java.io.IOException;

/**
 * Hand-authored world map for Phantasia: Infinity.
 *
 * Two modes of use:
 *
 *   1. Build an in-memory WorldMap with no disk I/O (preferred at runtime):
 *          WorldMap map = PendragonWorldMap.buildWorldMap();
 *      LoadingScreen calls this so the game works even before world.dat exists.
 *
 *   2. Bake world.dat to disk (run once, or whenever the map is edited):
 *          ./gradlew :phantasia-core:bakeMap
 *      Or: right-click PendragonWorldMap → Run 'main()'
 *      Output: phantasia-core/data/world.dat
 *
 * MAP LEGEND (32×32, Y=0 at bottom in WorldPosition coords):
 *   O = OCEAN      . = PLAINS    # = FOREST
 *   ^ = MOUNTAIN   ~ = SWAMP     = = ROAD
 *   T = TOWN tile  D = DUNGEON tile
 *
 *   Towns    (FeatureType.TOWN):
 *     0 = Pendragon   (x=6,  y=10)  start city, center-west
 *     1 = Scandor     (x=12, y=4)   south-east coast
 *     2 = Loftwood    (x=9,  y=25)  north forest town
 *     3 = Ironhaven   (x=12, y=16)  mountain pass town
 *
 *   Dungeons (FeatureType.DUNGEON):
 *     1 = Frostpeak Cavern (x=9,  y=27)  northern mountains
 *     2 = Sunken Vault     (x=5,  y=6)   swamp dungeon
 */
public class PendragonWorldMap {

    static final int W       = 32;
    static final int H       = 32;
    static final int START_X = 6;   // Pendragon town
    static final int START_Y = 10;

    // -------------------------------------------------------------------------
    // In-memory factory — no disk I/O required
    // -------------------------------------------------------------------------

    /**
     * Builds and returns the Pendragon world map entirely in memory.
     * Call this at runtime instead of WorldMap.loadFromFile() so the game
     * runs even before world.dat has been baked.
     */
    public static WorldMap buildWorldMap() {
        TileType[][]     terrain  = buildTerrain();
        WorldFeature[][] features = buildFeatures();

        Tile[][] grid = new Tile[W][H];
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                WorldFeature feat = (features[x][y] != null)
                        ? features[x][y]
                        : WorldFeature.NONE;
                grid[x][y] = new Tile.Builder(terrain[x][y]).feature(feat).build();
            }
        }

        return WorldMap.fromGrid(grid, W, H, new WorldPosition(START_X, START_Y));
    }

    // -------------------------------------------------------------------------
    // Bake entry point — writes world.dat to disk
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        TileType[][]     terrain  = buildTerrain();
        WorldFeature[][] features = buildFeatures();

        String outPath = DataPaths.DAT_DIR + "/world.dat";
        DataPaths.ensureParentDirs(outPath);
        WorldMapBaker.bake(outPath, W, H, START_X, START_Y, terrain, features);

        // Write features.dat
        String featuresPath = DataPaths.DAT_DIR + "/features.dat";
        buildFeatureRegistry().save(featuresPath);

        System.out.println("Map baked: " + W + "×" + H
                + "  start=(" + START_X + ", " + START_Y + ")");
        System.out.println("Output: " + DataPaths.absolute(outPath));
        System.out.println("Features: " + DataPaths.absolute(featuresPath));

        // Sanity-check
        WorldMap map = WorldMap.loadFromFile(outPath);
        Tile startTile = map.getTile(START_X, START_Y);
        System.out.println("Start tile type: " + startTile.getType()
                + "  feature: " + startTile.getFeature());
        if (startTile.getType() != TileType.TOWN) {
            System.err.println("WARNING: start tile is not TOWN!");
        }
    }

    // -------------------------------------------------------------------------
    // Terrain grid
    // -------------------------------------------------------------------------

    static TileType[][] buildTerrain() {
        // 32 chars wide × 32 rows tall
        // Read top-to-bottom = north-to-south visually; Y-flipped in fromVisual()
        String[] visual = {
                //0         1         2         3
                //01234567890123456789012345678901
                "OOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO", // y=31 (far north)
                "OOOOOO###########OOOOOOOOOOOOOOO", // y=30
                "OOOOO##########^^OOOOOOOOOOOOOOO", // y=29
                "OOOO###...####^^^OOOOOOOOOOOOOOO", // y=28
                "OOO##....D.###^^^OOOOOOOOOOOOOOO", // y=27 — Frostpeak Cavern (dungeon 1)
                "OO##.....##.##^^OOOOOOOOOOOOOOOO", // y=26
                "OO#......T.=.##^OOOOOOOOOOOOOOOO", // y=25 — Loftwood (town 2) at (9,25)
                "O##.....==....#^OOOOOOOOOOOOOOOO", // y=24
                "O#......=.....##OOOOOOOOOOOOOOOO", // y=23
                "O#.....=.......#OOOOOOOOOOOOOOOO", // y=22
                "O##...=........^OOOOOOOOOOOOOOOO", // y=21
                "OO#..=....^....^OOOOOOOOOOOOOOOO", // y=20
                "OO##=....^^...^^OOOOOOOOOOOOOOOO", // y=19
                "OOO=....^^^..^^^OOOOOOOOOOOOOOOO", // y=18
                "OO==...^^^^..^^#OOOOOOOOOOOOOOOO", // y=17
                "O===...^^^..T##^OOOOOOOOOOOOOOOO", // y=16 — Ironhaven (town 3) at (12,16)
                "O===..####.####OOOOOOOOOOOOOOOOO", // y=15
                "OO==.###..###..OOOOOOOOOOOOOOOOO", // y=14
                "OO==.##....#...OOOOOOOOOOOOOOOOO", // y=13
                "OO==.##....#...OOOOOOOOOOOOOOOOO", // y=12
                "OO=..##....#...OOOOOOOOOOOOOOOOO", // y=11
                "OO=...T====....OOOOOOOOOOOOOOOOO", // y=10 — Pendragon (town 0) at (6,10)
                "OO=......=.....OOOOOOOOOOOOOOOOO", // y=9
                "OO~......=.....OOOOOOOOOOOOOOOOO", // y=8
                "OO~~.....=.....OOOOOOOOOOOOOOOOO", // y=7
                "OO~~~D...=.....OOOOOOOOOOOOOOOOO", // y=6 — Sunken Vault (dungeon 2) at (5,6)
                "OO~~~~...=.....OOOOOOOOOOOOOOOOO", // y=5
                "OOO~~~~..=..T..OOOOOOOOOOOOOOOOO", // y=4 — Scandor (town 1) at (12,4)
                "OOOO~~~..=.....OOOOOOOOOOOOOOOOO", // y=3
                "OOOOO~~..=.....OOOOOOOOOOOOOOOOO", // y=2
                "OOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO", // y=1
                "OOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO", // y=0 (far south)
        };

        TileType[][] grid = new TileType[W][H];
        for (int row = 0; row < H; row++) {
            int    y    = (H - 1) - row;   // flip: visual row 0 → y=31
            String line = visual[row];
            for (int x = 0; x < W; x++) {
                char c = (x < line.length()) ? line.charAt(x) : 'O';
                grid[x][y] = charToTile(c);
            }
        }
        return grid;
    }

    private static TileType charToTile(char c) {
        return switch (c) {
            case '.' -> TileType.PLAINS;
            case '#' -> TileType.FOREST;
            case '^' -> TileType.MOUNTAIN;
            case '~' -> TileType.SWAMP;
            case '=' -> TileType.ROAD;
            case 'T' -> TileType.TOWN;
            case 'D' -> TileType.DUNGEON;
            default  -> TileType.OCEAN;
        };
    }

    // -------------------------------------------------------------------------
    // Feature overlay
    // -------------------------------------------------------------------------

    // Replace buildFeatures() in PendragonWorldMap:
    static WorldFeature[][] buildFeatures() {
        WorldFeature[][] f = new WorldFeature[W][H];

        // Towns — tile references only (id, no name)
        place(f,  6, 10, WorldFeature.town(0));
        place(f,  9, 25, WorldFeature.town(2));
        place(f, 12, 16, WorldFeature.town(3));
        place(f, 12,  4, WorldFeature.town(1));

        // Dungeons
        place(f,  9, 27, WorldFeature.dungeon(1));
        place(f,  5,  6, WorldFeature.dungeon(2));

        // NPCs
        place(f,  7, 10, WorldFeature.npc(101));

        return f;
    }

    // Add new method — call this from main() to write features.dat:
    static FeatureRegistry buildFeatureRegistry() {
        FeatureRegistry registry = new FeatureRegistry();

        registry.add(FeatureRecord.town(0,  6, 10,
                "Pendragon",
                "The ancient capital, seat of the High Council."));
        registry.add(FeatureRecord.town(1, 12,  4,
                "Scandor",
                "A northern trading post, cold and pragmatic."));
        registry.add(FeatureRecord.town(2,  9, 25,
                "Loftwood",
                "A peaceful forest settlement, known for its healers."));
        registry.add(FeatureRecord.town(3, 12, 16,
                "Ironhaven",
                "A fortified city built on iron mines."));

        registry.add(FeatureRecord.dungeon(1,  9, 27,
                "Frostpeak Cavern",
                "A frozen labyrinth carved by ancient hands."));
        registry.add(FeatureRecord.dungeon(2,  5,  6,
                "Sunken Vault",
                "A submerged treasury, half-drowned and treacherous."));

        registry.add(FeatureRecord.npc(101, 7, 10, "Filmon"));

        return registry;
    }

    private static void place(WorldFeature[][] f, int x, int y, WorldFeature feature) {
        if (x >= 0 && x < W && y >= 0 && y < H) {
            f[x][y] = feature;
        }
    }
}