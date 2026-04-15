// phantasia-editor/src/main/java/com/phantasia/editor/generators/TownLayoutGenerator.java
package com.phantasia.editor.generators;

import com.phantasia.core.world.*;

import java.util.*;

/**
 * Procedural town interior map generator.
 *
 * Produces a starting layout for a town interior: cobblestone streets,
 * wooden building interiors, a central market square, and scattered
 * decorative elements. The designer refines the result by hand.
 *
 * The algorithm:
 * <ol>
 *   <li>Fill with ground (index 0 = stone floor)</li>
 *   <li>Place a central market square</li>
 *   <li>Carve streets from the edges toward the center</li>
 *   <li>Place rectangular buildings along streets</li>
 *   <li>Add doors to each building</li>
 *   <li>Scatter decorative tiles (hearths, crates, plants)</li>
 *   <li>Place an exit tile at the south edge</li>
 * </ol>
 */
public class TownLayoutGenerator {

    private static final Random RNG = new Random();

    // Tile indices into the town tile set (InteriorTileSet "town_standard")
    public static final int STONE_FLOOR  = 0;
    public static final int WOOD_FLOOR   = 1;
    public static final int WALL         = 2;
    public static final int DOOR         = 3;
    public static final int COUNTER      = 4;
    public static final int HEARTH       = 5;
    public static final int MARKET_STALL = 6;
    public static final int CRATE        = 7;
    public static final int PLANT        = 8;
    public static final int WELL         = 9;
    public static final int EXIT         = 10;
    public static final int ROAD         = 11;
    public static final int CARPET       = 12;
    public static final int BOOKSHELF    = 13;
    public static final int BED          = 14;
    public static final int TABLE        = 15;

    /**
     * Generates a town interior map.
     *
     * @param id       the interior map ID
     * @param name     the town name
     * @param width    map width (16–40 recommended)
     * @param height   map height (16–40 recommended)
     * @param density  building density: 0.0 (sparse) to 1.0 (packed)
     * @param seed     random seed (-1 for random)
     * @return a populated InteriorMap ready for hand-refinement
     */
    public static InteriorMap generate(int id, String name,
                                       int width, int height,
                                       double density, long seed) {
        if (seed >= 0) RNG.setSeed(seed);

        InteriorMap map = new InteriorMap(id, name, InteriorMapType.TOWN,
                width, height, "town_standard");

        // Step 1: Fill with stone floor
        fillAll(map, width, height, STONE_FLOOR);

        // Step 2: Border walls
        for (int x = 0; x < width; x++) {
            map.setTile(x, 0, WALL);
            map.setTile(x, height - 1, WALL);
        }
        for (int y = 0; y < height; y++) {
            map.setTile(0, y, WALL);
            map.setTile(width - 1, y, WALL);
        }

        // Step 3: Central market square
        int sqSize = Math.max(4, Math.min(8, width / 4));
        int sqX = width / 2 - sqSize / 2;
        int sqY = height / 2 - sqSize / 2;
        for (int x = sqX; x < sqX + sqSize; x++)
            for (int y = sqY; y < sqY + sqSize; y++)
                map.setTile(x, y, ROAD);

        // Well in the center
        map.setTile(width / 2, height / 2, WELL);

        // Market stalls around the square
        for (int i = 0; i < sqSize - 2; i += 2) {
            if (RNG.nextBoolean()) map.setTile(sqX + 1 + i, sqY, MARKET_STALL);
            if (RNG.nextBoolean()) map.setTile(sqX + 1 + i, sqY + sqSize - 1, MARKET_STALL);
        }

        // Step 4: Main streets (cross pattern)
        // Horizontal street
        for (int x = 1; x < width - 1; x++)
            map.setTile(x, height / 2, ROAD);
        // Vertical street
        for (int y = 1; y < height - 1; y++)
            map.setTile(width / 2, y, ROAD);

        // Step 5: Place buildings
        int maxBuildings = (int) (density * (width * height) / 60);
        List<Building> buildings = new ArrayList<>();
        for (int attempt = 0; attempt < maxBuildings * 5; attempt++) {
            if (buildings.size() >= maxBuildings) break;

            int bw = 4 + RNG.nextInt(4);  // 4–7 wide
            int bh = 4 + RNG.nextInt(3);  // 4–6 tall
            int bx = 2 + RNG.nextInt(Math.max(1, width - bw - 4));
            int by = 2 + RNG.nextInt(Math.max(1, height - bh - 4));

            Building b = new Building(bx, by, bw, bh);

            // Check: doesn't overlap streets, market, or other buildings
            if (overlapsStreets(b, width, height, sqX, sqY, sqSize)) continue;
            if (buildings.stream().anyMatch(b::intersects)) continue;

            carveBuilding(map, b);
            buildings.add(b);
        }

        // Step 6: Scatter decorations in open areas
        scatterDecorations(map, width, height);

        // Step 7: Exit at south edge
        map.setTile(width / 2, height - 1, EXIT);

        return map;
    }

