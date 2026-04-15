// phantasia-editor/src/main/java/com/phantasia/editor/panels/TownEditorPanel.java
package com.phantasia.editor.panels;

import com.phantasia.core.data.TownDefinition;
import com.phantasia.core.model.NpcDefinition;
import com.phantasia.core.world.InteriorMap;
import com.phantasia.core.world.InteriorMapType;
import com.phantasia.editor.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Town Editor — town properties, NPC roster management, arrival dialogue,
 * and link to the interior map for editing.
 *
 * Towns are authored as TownDefinitions that link to an InteriorMap.
 * This panel manages the TownDefinition; clicking "Edit Map" opens
 * the InteriorMapEditorPanel for the linked map.
 */
public class TownEditorPanel extends JPanel implements EditorFrame.WorkspaceTab {

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> townList = new JList<>(listModel);

    private final JTextField nameField    = EditorUtils.field(20);
    private final JTextArea  descArea     = new JTextArea(3, 30);
    private final JTextField mapIdField   = EditorUtils.field(5);
    private final JTextArea  arrivalArea  = new JTextArea(3, 30);

    // NPC roster
    private final DefaultListModel<String> npcModel = new DefaultListModel<>();
    private final JList<String> npcRosterList = new JList<>(npcModel);
    private final JTextField addNpcIdField = EditorUtils.field(5);

    private int currentTownIdx = -1;
    private boolean loading = false;

    /** Callback to open an interior map in the workspace. */
    private Runnable onEditMap;

    public TownEditorPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // ── Left: town list ──────────────────────────────────────────────
        rebuildTownList();
        townList.setFont(EditorTheme.FONT_FIELD);

        JPanel leftPanel = EditorUtils.titledPanel("Towns", new BorderLayout(0, 4));
        leftPanel.setPreferredSize(new Dimension(200, 0));
        leftPanel.add(new JScrollPane(townList), BorderLayout.CENTER);

        JPanel tBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tBtns.setOpaque(false);
        JButton addT = EditorUtils.button("+ New");
        JButton delT = EditorUtils.button("Delete");
        addT.addActionListener(e -> addTown());
        delT.addActionListener(e -> deleteTown());
        tBtns.add(addT);
        tBtns.add(delT);
        leftPanel.add(tBtns, BorderLayout.SOUTH);

        // ── Center: properties ───────────────────────────────────────────
        JPanel propsPanel = EditorUtils.titledPanel("Town Properties", new GridLayout(0, 1, 4, 2));
        EditorUtils.addFormRow(propsPanel, "Name", nameField);

        descArea.setFont(EditorTheme.FONT_FIELD);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        propsPanel.add(EditorUtils.label("Description:"));
        propsPanel.add(new JScrollPane(descArea));

        EditorUtils.addFormRow(propsPanel, "Interior Map ID", mapIdField);

        JButton editMapBtn = EditorUtils.accentButton("Edit Interior Map →");
        editMapBtn.addActionListener(e -> editInteriorMap());
        propsPanel.add(editMapBtn);

        JButton createMapBtn = EditorUtils.button("Create New Map for Town");
        createMapBtn.addActionListener(e -> createMapForTown());
        propsPanel.add(createMapBtn);

        // Arrival dialogue
        JPanel arrivalPanel = EditorUtils.titledPanel("Arrival Dialogue", new BorderLayout());
        arrivalArea.setFont(EditorTheme.FONT_FIELD);
        arrivalArea.setLineWrap(true);
        arrivalArea.setWrapStyleWord(true);
        arrivalPanel.add(new JScrollPane(arrivalArea), BorderLayout.CENTER);

        // NPC roster
        JPanel npcPanel = EditorUtils.titledPanel("Resident NPCs", new BorderLayout(4, 4));
        npcRosterList.setFont(EditorTheme.FONT_SMALL);
        npcPanel.add(new JScrollPane(npcRosterList), BorderLayout.CENTER);

