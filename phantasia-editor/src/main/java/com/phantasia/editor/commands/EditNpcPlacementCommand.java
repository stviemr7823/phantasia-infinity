package com.phantasia.editor.commands;

import com.phantasia.core.model.PlacedNpc;
import com.phantasia.core.world.InteriorMap;
import com.phantasia.editor.EditCommand;
import java.util.*;

public final class EditNpcPlacementCommand implements EditCommand {
    private final InteriorMap map;
    private final List<PlacedNpc> before, after;
    public EditNpcPlacementCommand(InteriorMap map, PlacedNpc oldNpc, PlacedNpc newNpc) {
        this.map = Objects.requireNonNull(map); before = List.copyOf(map.getPlacedNpcs());
        var changed = new ArrayList<>(before);
        if (oldNpc == null && newNpc == null) throw new IllegalArgumentException("No NPC change");
        if (oldNpc != null) {
            int i = changed.indexOf(oldNpc); if (i < 0) throw new IllegalArgumentException("Placement no longer exists");
            if (newNpc == null) changed.remove(i); else changed.set(i, newNpc);
        } else changed.add(newNpc);
        after = List.copyOf(changed);
    }
    private void apply(List<PlacedNpc> list) { for (var n : List.copyOf(map.getPlacedNpcs())) map.removeNpc(n); for (var n : list) map.addNpc(n); }
    @Override public void execute() { apply(after); }
    @Override public void undo() { apply(before); }
    @Override public String dirtyKey() { return "interiorMap:" + map.getId(); }
    @Override public String description() { return "Edit NPC placement on " + map.getName(); }
}
