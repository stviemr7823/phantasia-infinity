// phantasia-core/src/main/java/com/phantasia/core/tools/DungeonEditorPanel.java
package com.phantasia.core.tools;

import com.phantasia.core.data.DataPaths;
import com.phantasia.core.world.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * Dungeon zone and encounter table editor.
 *
 * Zones are persisted to a plain-text file (dungeons.txt) in DataPaths.DAT_DIR.
 * Format — one zone per block, fields on separate lines:
 *   ZONE:<name>
 *   DEPTH:<n>
 *   DRAIN:<n>
 *   ENTRY:<MonsterName>,<min>,<max>,<weight>
 *   ...
 *   END
 */
public class DungeonEditorPanel extends JPanel {

    private static final String DUNGEON_FILE =
            DataPaths.DAT_DIR + "/dungeons.txt";

    private final List<DungeonZone>  zones     = new ArrayList<>();
    private final JList<String>      zoneList  = EditorUtils.styledList();
    private final JTextField         nameField  = EditorUtils.field(20);
    private final JTextField         depthField = EditorUtils.field(5);
    private final JTextField         drainField = EditorUtils.field(5);
    private final JTextArea          tableArea  = new JTextArea(8, 30);

    /**
     * Tracks which zone index is currently loaded in the form.
     * We commit the form to THIS index before switching, not after.
     */
    private int currentIndex = -1;

    public DungeonEditorPanel() {
        setLayout(new BorderLayout(8, 8));
        setBackground(PhantasiaEditor.BG_DARK);
        setBorder(new EmptyBorder(12, 12, 12, 12));

        tableArea.setBackground(PhantasiaEditor.BG_LIST);
        tableArea.setForeground(PhantasiaEditor.TEXT_MAIN);
        tableArea.setCaretColor(PhantasiaEditor.ACCENT);
        tableArea.setFont(PhantasiaEditor.FONT_FIELD);
        tableArea.setBorder(new EmptyBorder(6, 6, 6, 6));

        JPanel leftPanel = EditorUtils.titledPanel("Dungeon Zones", new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(200, 0));
        leftPanel.add(EditorUtils.scrollPane(zoneList), BorderLayout.CENTER);

        JPanel form = EditorUtils.titledPanel("Zone Properties",
                new GridLayout(0, 1, 4, 4));
        EditorUtils.addFormRow(form, "Zone Name",   nameField);
        EditorUtils.addFormRow(form, "Depth",       depthField);
        EditorUtils.addFormRow(form, "Timer Drain", drainField);

        JPanel tablePanel = EditorUtils.titledPanel(
                "Encounter Table  (MonsterName, min, max, weight — one per line)",
                new BorderLayout());
        tablePanel.add(EditorUtils.scrollPane(tableArea), BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setBackground(PhantasiaEditor.BG_DARK);
        right.add(form,       BorderLayout.NORTH);
        right.add(tablePanel, BorderLayout.CENTER);

        JButton addBtn  = EditorUtils.button("Add Zone");
        JButton delBtn  = EditorUtils.button("Delete Zone");
        JButton saveBtn = EditorUtils.accentButton("Save");
        JButton loadBtn = EditorUtils.button("Load");

        addBtn .addActionListener(e -> addZone());
        delBtn .addActionListener(e -> deleteZone());
        saveBtn.addActionListener(e -> saveZones());
        loadBtn.addActionListener(e -> loadZones());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setBackground(PhantasiaEditor.BG_DARK);
        buttons.add(addBtn);
        buttons.add(delBtn);
        buttons.add(saveBtn);
        buttons.add(loadBtn);

        add(leftPanel, BorderLayout.WEST);
        add(right,     BorderLayout.CENTER);
        add(buttons,   BorderLayout.SOUTH);

        zoneList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            // Commit form to the index we're LEAVING, not the one arriving
            if (currentIndex >= 0 && currentIndex < zones.size())
                commitForm(currentIndex);
            int next = zoneList.getSelectedIndex();
            currentIndex = next;
            loadToForm(next);
        });

