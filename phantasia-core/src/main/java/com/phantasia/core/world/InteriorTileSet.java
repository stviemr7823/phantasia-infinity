// phantasia-core/src/main/java/com/phantasia/core/world/InteriorTileSet.java
package com.phantasia.core.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the tile vocabulary for an interior map type.
 *
 * Each interior map references a tile set by ID. The tile set determines
 * which tile types are available for painting in the editor and how each
 * tile behaves (passable, interaction type).
 *
 * Towns and dungeons use separate tile sets to establish distinct atmospheres.
 * Multiple tile set variants can exist per category — "dungeon_stone",
 * "dungeon_ice", "town_castle", "town_village" — each with different
 * visual assets but the same behavioral vocabulary.
 *
 * RENDERER MAPPING:
 *   Each TileSetEntry carries an assetId string. The renderer maps this
 *   to visual assets: j2d loads sprite PNGs, JME loads 3D models/materials.
 *   Fallback to colored rectangles when assets are absent (same pattern
 *   as KenneyTileLoader on the overworld).
 */
public class InteriorTileSet {

    private final String              id;           // "town_standard", "dungeon_stone"
    private final List<TileSetEntry>  entries;
    private final int                 defaultFloor; // index of the default floor tile

    public InteriorTileSet(String id, List<TileSetEntry> entries, int defaultFloor) {
        this.id           = id;
        this.entries      = List.copyOf(entries);
        this.defaultFloor = defaultFloor;
    }

    public String             getId()           { return id; }
    public List<TileSetEntry> getEntries()      { return entries; }
    public int                getDefaultFloor() { return defaultFloor; }

    public TileSetEntry getEntry(int index) {
        if (index < 0 || index >= entries.size()) return null;
        return entries.get(index);
    }

    public int size() { return entries.size(); }

    // =====================================================================
    // Built-in tile sets
    // =====================================================================

    /** Standard town tile set — warm interiors, shops, homes. */
    public static InteriorTileSet standardTown() {
        List<TileSetEntry> tiles = new ArrayList<>();
        int i = 0;
        tiles.add(new TileSetEntry(i++, "wood_floor",    true,  "town_wood_floor",    InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "stone_floor",   true,  "town_stone_floor",   InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "wall",          false, "town_wall",          InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "counter",       false, "town_counter",       InteractionType.BUMP_TRIGGER));
        tiles.add(new TileSetEntry(i++, "door",          true,  "town_door",          InteractionType.STEP_TRIGGER));
        tiles.add(new TileSetEntry(i++, "window",        false, "town_window",        InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "stairs",        true,  "town_stairs",        InteractionType.STEP_TRIGGER));
        tiles.add(new TileSetEntry(i++, "hearth",        false, "town_hearth",        InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "table",         false, "town_table",         InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "shelf",         false, "town_shelf",         InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "bed",           false, "town_bed",           InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "rug",           true,  "town_rug",           InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "market_stall",  false, "town_market_stall",  InteractionType.BUMP_TRIGGER));
        tiles.add(new TileSetEntry(i++, "fountain",      false, "town_fountain",      InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "cobblestone",   true,  "town_cobblestone",   InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "garden",        true,  "town_garden",        InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "exit",          true,  "town_exit",          InteractionType.STEP_TRIGGER));
        return new InteriorTileSet("town_standard", tiles, 0); // default = wood_floor
    }

    /** Standard dungeon tile set — stone corridors, dangers. */
    public static InteriorTileSet standardDungeon() {
        List<TileSetEntry> tiles = new ArrayList<>();
        int i = 0;
        tiles.add(new TileSetEntry(i++, "stone_floor",  true,  "dng_stone_floor",  InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "wall",         false, "dng_wall",         InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "door",         true,  "dng_door",         InteractionType.STEP_TRIGGER));
        tiles.add(new TileSetEntry(i++, "locked_door",  false, "dng_locked_door",  InteractionType.BUMP_TRIGGER));
        tiles.add(new TileSetEntry(i++, "stairs_up",    true,  "dng_stairs_up",    InteractionType.STEP_TRIGGER));
        tiles.add(new TileSetEntry(i++, "stairs_down",  true,  "dng_stairs_down",  InteractionType.STEP_TRIGGER));
        tiles.add(new TileSetEntry(i++, "chest",        true,  "dng_chest",        InteractionType.STEP_TRIGGER));
        tiles.add(new TileSetEntry(i++, "trap_hidden",  true,  "dng_stone_floor",  InteractionType.STEP_TRIGGER)); // looks like floor
        tiles.add(new TileSetEntry(i++, "trap_visible", true,  "dng_trap",         InteractionType.STEP_TRIGGER));
        tiles.add(new TileSetEntry(i++, "rubble",       false, "dng_rubble",       InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "water",        false, "dng_water",        InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "altar",        false, "dng_altar",        InteractionType.BUMP_TRIGGER));
        tiles.add(new TileSetEntry(i++, "cage",         false, "dng_cage",         InteractionType.BUMP_TRIGGER));
        tiles.add(new TileSetEntry(i++, "throne",       false, "dng_throne",       InteractionType.BUMP_TRIGGER));
        tiles.add(new TileSetEntry(i++, "pillar",       false, "dng_pillar",       InteractionType.NONE));
        tiles.add(new TileSetEntry(i++, "secret_wall",  false, "dng_wall",         InteractionType.BUMP_TRIGGER)); // looks like wall
        return new InteriorTileSet("dungeon_standard", tiles, 0); // default = stone_floor
    }
}