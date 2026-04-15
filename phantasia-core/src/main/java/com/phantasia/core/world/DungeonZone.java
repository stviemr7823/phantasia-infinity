// phantasia-core/src/main/java/com/phantasia/core/world/DungeonZone.java
package com.phantasia.core.world;

/**
 * A named region within a dungeon with its own encounter table
 * and pacing characteristics.
 *
 * Zone transitions happen when the party crosses a threshold —
 * a staircase, a sealed door, or a coordinate boundary defined
 * in the dungeon layout. Each transition calls
 * DungeonEncounterManager.enterZone() with the new zone.
 *
 * depth is informational — used for difficulty scaling and display.
 * timerDrain controls how quickly the step timer burns in this zone.
 * Higher drain = more frequent encounters = deeper/more dangerous.
 *
 * Example progression for the Pendragon Archives:
 *   Entry Hall      depth=1  drain=2  (Skeletons, Zombies)
 *   Deep Corridors  depth=3  drain=3  (Wights, Wraiths)
 *   Inner Sanctum   depth=5  drain=4  (High Demons, Vampires)
 */
public class DungeonZone {

    public final String         name;
    public final int            depth;
    public final int            timerDrain;
    public final EncounterTable encounterTable;

    public DungeonZone(String name, int depth,
                       int timerDrain, EncounterTable encounterTable) {
        if (encounterTable == null) {
            throw new IllegalArgumentException(
                    "DungeonZone '" + name + "' must have an EncounterTable.");
        }
        this.name           = name;
        this.depth          = depth;
        this.timerDrain     = timerDrain;
        this.encounterTable = encounterTable;
    }

    // -------------------------------------------------------------------------
    // Named constructors — readable authoring
    // -------------------------------------------------------------------------

    /** Shallow zone — light encounters, slow drain. */
    public static DungeonZone entry(String name, EncounterTable table) {
        return new DungeonZone(name, 1, 2, table);
    }

    /** Mid-depth zone — moderate encounters. */
    public static DungeonZone mid(String name, int depth, EncounterTable table) {
        return new DungeonZone(name, depth, 3, table);
    }

    /** Deep zone — relentless encounters, fast drain. */
    public static DungeonZone deep(String name, int depth, EncounterTable table) {
        return new DungeonZone(name, depth, 4, table);
    }

    @Override
    public String toString() {
        return name + " (depth " + depth + ", drain " + timerDrain + ")";
    }
}