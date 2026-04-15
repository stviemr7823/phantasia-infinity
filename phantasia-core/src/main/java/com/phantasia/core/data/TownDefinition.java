// phantasia-core/src/main/java/com/phantasia/core/data/TownDefinition.java
package com.phantasia.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Links an overworld town feature to its authored interior map,
 * resident NPCs, and flavor text.
 *
 * TownDefinition is the bridge between the world map (where the town
 * appears as a feature marker) and the interior map (where the player
 * walks around, shops, and talks to NPCs).
 *
 * SERVICES:
 *   Towns no longer use service bitmasks. Services are determined by
 *   the PlacedFeatures on the interior map — if there's a shop counter,
 *   the town has a shop. This is enforced naturally by the editor.
 *
 * LIFECYCLE:
 *   - Authored in the editor's Town tab
 *   - FeatureRecord on the world map links to this definition by ID
 *   - Interior map is authored in the embedded interior map editor
 *   - Baked alongside the world map and interior data
 *   - Loaded at runtime when the player enters the town
 */
public class TownDefinition {

    private int              id;              // matches FeatureRecord.id
    private String           name;            // "Pendragon"
    private String           description;     // flavor text for the journal
    private int              interiorMapId;   // the authored town map
    private final List<Integer> residentNpcIds = new ArrayList<>();  // NPC definition IDs
    private String           arrivalDialogue; // text shown on first visit (nullable)

    public TownDefinition() {}

    public TownDefinition(int id, String name, int interiorMapId) {
        this.id            = id;
        this.name          = name;
        this.interiorMapId = interiorMapId;
    }

    // -------------------------------------------------------------------------
    // NPC management
    // -------------------------------------------------------------------------

    public void addResidentNpc(int npcId) {
        residentNpcIds.add(npcId);
    }

    /**
     * Removes the first occurrence of the given NPC ID from the roster.
     * No-op if the ID is not present.
     */
    public void removeResidentNpc(int npcId) {
        residentNpcIds.remove(Integer.valueOf(npcId));
    }

    /**
     * Removes the NPC at the given list index from the roster.
     * Matches the selection model used by the editor's NPC roster list.
     *
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public void removeResidentNpcAt(int index) {
        residentNpcIds.remove(index);
    }

    public List<Integer> getResidentNpcIds() {
        return Collections.unmodifiableList(residentNpcIds);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int    getId()              { return id; }
    public String getName()            { return name; }
    public String getDescription()     { return description; }
    public int    getInteriorMapId()   { return interiorMapId; }
    public String getArrivalDialogue() { return arrivalDialogue; }

    public void setId(int id)                       { this.id = id; }
    public void setName(String name)                { this.name = name; }
    public void setDescription(String desc)         { this.description = desc; }
    public void setInteriorMapId(int mapId)         { this.interiorMapId = mapId; }
    public void setArrivalDialogue(String dialogue) { this.arrivalDialogue = dialogue; }

    @Override
    public String toString() {
        return "Town[" + id + "] " + name + " (map=" + interiorMapId + ")";
    }
}