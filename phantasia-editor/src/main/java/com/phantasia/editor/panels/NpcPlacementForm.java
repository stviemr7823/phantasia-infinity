package com.phantasia.editor.panels;

import com.phantasia.core.logic.Direction;
import com.phantasia.core.model.*;
import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/** Selects a reusable definition; editing placement never changes NPC identity/dialogue. */
public final class NpcPlacementForm extends JPanel {
    private record Choice(int id, String name) { @Override public String toString() { return "#" + id + " — " + name; } }
    private final JComboBox<Choice> npc = new JComboBox<>();
    private final JTextField x, y;
    private final JComboBox<Direction> facing = new JComboBox<>(Direction.values());
    private final JComboBox<Object> appear, disappear;
    public NpcPlacementForm(int px, int py, PlacedNpc original, Collection<NpcDefinition> definitions) {
        super(new GridLayout(0, 2, 6, 6)); npc.setName("npcId");
        Choice selected = null;
        for (var def : definitions) {
            var choice = new Choice(def.getId(), def.getName()); npc.addItem(choice);
            if (original != null && original.npcId() == def.getId()) selected = choice;
        }
        if (original != null && selected == null) { selected = new Choice(original.npcId(), "Missing definition"); npc.addItem(selected); }
        if (selected != null) npc.setSelectedItem(selected);
        x = MapPlacementFields.number("npcX", original == null ? px : original.x());
        y = MapPlacementFields.number("npcY", original == null ? py : original.y());
        facing.setName("facing"); facing.setSelectedItem(original == null ? Direction.SOUTH : original.facing());
        appear = MapPlacementFields.flags("appearFlag", original == null ? null : original.appearCondition());
        disappear = MapPlacementFields.flags("disappearFlag", original == null ? null : original.disappearCondition());
        add(new JLabel("NPC definition")); add(npc); add(new JLabel("X")); add(x); add(new JLabel("Y")); add(y);
        add(new JLabel("Facing")); add(facing); add(new JLabel("Appears after flag")); add(appear); add(new JLabel("Disappears after flag")); add(disappear);
    }
    public PlacedNpc readPlacement() {
        Choice choice = (Choice) npc.getSelectedItem();
        if (choice == null) throw new IllegalArgumentException("Create an NPC definition before placing it");
        var a = MapPlacementFields.flag(appear); var d = MapPlacementFields.flag(disappear);
        if (a != null && a == d) throw new IllegalArgumentException("Appearance and disappearance must use different flags");
        return new PlacedNpc(choice.id(), MapPlacementFields.number(x), MapPlacementFields.number(y), (Direction) facing.getSelectedItem(), a, d);
    }
}