    // ── Building placement ───────────────────────────────────────────────

    private static void carveBuilding(InteriorMap map, Building b) {
        // Walls
        for (int x = b.x; x < b.x + b.w; x++) {
            map.setTile(x, b.y, WALL);
            map.setTile(x, b.y + b.h - 1, WALL);
        }
        for (int y = b.y; y < b.y + b.h; y++) {
            map.setTile(b.x, y, WALL);
            map.setTile(b.x + b.w - 1, y, WALL);
        }

        // Interior floor
        for (int x = b.x + 1; x < b.x + b.w - 1; x++)
            for (int y = b.y + 1; y < b.y + b.h - 1; y++)
                map.setTile(x, y, WOOD_FLOOR);

        // Door on a random wall side
        int side = RNG.nextInt(4);
        switch (side) {
            case 0 -> map.setTile(b.x + b.w / 2, b.y, DOOR);              // north
            case 1 -> map.setTile(b.x + b.w / 2, b.y + b.h - 1, DOOR);   // south
            case 2 -> map.setTile(b.x, b.y + b.h / 2, DOOR);              // west
            case 3 -> map.setTile(b.x + b.w - 1, b.y + b.h / 2, DOOR);   // east
        }

        // Random interior furniture
        int interiorW = b.w - 2;
        int interiorH = b.h - 2;
        if (interiorW > 1 && interiorH > 1) {
            int ix = b.x + 1 + RNG.nextInt(interiorW);
            int iy = b.y + 1 + RNG.nextInt(interiorH);
            int[] furniture = { COUNTER, HEARTH, TABLE, BOOKSHELF, BED, CARPET };
            map.setTile(ix, iy, furniture[RNG.nextInt(furniture.length)]);
        }
    }

    private static boolean overlapsStreets(Building b, int mapW, int mapH,
                                           int sqX, int sqY, int sqSize) {
        int midY = mapH / 2;
        int midX = mapW / 2;
        // Check horizontal street
        if (b.y <= midY && b.y + b.h > midY) return true;
        // Check vertical street
        if (b.x <= midX && b.x + b.w > midX) return true;
        // Check market square (with 1-tile margin)
        return b.x < sqX + sqSize + 1 && b.x + b.w > sqX - 1
                && b.y < sqY + sqSize + 1 && b.y + b.h > sqY - 1;
    }

    // ── Decoration ───────────────────────────────────────────────────────

    private static void scatterDecorations(InteriorMap map, int w, int h) {
        int count = (w * h) / 40;
        for (int i = 0; i < count; i++) {
            int x = 2 + RNG.nextInt(w - 4);
            int y = 2 + RNG.nextInt(h - 4);
            if (map.getTile(x, y) == STONE_FLOOR) {
                int[] decor = { PLANT, CRATE, PLANT, PLANT };
                map.setTile(x, y, decor[RNG.nextInt(decor.length)]);
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void fillAll(InteriorMap map, int w, int h, int tile) {
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                map.setTile(x, y, tile);
    }

    private record Building(int x, int y, int w, int h) {
        boolean intersects(Building o) {
            return x < o.x + o.w + 1 && x + w + 1 > o.x
                    && y < o.y + o.h + 1 && y + h + 1 > o.y;
        }
    }
}