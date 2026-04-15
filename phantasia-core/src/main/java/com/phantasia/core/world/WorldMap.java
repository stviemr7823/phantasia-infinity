// phantasia-core/src/main/java/com/phantasia/core/world/WorldMap.java
package com.phantasia.core.world;

import java.io.*;
import java.nio.file.*;

/**
 * The world grid.
 *
 * Two construction paths:
 *   WorldMap.loadFromFile(path)          — load from binary .map file (baked by WorldMapBaker)
 *   WorldMap.fromGrid(grid, w, h, start) — build directly from a Tile[][] (no disk I/O)
 *
 * File format (for loadFromFile):
 *   Bytes 0–1 : width  (16-bit big-endian)
 *   Bytes 2–3 : height (16-bit big-endian)
 *   Bytes 4–5 : start X (16-bit big-endian)
 *   Bytes 6–7 : start Y (16-bit big-endian)
 *   Bytes 8+  : tile records, row-major order (x fast, y slow)
 *
 * Each tile record = 2 bytes:
 *   Byte 0 : TileType ordinal
 *   Byte 1 : feature packed byte
 *             bits 7–6 : FeatureType ordinal (0=NONE, 1=TOWN, 2=DUNGEON)
 *             bits 5–0 : feature ID (0–63)
 */
public class WorldMap {

    private final Tile[][]      grid;
    private final int           width;
    private final int           height;
    private final WorldPosition startPosition;

    private WorldMap(Tile[][] grid, int width, int height, WorldPosition start) {
        this.grid          = grid;
        this.width         = width;
        this.height        = height;
        this.startPosition = start;
    }

    // -------------------------------------------------------------------------
    // Factories
    // -------------------------------------------------------------------------

    /**
     * Constructs a WorldMap directly from an already-built Tile[][] grid.
     * No disk I/O — used by PendragonWorldMap.buildWorldMap() so the game
     * works even when world.dat has not been baked yet.
     */
    public static WorldMap fromGrid(Tile[][] grid, int width, int height,
                                    WorldPosition start) {
        return new WorldMap(grid, width, height, start);
    }

    /**
     * Loads a WorldMap from a binary .map file produced by WorldMapBaker.
     */
    public static WorldMap loadFromFile(String path) throws IOException {
        byte[] data = Files.readAllBytes(Path.of(path));

        int width  = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        int height = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        int startX = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
        int startY = ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);

        Tile[][] grid  = new Tile[width][height];
        int      offset = 8;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int typeByte = data[offset]     & 0xFF;
                int featByte = data[offset + 1] & 0xFF;
                offset += 2;

                TileType     type = TileType.values()[typeByte];
                WorldFeature feat = parseFeature(featByte);

                grid[x][y] = new Tile.Builder(type).feature(feat).build();
            }
        }

        return new WorldMap(grid, width, height, new WorldPosition(startX, startY));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Tile getTile(WorldPosition pos) {
        return getTile(pos.x(), pos.y());
    }

    public Tile getTile(int x, int y) {
        if (!inBounds(x, y)) return Tile.of(TileType.OCEAN);
        return grid[x][y];
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public boolean isPassable(WorldPosition pos) {
        return getTile(pos).isPassable();
    }

    public WorldPosition getStartPosition() { return startPosition; }
    public int           getWidth()         { return width;         }
    public int           getHeight()        { return height;        }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static WorldFeature parseFeature(int featByte) {
        if (featByte == 0) return WorldFeature.NONE;

        int typeId = (featByte >> 6) & 0x03;
        int dataId =  featByte       & 0x3F;

        FeatureType type = FeatureType.fromId(typeId);
        return new WorldFeature(type, dataId);
    }
}