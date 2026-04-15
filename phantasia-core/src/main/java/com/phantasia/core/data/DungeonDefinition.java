// phantasia-core/src/main/java/com/phantasia/core/data/DungeonDefinition.java
package com.phantasia.core.data;

import com.phantasia.core.world.EncounterTable;
import com.phantasia.core.world.DungeonZone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Links an overworld dungeon feature to its authored floors,
 * encounter tables, and boss encounter.
 *
 * FLOOR PROGRESSION:
 *   Floors are ordered in a list. Floor 0 is the entry level.
 *   Conventional stair linking: stairs-down on floor N connects to
 *   stairs-up on floor N+1. No explicit stair-pair wiring needed.
 *
 * ENCOUNTER TABLES:
 *   Each floor carries its own {@link DungeonZone} with encounter tables
 *   and pacing data. Deeper floors can have harder encounter tables,
 *   shorter intervals, or different monster mixes.
 *
 * BOSS:
 *   The bossEncounter field names the monster for a scripted boss fight.
 *   The boss encounter triggers from a PlacedFeature (NPC with BOSS role
 *   or a special tile) on the final floor — not from the random encounter
 *   timer. Null means no boss.
 *
 * LIFECYCLE:
 *   - Authored in the editor's Dungeon tab
 *   - FeatureRecord on the world map links to this definition by ID
 *   - Each floor's interior map is authored in the embedded interior map editor
 *   - Baked to dungeons.dat alongside floor maps
 *   - Loaded at runtime when the player enters the dungeon
 */
public class DungeonDefinition {

    private int             id;
    private String          name;              // "Frostpeak Cavern"
    private String          description;       // journal flavor text
    private final List<DungeonFloorDef> floors = new ArrayList<>();
    private String          bossEncounter;     // monster name for boss fight (nullable)

    public DungeonDefinition() {}

    public DungeonDefinition(int id, String name) {
        this.id   = id;
        this.name = name;
    }

    // -------------------------------------------------------------------------
    // Floor management
    // -------------------------------------------------------------------------

    public void addFloor(DungeonFloorDef floor) {
        floors.add(floor);
    }

    public List<DungeonFloorDef> getFloors() {
        return Collections.unmodifiableList(floors);
    }

    public DungeonFloorDef getFloor(int index) {
        if (index < 0 || index >= floors.size()) return null;
        return floors.get(index);
    }

    public int getFloorCount() {
        return floors.size();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int    getId()             { return id; }
    public String getName()           { return name; }
    public String getDescription()    { return description; }
    public String getBossEncounter()  { return bossEncounter; }

    public void setId(int id)                       { this.id = id; }
    public void setName(String name)                { this.name = name; }
    public void setDescription(String desc)         { this.description = desc; }
    public void setBossEncounter(String monster)    { this.bossEncounter = monster; }

    @Override
    public String toString() {
        return "Dungeon[" + id + "] " + name
                + " (" + floors.size() + " floors"
                + (bossEncounter != null ? ", boss=" + bossEncounter : "")
                + ")";
    }
}