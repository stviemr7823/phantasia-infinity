package com.phantasia.editor.commands;

import com.phantasia.core.world.*;
import com.phantasia.editor.EditCommand;
import java.util.*;

/** Entrance edits never rewrite other maps' links; broken references remain visible. */
public final class EditEntranceCommand implements EditCommand {
    private final InteriorMap map;
    private final List<MapEntrance> before, after;
    private final String oldDefault, newDefault;
    public EditEntranceCommand(InteriorMap map, MapEntrance oldEntry, MapEntrance newEntry, boolean makeDefault) {
        this.map = Objects.requireNonNull(map); before = List.copyOf(map.getEntrances()); oldDefault = map.getDefaultEntranceId();
        if (oldEntry == null && newEntry == null) throw new IllegalArgumentException("No entrance change");
        if (newEntry != null && (newEntry.id() == null || newEntry.id().isBlank())) throw new IllegalArgumentException("Entrance name is required");
        for (var e : before) if (!Objects.equals(e, oldEntry) && newEntry != null && Objects.equals(e.id(), newEntry.id()))
            throw new IllegalArgumentException("Entrance name already exists: " + newEntry.id());
        var changed = new ArrayList<>(before);
        if (oldEntry != null) {
            int i = changed.indexOf(oldEntry); if (i < 0) throw new IllegalArgumentException("Entrance no longer exists");
            if (newEntry == null) changed.remove(i); else changed.set(i, newEntry);
        } else changed.add(newEntry);
        after = List.copyOf(changed);
        boolean editingDefault = oldEntry != null && Objects.equals(oldEntry.id(), oldDefault);
        newDefault = makeDefault && newEntry != null ? newEntry.id() : editingDefault ? null : oldDefault;
    }
    private void apply(List<MapEntrance> entries, String defaultId) {
        for (var e : List.copyOf(map.getEntrances())) map.removeEntrance(e);
        for (var e : entries) map.addEntrance(e); map.setDefaultEntranceId(defaultId);
    }
    @Override public void execute() { apply(after, newDefault); }
    @Override public void undo() { apply(before, oldDefault); }
    @Override public String dirtyKey() { return "interiorMap:" + map.getId(); }
    @Override public String description() { return "Edit entrance on " + map.getName(); }
}
