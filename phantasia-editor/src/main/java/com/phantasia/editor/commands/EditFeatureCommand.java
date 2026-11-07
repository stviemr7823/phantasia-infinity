package com.phantasia.editor.commands;

import com.phantasia.core.world.*;
import com.phantasia.editor.EditCommand;
import java.util.*;

/** A single undo step for placement, replacement or removal; preserves list order. */
public final class EditFeatureCommand implements EditCommand {
    private final InteriorMap map;
    private final List<PlacedFeature> before, after;
    private final String description;

    public EditFeatureCommand(InteriorMap map, PlacedFeature oldFeature, PlacedFeature newFeature) {
        this.map = Objects.requireNonNull(map);
        if (oldFeature == null && newFeature == null) throw new IllegalArgumentException("No feature change");
        before = List.copyOf(map.getFeatures());
        var changed = new ArrayList<>(before);
        if (oldFeature != null) {
            int index = changed.indexOf(oldFeature);
            if (index < 0) throw new IllegalArgumentException("Feature no longer exists");
            if (newFeature == null) changed.remove(index); else changed.set(index, newFeature);
        } else changed.add(Objects.requireNonNull(newFeature));
        after = List.copyOf(changed);
        description = (newFeature == null ? "Remove" : oldFeature == null ? "Place" : "Edit")
                + " feature on " + map.getName();
    }
    private void apply(List<PlacedFeature> features) {
        for (var f : List.copyOf(map.getFeatures())) map.removeFeature(f);
        for (var f : features) map.addFeature(f);
    }
    @Override public void execute() { apply(after); }
    @Override public void undo() { apply(before); }
    @Override public String description() { return description; }
    @Override public String dirtyKey() { return "interiorMap:" + map.getId(); }
}
