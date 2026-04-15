// phantasia-core/src/main/java/com/phantasia/core/world/DungeonFloor.java
package com.phantasia.core.world;

import java.util.BitSet;

/**
 * A single dungeon floor: a 2D grid of tiles with fog-of-war tracking.
 *
 * COORDINATE SYSTEM:
 *   Dungeons use Y-down (same as screen space). Y=0 is the top row.
 *   This is simpler than the overworld's Y-up convention and avoids
 *   the flip math that complicates MapPanel/PartyActor.
 *
 * FOG OF WAR:
 *   Each tile has a single "explored" bit stored in a compact BitSet.
 *   Tiles are explored when the party moves within torch radius.
 *   The BitSet can be serialized for save/load via getExploredBits().
 *
 * TILE TYPES:
 *   VOID       — out of bounds / solid rock (impassable, invisible)
 *   WALL       — carved wall (impassable, visible when explored)
 *   FLOOR      — open ground (passable)
 *   DOOR       — passage (passable, may be locked via QuestFlag)
 *   STAIRS_UP  — ascend / exit dungeon
 *   STAIRS_DOWN— descend to deeper floor
 *   CHEST      — lootable container (tracked by QuestFlag)
 *   TRAP       — concealed hazard
 */
public class DungeonFloor {

    public enum TileType {
        VOID, FLOOR, WALL, DOOR, STAIRS_UP, STAIRS_DOWN, CHEST, TRAP;

        /** Returns true if the party can walk onto this tile. */
        public boolean isPassable() {
            return this != VOID && this != WALL;
        }
    }

    private final int           width;
    private final int           height;
    private final TileType[][]  tiles;
    private final BitSet        explored;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public DungeonFloor(int width, int height) {
        this.width    = width;
        this.height   = height;
        this.tiles    = new TileType[width][height];
        this.explored = new BitSet(width * height);

        // Default fill: WALL everywhere.
        // DungeonFloorGenerator carves FLOOR into this.
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                tiles[x][y] = TileType.WALL;
    }

    // -------------------------------------------------------------------------
    // Dimensions
    // -------------------------------------------------------------------------

    public int getWidth()  { return width;  }
    public int getHeight() { return height; }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    // -------------------------------------------------------------------------
    // Tile access
    // -------------------------------------------------------------------------

    public void setTile(int x, int y, TileType type) {
        if (inBounds(x, y)) {
            tiles[x][y] = type;
        }
    }

    public TileType getTile(int x, int y) {
        if (!inBounds(x, y)) return TileType.VOID;
        TileType t = tiles[x][y];
        return t != null ? t : TileType.VOID;
    }

    // -------------------------------------------------------------------------
    // Fog of war
    // -------------------------------------------------------------------------

    public boolean isExplored(int x, int y) {
        if (!inBounds(x, y)) return false;
        return explored.get(y * width + x);
    }

    public void setExplored(int x, int y, boolean val) {
        if (!inBounds(x, y)) return;
        explored.set(y * width + x, val);
    }

    /**
     * Marks all tiles within a circular radius of (playerX, playerY)
     * as explored.  Called on every successful dungeon move.
     *
     * @param radius  torch radius in tiles (design doc: 3 for standard,
     *                5-7 for bright torch items)
     */
    public void updateExploration(int playerX, int playerY, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dx * dx + dy * dy <= radius * radius) {
                    setExplored(playerX + dx, playerY + dy, true);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /**
     * Finds the position of the first STAIRS_UP tile (the dungeon entry point).
     * Returns null if none exists (shouldn't happen with a well-formed floor).
     */
    public WorldPosition findStairsUp() {
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                if (tiles[x][y] == TileType.STAIRS_UP)
                    return new WorldPosition(x, y);
        return null;
    }

    /**
     * Finds the position of the first STAIRS_DOWN tile.
     */
    public WorldPosition findStairsDown() {
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                if (tiles[x][y] == TileType.STAIRS_DOWN)
                    return new WorldPosition(x, y);
        return null;
    }

    // -------------------------------------------------------------------------
    // Serialization support (for SaveManager)
    // -------------------------------------------------------------------------

    /** Returns the raw explored bits for save serialization. */
    public BitSet getExploredBits() {
        return (BitSet) explored.clone();
    }

    /** Restores explored bits from a previously saved BitSet. */
    public void setExploredBits(BitSet saved) {
        explored.clear();
        explored.or(saved);
    }
}