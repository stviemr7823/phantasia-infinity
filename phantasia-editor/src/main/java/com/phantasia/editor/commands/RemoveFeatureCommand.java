// phantasia-editor/src/main/java/com/phantasia/editor/commands/RemoveFeatureCommand.java
package com.phantasia.editor.commands;

import com.phantasia.editor.EditCommand;
import com.phantasia.core.world.InteriorMap;
import com.phantasia.core.world.PlacedFeature;

import java.util.Objects;

/**
 * Removes an interactive feature from an interior map.
 * Undo restores the feature; redo removes it again.
 */
public class RemoveFeatureCommand implements EditCommand {

    private final InteriorMap   map;
    private final PlacedFeature feature;
    private final String        dirtyKey;

    public RemoveFeatureCommand(InteriorMap map, PlacedFeature feature) {
        this.map      = Objects.requireNonNull(map);
        this.feature  = Objects.requireNonNull(feature);
        this.dirtyKey = "interiorMap:" + map.getId();
    }

    @Override
    public void execute() {
        map.removeFeature(feature);
    }

    @Override
    public void undo() {
        map.addFeature(feature);
    }

    @Override
    public String description() {
        return "Remove " + feature.featureType().name().toLowerCase().replace('_', ' ')
                + " at (" + feature.x() + ", " + feature.y() + ")"
                + " from " + map.getName();
    }

    @Override
    public String dirtyKey() {
        return dirtyKey;
    }
}
