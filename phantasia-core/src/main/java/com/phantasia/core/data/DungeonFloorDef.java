// phantasia-core/src/main/java/com/phantasia/core/data/DungeonFloorDef.java
package com.phantasia.core.data;

import com.phantasia.core.world.DungeonZone;

/**
 * Defines a single floor within a {@link DungeonDefinition}.
 *
 * Each floor links to an authored {@link com.phantasia.core.world.InteriorMap}
 * and carries its own encounter zone data. The tile set theme allows
 * visual variation between floors — the first floor might be stone,
 * the deeper levels ice or flooded caverns.
 *
 * CONVENTIONAL LINKING:
 *   Floors are ordered in the parent DungeonDefinition's list.
 *   Stairs-down on floor N automatically connects to stairs-up on floor N+1.
 *   No explicit stair-pair wiring is needed.
 *
 * @param floorIndex    position in the dungeon (0 = entry level)
 * @param interiorMapId the authored floor map (references InteriorMap.id)
 * @param zone          encounter table and pacing for this floor
 * @param tileSetTheme  tile set variant ("dungeon_standard", "dungeon_ice", etc.)
 */
public record DungeonFloorDef(
        int         floorIndex,
        int         interiorMapId,
        DungeonZone zone,
        String      tileSetTheme
) {

    /** Convenience constructor using the standard dungeon tile set. */
    public DungeonFloorDef(int floorIndex, int interiorMapId, DungeonZone zone) {
        this(floorIndex, interiorMapId, zone, "dungeon_standard");
    }
}
