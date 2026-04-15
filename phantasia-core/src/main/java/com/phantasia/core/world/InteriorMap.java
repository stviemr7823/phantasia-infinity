// phantasia-core/src/main/java/com/phantasia/core/world/InteriorMap.java
package com.phantasia.core.world;

import com.phantasia.core.data.QuestFlag;
import com.phantasia.core.model.PlacedNpc;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * An authored interior grid — the unified data structure for towns and dungeons.
 *
 * InteriorMap is a tile grid with placed interactive features and NPCs.
 * Towns and dungeons share this structure; the behavioral differences come
 * from {@link InteriorSettings} (fog of war, encounters, lighting).
 *
 * COORDINATE SYSTEM:
 *   Y-down, matching screen space. (0,0) is the top-left corner.
 *   This is consistent with DungeonPanel's existing convention and avoids
 *   the Y-flip math used on the overworld.
 *
 * TILE DATA:
 *   Each cell stores an integer index into the map's {@link InteriorTileSet}.
 *   The tile set defines what that index means (name, passability, asset,
 *   interaction type). This keeps the grid compact — one int per cell.
 *
 * FOG OF WAR:
 *   Dungeon maps track exploration state in a compact BitSet.
 *   Town maps ignore this (all tiles are always visible).
 *
 * LIFECYCLE:
 *   - Authored in the editor's interior map editor
 *   - Baked to interiors.dat
 *   - Loaded at runtime by InteriorMapLoader
 *   - Rendered by j2d's InteriorPanel (or JME/libGDX equivalents)
 */
public class InteriorMap {

    private final int                  id;
    private final String               name;
    private final InteriorMapType      mapType;
    private final int                  width;
    private final int                  height;
    private final int[][]              tiles;          // indices into tileSet
    private final String               tileSetId;      // which tile set to use
    private final InteriorSettings     settings;
    private final List<PlacedFeature>  features;
    private final List<PlacedNpc>      placedNpcs;

    // Runtime state (not serialized in the map definition)
    private final BitSet               explored;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public InteriorMap(int id, String name, InteriorMapType mapType,
                       int width, int height, String tileSetId) {
        this.id         = id;
        this.name       = name;
        this.mapType    = mapType;
        this.width      = width;
        this.height     = height;
        this.tiles      = new int[width][height];
        this.tileSetId  = tileSetId;
        this.settings   = (mapType == InteriorMapType.TOWN)
                ? InteriorSettings.forTown()
                : InteriorSettings.forDungeon();
        this.features   = new ArrayList<>();
        this.placedNpcs = new ArrayList<>();
        this.explored   = new BitSet(width * height);
    }

    // -------------------------------------------------------------------------
    // Tile access
    // -------------------------------------------------------------------------

    /** Sets the tile index at (x, y). */
    public void setTile(int x, int y, int tileIndex) {
        if (inBounds(x, y)) tiles[x][y] = tileIndex;
    }

    /** Returns the tile index at (x, y), or -1 if out of bounds. */
    public int getTile(int x, int y) {
        if (!inBounds(x, y)) return -1;
        return tiles[x][y];
    }

    /** Returns the raw tile grid (for serialization). */
    public int[][] getTileGrid() { return tiles; }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    // -------------------------------------------------------------------------
    // Feature management
    // -------------------------------------------------------------------------

    public void addFeature(PlacedFeature feature) {
        features.add(feature);
    }

    public void removeFeature(PlacedFeature feature) {
        features.remove(feature);
    }

    public List<PlacedFeature> getFeatures() {
        return Collections.unmodifiableList(features);
    }

    /** Returns the feature at (x, y), or null if none. */
    public PlacedFeature getFeatureAt(int x, int y) {
        for (PlacedFeature f : features) {
            if (f.x() == x && f.y() == y) return f;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // NPC management
    // -------------------------------------------------------------------------

    public void addNpc(PlacedNpc npc) {
        placedNpcs.add(npc);
    }

    public void removeNpc(PlacedNpc npc) {
        placedNpcs.remove(npc);
    }

    public List<PlacedNpc> getPlacedNpcs() {
        return Collections.unmodifiableList(placedNpcs);
    }

    /** Returns the NPC at (x, y), or null if none. */
    public PlacedNpc getNpcAt(int x, int y) {
        for (PlacedNpc n : placedNpcs) {
            if (n.x() == x && n.y() == y) return n;
        }
        return null;
    }

    /**
     * Returns visible NPCs given the current quest flag state.
     * @param flagChecker typically session::hasFlag
     */
    public List<PlacedNpc> getVisibleNpcs(
            java.util.function.Predicate<QuestFlag> flagChecker) {
        return placedNpcs.stream()
                .filter(n -> n.isVisible(flagChecker))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Fog of war (dungeon maps only)
    // -------------------------------------------------------------------------

    public boolean isExplored(int x, int y) {
        if (!inBounds(x, y)) return false;
        return explored.get(y * width + x);
    }

    public void setExplored(int x, int y, boolean val) {
        if (!inBounds(x, y)) return;
        explored.set(y * width + x, val);
    }

    public void updateExploration(int px, int py, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dx * dx + dy * dy <= radius * radius) {
                    setExplored(px + dx, py + dy, true);
                }
            }
        }
    }

    public BitSet getExploredBits() { return (BitSet) explored.clone(); }

    public void setExploredBits(BitSet bits) {
        explored.clear();
        explored.or(bits);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** Finds the first tile matching the given tile set index. */
    public WorldPosition findTile(int tileIndex) {
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                if (tiles[x][y] == tileIndex)
                    return new WorldPosition(x, y);
        return null;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int              getId()         { return id; }
    public String           getName()       { return name; }
    public InteriorMapType  getMapType()    { return mapType; }
    public int              getWidth()      { return width; }
    public int              getHeight()     { return height; }
    public String           getTileSetId()  { return tileSetId; }
    public InteriorSettings getSettings()   { return settings; }
}
