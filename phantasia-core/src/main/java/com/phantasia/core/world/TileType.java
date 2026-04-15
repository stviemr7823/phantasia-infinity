// phantasia-core/src/main/java/com/phantasia/core/world/TileType.java
package com.phantasia.core.world;

/**
 * Terrain types.
 *
 * timerDrain: how many steps worth of encounter-timer this terrain
 * consumes per pace. Road is peaceful (drain 1), swamp is relentless
 * (drain 4). The timer range of 8-20 means:
 *   Road:     8-20 paces between encounters
 *   Plains:   5-13 paces
 *   Forest:   4-10 paces
 *   Mountain: 4-10 paces
 *   Swamp:    2-5  paces  (nearly constant danger)
 */
public enum TileType {
    OCEAN    (0, false),  // Impassable — drain irrelevant
    ROAD     (1, true),
    PLAINS   (2, true),
    FOREST   (3, true),
    MOUNTAIN (3, true),
    SWAMP    (4, true),
    TOWN     (0, true),   // No overland encounters
    DUNGEON  (0, true);   // Dungeon encounters handled separately

    public final int     timerDrain;
    public final boolean passable;

    TileType(int timerDrain, boolean passable) {
        this.timerDrain = timerDrain;
        this.passable   = passable;
    }
}