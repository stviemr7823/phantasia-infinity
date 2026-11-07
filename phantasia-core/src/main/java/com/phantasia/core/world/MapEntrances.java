package com.phantasia.core.world;

import com.phantasia.core.logic.Direction;

/** Arrival checks and one-time suggestions for new maps and legacy imports. */
public final class MapEntrances {
    private static final InteriorTileSet TOWN = InteriorTileSet.standardTown(), DUNGEON = InteriorTileSet.standardDungeon();
    private MapEntrances() {}
    public static boolean isClear(InteriorMap map, WorldPosition p) {
        if (p == null || !map.inBounds(p.x(), p.y())) return false;
        InteriorTileSet set = switch (map.getTileSetId() == null ? "" : map.getTileSetId()) {
            case "town_standard" -> TOWN;
            case "dungeon_standard" -> DUNGEON;
            default -> null;
        };
        if (set == null) return false;
        TileSetEntry tile = set.getEntry(map.getTile(p.x(), p.y()));
        if (tile == null || !tile.passable() || map.getNpcAt(p.x(), p.y()) != null) return false;
        PlacedFeature f = map.getFeatureAt(p.x(), p.y());
        return f == null || (f.featureType() != PlacedFeatureType.LOCKED_DOOR
                && f.featureType() != PlacedFeatureType.STAIRS_LINK && f.featureType() != PlacedFeatureType.EXIT
                && f.featureType() != PlacedFeatureType.TRAP);
    }
    /** Preserves the traditional town entry when valid, otherwise finds a clear tile.
     * Called during creation/import only. Runtime never silently repairs bad entrances.
     */
    public static void addSuggestedDefault(InteriorMap map) {
        if (!map.getEntrances().isEmpty()) return;
        WorldPosition p = new WorldPosition(9, 13);
        if (!isClear(map, p)) {
            p = null;
            outer: for (int y = 0; y < map.getHeight(); y++) for (int x = 0; x < map.getWidth(); x++) {
                var candidate = new WorldPosition(x, y);
                if (isClear(map, candidate)) { p = candidate; break outer; }
            }
        }
        if (p != null) {
            map.addEntrance(new MapEntrance("entry", p, Direction.SOUTH));
            map.setDefaultEntranceId("entry");
        }
    }
}