        // Auto-load on startup, fall back to a seed zone if nothing exists
        if (!autoLoad()) addDefaultZone();
    }

    // ------------------------------------------------------------------
    // Zone management
    // ------------------------------------------------------------------

    private void addDefaultZone() {
        EncounterTable table = new EncounterTable(List.of(
                new EncounterTable.Entry("Skeleton", 1, 3, 60),
                new EncounterTable.Entry("Zombie",   1, 2, 40)
        ));
        addZoneToList(DungeonZone.entry("Entry Hall", table));
        zoneList.setSelectedIndex(0);
    }

    private void addZone() {
        EncounterTable table = new EncounterTable(List.of(
                new EncounterTable.Entry("Monster", 1, 2, 100)
        ));
        addZoneToList(new DungeonZone("New Zone", 1, 2, table));
        zoneList.setSelectedIndex(zones.size() - 1);
    }

    private void addZoneToList(DungeonZone zone) {
        zones.add(zone);
        ((DefaultListModel<String>) zoneList.getModel()).addElement(zone.name);
    }

    private void deleteZone() {
        int idx = zoneList.getSelectedIndex();
        if (idx < 0) return;
        if (!EditorUtils.confirm(this, "Delete zone?", "Confirm")) return;
        zones.remove(idx);
        ((DefaultListModel<String>) zoneList.getModel()).remove(idx);
        currentIndex = -1;
    }

    // ------------------------------------------------------------------
    // Form binding
    // ------------------------------------------------------------------

    private void loadToForm(int idx) {
        if (idx < 0 || idx >= zones.size()) return;
        DungeonZone zone = zones.get(idx);
        nameField .setText(zone.name);
        depthField.setText(String.valueOf(zone.depth));
        drainField.setText(String.valueOf(zone.timerDrain));

        StringBuilder sb = new StringBuilder();
        for (EncounterTable.Entry entry : zone.encounterTable.getEntries()) {
            sb.append(entry.monsterName()).append(", ")
                    .append(entry.minCount()).append(", ")
                    .append(entry.maxCount()).append(", ")
                    .append(entry.weight()).append("\n");
        }
        tableArea.setText(sb.toString());
    }

    /**
     * Reads form values and updates the zone at the given index.
     * Called when navigating AWAY from a zone — fixes the original bug where
     * applyChanges was called on the incoming index, corrupting zone data.
     */
    private void commitForm(int idx) {
        if (idx < 0 || idx >= zones.size()) return;

        String name  = nameField.getText().trim();
        int    depth = EditorUtils.parseInt(depthField, 1);
        int    drain = EditorUtils.parseInt(drainField, 2);

        List<EncounterTable.Entry> entries = new ArrayList<>();
        for (String line : tableArea.getText().split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",");
            if (parts.length < 4) continue;
            try {
                entries.add(new EncounterTable.Entry(
                        parts[0].trim(),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()),
                        Integer.parseInt(parts[3].trim())));
            } catch (NumberFormatException ignored) {}
        }

        if (entries.isEmpty()) return;  // don't overwrite with invalid data

        zones.set(idx, new DungeonZone(name, depth, drain,
                new EncounterTable(entries)));
        ((DefaultListModel<String>) zoneList.getModel()).set(idx, name);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    private boolean autoLoad() {
        File f = new File(DUNGEON_FILE);
        if (!f.exists()) return false;
        try {
            loadFromFile(f);
            return !zones.isEmpty();
        } catch (IOException ex) {
            System.err.println("[DungeonEditor] autoLoad failed: " + ex.getMessage());
            return false;
        }
    }

    private void loadZones() {
        JFileChooser fc = new JFileChooser(DataPaths.DAT_DIR);
        fc.setDialogTitle("Load dungeons.txt");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Dungeon files (*.txt)", "txt"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            loadFromFile(fc.getSelectedFile());
            if (!zones.isEmpty()) zoneList.setSelectedIndex(0);
        } catch (IOException ex) {
            EditorUtils.error(this, "Load failed: " + ex.getMessage());
        }
    }

    private void loadFromFile(File f) throws IOException {
        List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        zones.clear();
        ((DefaultListModel<String>) zoneList.getModel()).clear();
        currentIndex = -1;

        String name = ""; int depth = 1; int drain = 2;
        List<EncounterTable.Entry> entries = new ArrayList<>();

        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("ZONE:"))  { name  = line.substring(5); }
            else if (line.startsWith("DEPTH:")) { depth = Integer.parseInt(line.substring(6).trim()); }
            else if (line.startsWith("DRAIN:")) { drain = Integer.parseInt(line.substring(6).trim()); }
            else if (line.startsWith("ENTRY:")) {
                String[] p = line.substring(6).split(",");
                if (p.length >= 4)
                    entries.add(new EncounterTable.Entry(
                            p[0].trim(),
                            Integer.parseInt(p[1].trim()),
                            Integer.parseInt(p[2].trim()),
                            Integer.parseInt(p[3].trim())));
            } else if (line.equals("END") && !entries.isEmpty()) {
                addZoneToList(new DungeonZone(name, depth, drain,
                        new EncounterTable(new ArrayList<>(entries))));
                entries.clear();
                name = ""; depth = 1; drain = 2;
            }
        }
    }

    private void saveZones() {
        // Commit whatever is currently in the form before saving
        if (currentIndex >= 0) commitForm(currentIndex);

        DataPaths.ensureParentDirs(DUNGEON_FILE);
        try (PrintWriter pw = new PrintWriter(new FileWriter(DUNGEON_FILE))) {
            for (DungeonZone zone : zones) {
                pw.println("ZONE:" + zone.name);
                pw.println("DEPTH:" + zone.depth);
                pw.println("DRAIN:" + zone.timerDrain);
                for (EncounterTable.Entry e : zone.encounterTable.getEntries())
                    pw.printf("ENTRY:%s,%d,%d,%d%n",
                            e.monsterName(), e.minCount(), e.maxCount(), e.weight());
                pw.println("END");
            }
            EditorUtils.confirm(this, "Saved " + zones.size()
                    + " zones to " + DUNGEON_FILE);
        } catch (IOException ex) {
            EditorUtils.error(this, "Save failed: " + ex.getMessage());
        }
    }
}