        JPanel npcBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        npcBtns.setOpaque(false);
        npcBtns.add(EditorUtils.label("NPC ID:"));
        npcBtns.add(addNpcIdField);
        JButton addNpc = EditorUtils.button("Add");
        JButton removeNpc = EditorUtils.button("Remove Selected");
        addNpc.addActionListener(e -> addNpcToRoster());
        removeNpc.addActionListener(e -> removeNpcFromRoster());
        npcBtns.add(addNpc);
        npcBtns.add(removeNpc);
        npcPanel.add(npcBtns, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setOpaque(false);
        center.add(propsPanel, BorderLayout.NORTH);
        center.add(arrivalPanel, BorderLayout.CENTER);
        center.add(npcPanel, BorderLayout.SOUTH);

        add(leftPanel, BorderLayout.WEST);
        add(center,    BorderLayout.CENTER);

        townList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            commitCurrentTown();
            currentTownIdx = townList.getSelectedIndex();
            loadTownToForm(currentTownIdx);
        });

        if (listModel.getSize() > 0) townList.setSelectedIndex(0);
    }

    @Override
    public String getTabKey() { return "towns:all"; }

    public void setOnEditMap(Runnable handler) { this.onEditMap = handler; }

    // ── Town list ────────────────────────────────────────────────────────

    private void rebuildTownList() {
        listModel.clear();
        for (var entry : EditorState.get().getTowns().entrySet())
            listModel.addElement("[" + entry.getKey() + "] " + entry.getValue().getName());
    }

    private void addTown() {
        String name = JOptionPane.showInputDialog(this, "Town name:", "New Town");
        if (name == null || name.isBlank()) return;
        EditorState state = EditorState.get();
        int id = state.nextTownId();
        TownDefinition town = new TownDefinition(id, name, -1);
        state.putTown(town);
        state.markDirty("town:" + id);
        rebuildTownList();
        townList.setSelectedIndex(listModel.getSize() - 1);
    }

    private void deleteTown() {
        if (currentTownIdx < 0) return;
        List<Integer> ids = new ArrayList<>(EditorState.get().getTowns().keySet());
        if (currentTownIdx >= ids.size()) return;
        if (!EditorUtils.confirm(this, "Delete this town?", "Confirm")) return;
        EditorState.get().removeTown(ids.get(currentTownIdx));
        currentTownIdx = -1;
        rebuildTownList();
    }

    // ── Form binding ─────────────────────────────────────────────────────

    private TownDefinition getCurrentTown() {
        List<Integer> ids = new ArrayList<>(EditorState.get().getTowns().keySet());
        if (currentTownIdx < 0 || currentTownIdx >= ids.size()) return null;
        return EditorState.get().getTown(ids.get(currentTownIdx));
    }

    private void loadTownToForm(int idx) {
        TownDefinition town = getCurrentTown();
        if (town == null) return;
        loading = true;
        nameField.setText(town.getName());
        descArea.setText(town.getDescription() != null ? town.getDescription() : "");
        mapIdField.setText(String.valueOf(town.getInteriorMapId()));
        arrivalArea.setText(town.getArrivalDialogue() != null ? town.getArrivalDialogue() : "");

        // NPC roster
        npcModel.clear();
        EditorState state = EditorState.get();
        for (int npcId : town.getResidentNpcIds()) {
            NpcDefinition npc = state.getNpc(npcId);
            npcModel.addElement("[" + npcId + "] "
                    + (npc != null ? npc.getName() : "Unknown NPC"));
        }
        loading = false;
    }

    private void commitCurrentTown() {
        if (loading) return;
        TownDefinition town = getCurrentTown();
        if (town == null) return;
        town.setName(nameField.getText().trim());
        town.setDescription(descArea.getText().trim());
        try { town.setInteriorMapId(Integer.parseInt(mapIdField.getText().trim())); }
        catch (NumberFormatException ignored) {}
        town.setArrivalDialogue(arrivalArea.getText().trim());
        EditorState.get().markDirty("town:" + town.getId());
    }

    // ── NPC roster ───────────────────────────────────────────────────────

    private void addNpcToRoster() {
        TownDefinition town = getCurrentTown();
        if (town == null) return;
        try {
            int npcId = Integer.parseInt(addNpcIdField.getText().trim());
            town.addResidentNpc(npcId);
            EditorState.get().markDirty("town:" + town.getId());
            loadTownToForm(currentTownIdx);
        } catch (NumberFormatException ignored) {}
    }

    private void removeNpcFromRoster() {
        TownDefinition town = getCurrentTown();
        if (town == null) return;
        int sel = npcRosterList.getSelectedIndex();
        if (sel < 0 || sel >= town.getResidentNpcIds().size()) return;
        town.removeResidentNpcAt(sel);
        EditorState.get().markDirty("town:" + town.getId());
        loadTownToForm(currentTownIdx);
    }

    // ── Map operations ───────────────────────────────────────────────────

    private void editInteriorMap() {
        TownDefinition town = getCurrentTown();
        if (town == null) return;
        int mapId = town.getInteriorMapId();
        if (mapId < 0) {
            EditorUtils.info(this, "No interior map linked. Create one first.");
            return;
        }
        // Signal EditorFrame to open the interior map
        // We use the openHandler pattern from AssetExplorer
        if (onEditMap != null) onEditMap.run();
        // Fallback: try to open via the workspace
        SwingUtilities.getWindowAncestor(this);
    }

    private void createMapForTown() {
        TownDefinition town = getCurrentTown();
        if (town == null) return;
        EditorState state = EditorState.get();
        int mapId = state.nextInteriorMapId();
        InteriorMap map = new InteriorMap(mapId, town.getName(),
                InteriorMapType.TOWN, 24, 20, "town_standard");
        for (int x = 0; x < 24; x++)
            for (int y = 0; y < 20; y++)
                map.setTile(x, y, 0);
        state.putInteriorMap(map);
        state.markDirty("interiorMap:" + mapId);
        town.setInteriorMapId(mapId);
        mapIdField.setText(String.valueOf(mapId));
        EditorUtils.info(this, "Created interior map '" + town.getName()
                + "' (ID " + mapId + ", 24×20).");
    }
}
