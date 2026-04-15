// phantasia-editor/src/main/java/com/phantasia/editor/commands/PlaceFeatureCommand.java
package com.phantasia.editor.commands;

import com.phantasia.editor.EditCommand;
import com.phantasia.core.world.InteriorMap;
import com.phantasia.core.world.PlacedFeature;

import java.util.Objects;

/**
 * Places a new interactive feature on an interior map.
 * Undo removes the feature; redo places it again.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * PlacedFeature shopCounter = new PlacedFeature(5, 3, PlacedFeatureType.SHOP_COUNTER, ...);
 * EditorState.get().execute(new PlaceFeatureCommand(interiorMap, shopCounter));
 * }</pre>
 */
public class PlaceFeatureCommand implements EditCommand {

    private final InteriorMap   map;
    private final PlacedFeature feature;
    private final String        dirtyKey;

    public PlaceFeatureCommand(InteriorMap map, PlacedFeature feature) {
        this.map      = Objects.requireNonNull(map);
        this.feature  = Objects.requireNonNull(feature);
        this.dirtyKey = "interiorMap:" + map.getId();
    }

    @Override
    public void execute() {
        map.addFeature(feature);
    }

    @Override
    public void undo() {
        map.removeFeature(feature);
    }

    @Override
    public String description() {
        return "Place " + feature.featureType().name().toLowerCase().replace('_', ' ')
                + " at (" + feature.x() + ", " + feature.y() + ")"
                + " on " + map.getName();
    }

    @Override
    public String dirtyKey() {
        return dirtyKey;
    }
}
