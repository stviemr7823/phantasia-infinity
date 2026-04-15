// phantasia-editor/src/main/java/com/phantasia/editor/commands/RemoveNpcCommand.java
package com.phantasia.editor.commands;

import com.phantasia.editor.EditCommand;
import com.phantasia.core.model.PlacedNpc;
import com.phantasia.core.world.InteriorMap;

import java.util.Objects;

/**
 * Removes an NPC placement from an interior map.
 * Undo restores the NPC; redo removes it again.
 */
public class RemoveNpcCommand implements EditCommand {

    private final InteriorMap map;
    private final PlacedNpc   placedNpc;
    private final String      dirtyKey;

    public RemoveNpcCommand(InteriorMap map, PlacedNpc placedNpc) {
        this.map       = Objects.requireNonNull(map);
        this.placedNpc = Objects.requireNonNull(placedNpc);
        this.dirtyKey  = "interiorMap:" + map.getId();
    }

    @Override
    public void execute() {
        map.removeNpc(placedNpc);
    }

    @Override
    public void undo() {
        map.addNpc(placedNpc);
    }

    @Override
    public String description() {
        return "Remove NPC " + placedNpc.npcId()
                + " from (" + placedNpc.x() + ", " + placedNpc.y() + ")"
                + " on " + map.getName();
    }

    @Override
    public String dirtyKey() {
        return dirtyKey;
    }
}