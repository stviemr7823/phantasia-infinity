package com.phantasia.editor.panels;

import com.phantasia.core.logic.Direction;
import com.phantasia.core.world.*;
import javax.swing.*;
import java.awt.*;

public final class EntranceEditForm extends JPanel {
    private final JTextField id = new JTextField(20), x, y;
    private final JComboBox<Direction> facing = new JComboBox<>(Direction.values());
    private final JCheckBox defaultEntry = new JCheckBox("Default arrival when entering this map");
    public EntranceEditForm(int px, int py, MapEntrance original, String defaultId) {
        super(new GridLayout(0, 2, 6, 6));
        id.setName("entranceId"); id.setText(original == null ? "entry" : original.id());
        x = MapPlacementFields.number("arrivalX", original != null && original.position() != null ? original.position().x() : px);
        y = MapPlacementFields.number("arrivalY", original != null && original.position() != null ? original.position().y() : py);
        facing.setName("facing"); facing.setSelectedItem(original == null || original.facing() == null ? Direction.SOUTH : original.facing());
        defaultEntry.setName("defaultEntrance"); defaultEntry.setSelected(defaultId == null || original != null && java.util.Objects.equals(original.id(), defaultId));
        add(new JLabel("Entrance name")); add(id); add(new JLabel("Arrival X")); add(x); add(new JLabel("Arrival Y")); add(y);
        add(new JLabel("Arrival facing")); add(facing); add(new JLabel("")); add(defaultEntry);
    }
    public MapEntrance readEntrance() {
        String key = id.getText().trim(); if (key.isEmpty()) throw new IllegalArgumentException("Entrance name is required");
        return new MapEntrance(key, new WorldPosition(MapPlacementFields.number(x), MapPlacementFields.number(y)), (Direction) facing.getSelectedItem());
    }
    public boolean isDefaultEntrance() { return defaultEntry.isSelected(); }
}
