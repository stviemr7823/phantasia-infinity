package com.phantasia.editor.panels;

import com.phantasia.core.data.QuestFlag;
import javax.swing.*;

final class MapPlacementFields {
    private MapPlacementFields() {}
    static JComboBox<Object> flags(String name, QuestFlag selected) {
        var box = new JComboBox<Object>(); box.setName(name); box.addItem("(none)");
        for (var flag : QuestFlag.values()) box.addItem(flag);
        box.setSelectedItem(selected == null ? "(none)" : selected); return box;
    }
    static QuestFlag flag(JComboBox<Object> box) { return box.getSelectedItem() instanceof QuestFlag f ? f : null; }
    static JTextField number(String name, int value) {
        var input = new JTextField(Integer.toString(value), 8); input.setName(name); return input;
    }
    static int number(JTextField field) {
        try { int n = Integer.parseInt(field.getText().trim()); if (n < 0) throw new NumberFormatException(); return n; }
        catch (NumberFormatException e) { throw new IllegalArgumentException(field.getName() + " must be a nonnegative whole number"); }
    }
}
