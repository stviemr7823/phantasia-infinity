// phantasia-editor/src/main/java/com/phantasia/editor/commands/PlaceNpcCommand.java
package com.phantasia.editor.commands;

import com.phantasia.editor.EditCommand;
import com.phantasia.core.model.PlacedNpc;
import com.phantasia.core.world.InteriorMap;

import java.util.Objects;

/**
 * Places an NPC on an interior map (drag from asset explorer onto the grid).
 * Undo removes the placement; redo restores it.
 */
public class PlaceNpcCommand implements EditCommand {

    private final InteriorMap map;
    private final PlacedNpc   placedNpc;
    private final String      dirtyKey;

    public PlaceNpcCommand(InteriorMap map, PlacedNpc placedNpc) {
        this.map       = Objects.requireNonNull(map);
        this.placedNpc = Objects.requireNonNull(placedNpc);
        this.dirtyKey  = "interiorMap:" + map.getId();
    }

    @Override
    public void execute() {
        map.addNpc(placedNpc);
    }

    @Override
    public void undo() {
        map.removeNpc(placedNpc);
    }

    @Override
    public String description() {
        return "Place NPC " + placedNpc.npcId()
                + " at (" + placedNpc.x() + ", " + placedNpc.y() + ")"
                + " on " + map.getName();
    }

    @Override
    public String dirtyKey() {
        return dirtyKey;
    }
}