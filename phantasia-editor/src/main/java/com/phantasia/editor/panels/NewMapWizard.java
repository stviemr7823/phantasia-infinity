// phantasia-editor/src/main/java/com/phantasia/editor/panels/NewMapWizard.java
package com.phantasia.editor.panels;

import com.phantasia.core.world.*;
import com.phantasia.editor.*;
import com.phantasia.editor.generators.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * "New Map" wizard dialog — the primary entry point for creating interior
 * maps. Accessible from Maps → New Town Map / New Dungeon Map, or Ctrl+N.
 *
 * The wizard prompts for map name, type, dimensions, and offers optional
 * procedural generation. On "Create", it:
 *   1. Creates an InteriorMap in EditorState
 *   2. Optionally runs the town/dungeon generator
 *   3. Opens the InteriorMapEditorPanel in the workspace
 *
 * This replaces the buried right-click → New workflow with a single
 * prominent action.
 */
public class NewMapWizard {

    /**
     * Shows the wizard and returns the created InteriorMap, or null if cancelled.
     */
    public static InteriorMap show(Component parent, InteriorMapType defaultType) {
        JTextField nameField = new JTextField(
                defaultType == InteriorMapType.TOWN ? "New Town" : "New Dungeon", 20);
        nameField.setFont(EditorTheme.FONT_FIELD);

        JComboBox<String> typeBox = new JComboBox<>(new String[]{"Town", "Dungeon"});
        typeBox.setSelectedIndex(defaultType == InteriorMapType.TOWN ? 0 : 1);

        JTextField widthField  = new JTextField(
                defaultType == InteriorMapType.TOWN ? "24" : "40", 5);
        JTextField heightField = new JTextField(
                defaultType == InteriorMapType.TOWN ? "20" : "30", 5);
        widthField.setFont(EditorTheme.FONT_FIELD);
        heightField.setFont(EditorTheme.FONT_FIELD);

        JCheckBox generateBox = new JCheckBox("Auto-generate layout", true);
        generateBox.setToolTipText(
                "Generate a starting layout with rooms/buildings. You can hand-edit it afterward.");

        JSlider densitySlider = new JSlider(20, 80, 50);
        densitySlider.setMajorTickSpacing(20);
        densitySlider.setPaintTicks(true);
        densitySlider.setPaintLabels(true);
        densitySlider.setToolTipText("Building/room density for the generator");

        JTextField seedField = new JTextField("-1", 8);
        seedField.setFont(EditorTheme.FONT_FIELD);

        // Update defaults when type changes
        typeBox.addActionListener(e -> {
            boolean town = typeBox.getSelectedIndex() == 0;
            if (widthField.getText().equals(town ? "40" : "24"))
                widthField.setText(town ? "24" : "40");
            if (heightField.getText().equals(town ? "30" : "20"))
                heightField.setText(town ? "20" : "30");
        });

        // Layout
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.setBorder(new EmptyBorder(10, 10, 10, 10));

        form.add(new JLabel("Map Name:"));       form.add(nameField);
        form.add(new JLabel("Map Type:"));        form.add(typeBox);
        form.add(new JLabel("Width:"));           form.add(widthField);
        form.add(new JLabel("Height:"));          form.add(heightField);
        form.add(new JLabel(""));                 form.add(generateBox);
        form.add(new JLabel("Density %:"));       form.add(densitySlider);
        form.add(new JLabel("Seed (-1=random):")); form.add(seedField);

        JPanel wrapper = new JPanel(new BorderLayout());
        JLabel header = new JLabel("Create a New Interior Map");
        header.setFont(EditorTheme.FONT_HEADER);
        header.setForeground(EditorTheme.ACCENT);
        header.setBorder(new EmptyBorder(10, 10, 5, 10));
        wrapper.add(header, BorderLayout.NORTH);
        wrapper.add(form, BorderLayout.CENTER);

        JLabel hint = new JLabel("<html><i>Tip: Generate a layout, then hand-paint to refine.</i></html>");
        hint.setFont(EditorTheme.FONT_LABEL);
        hint.setForeground(EditorTheme.TEXT_DIM);
        hint.setBorder(new EmptyBorder(5, 10, 10, 10));
        wrapper.add(hint, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(parent, wrapper,
                "New Map Wizard", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return null;

        // ── Parse inputs ─────────────────────────────────────────────────
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = "Untitled Map";

        boolean isTown = typeBox.getSelectedIndex() == 0;
        InteriorMapType mapType = isTown ? InteriorMapType.TOWN : InteriorMapType.DUNGEON;

        int width, height;
        try { width  = Math.max(8, Math.min(80, Integer.parseInt(widthField.getText().trim()))); }
        catch (NumberFormatException e) { width = isTown ? 24 : 40; }
        try { height = Math.max(8, Math.min(80, Integer.parseInt(heightField.getText().trim()))); }
        catch (NumberFormatException e) { height = isTown ? 20 : 30; }

        long seed = -1;
        try { seed = Long.parseLong(seedField.getText().trim()); }
        catch (NumberFormatException ignored) {}

        double density = densitySlider.getValue() / 100.0;

        // ── Create the map ───────────────────────────────────────────────
        EditorState state = EditorState.get();
        int id = state.nextInteriorMapId();

        InteriorMap map;
        if (generateBox.isSelected()) {
            if (isTown) {
                map = TownLayoutGenerator.generate(id, name, width, height, density, seed);
            } else {
                // Generate a dungeon using EnhancedDungeonGenerator, then
                // convert the DungeonFloor to an InteriorMap
                EnhancedDungeonGenerator.Config cfg = new EnhancedDungeonGenerator.Config(
                        width, height, (int)(density * 20), 4, 8, 1,
                        0.1, 0.5, true, true, seed);
                DungeonFloor floor = EnhancedDungeonGenerator.generate(cfg);
                map = new InteriorMap(id, name, InteriorMapType.DUNGEON,
                        width, height, "dungeon_standard");
                for (int x = 0; x < width; x++)
                    for (int y = 0; y < height; y++)
                        map.setTile(x, y, floor.getTile(x, y).ordinal());
            }
        } else {
            // Blank map
            map = new InteriorMap(id, name, mapType, width, height,
                    isTown ? "town_standard" : "dungeon_standard");
            int defaultTile = isTown ? 0 : 1; // wood_floor or wall
            for (int x = 0; x < width; x++)
                for (int y = 0; y < height; y++)
                    map.setTile(x, y, defaultTile);
        }

        state.putInteriorMap(map);
        state.markDirty("interiorMap:" + id);
        return map;
    }
}