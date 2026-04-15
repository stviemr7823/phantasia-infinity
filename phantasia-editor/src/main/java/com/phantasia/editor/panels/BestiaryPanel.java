// phantasia-editor/src/main/java/com/phantasia/editor/panels/BestiaryPanel.java
package com.phantasia.editor.panels;

import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.editor.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Full bestiary editor — shows all monsters in a selectable list with
 * a detail form on the right. This is the "browse all" panel opened
 * when double-clicking the "Monsters" category in the asset explorer.
 */
public class BestiaryPanel extends JPanel implements EditorFrame.WorkspaceTab {

    private final JList<String>        list;
    private final DefaultListModel<String> listModel;
    private final JTextField nameField  = EditorUtils.field(16);
    private final JTextField hpField    = EditorUtils.field(5);
    private final JTextField maxField   = EditorUtils.field(5);
    private final JTextField xpField    = EditorUtils.field(8);
    private final JTextField tresField  = EditorUtils.field(8);
    private final JTextField itm1Field  = EditorUtils.field(5);
    private final JTextField itm2Field  = EditorUtils.field(5);
    private final JCheckBox  undeadBox  = EditorUtils.checkbox("Undead");
    private final JCheckBox  amorphBox  = EditorUtils.checkbox("Amorphous");
    private final JButton    addBtn     = EditorUtils.button("+ New");
    private final JButton    deleteBtn  = EditorUtils.button("Delete");

    private int  lastIndex = -1;
    private boolean loading = false;

    public BestiaryPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        // ── Left: monster list ──
        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);
        list.setFont(EditorTheme.FONT_FIELD);
        list.setFixedCellHeight(20);
        rebuildList();

        JPanel leftPanel = EditorUtils.titledPanel("Bestiary", new BorderLayout(0, 4));
        leftPanel.setPreferredSize(new Dimension(230, 0));
        leftPanel.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        listButtons.setOpaque(false);
        addBtn.addActionListener(e -> addMonster());
        deleteBtn.addActionListener(e -> deleteMonster());
        listButtons.add(addBtn);
        listButtons.add(deleteBtn);
        leftPanel.add(listButtons, BorderLayout.SOUTH);

        // ── Right: detail form ──
        JPanel form = EditorUtils.titledPanel("Monster Properties", new GridLayout(0, 1, 4, 2));
        EditorUtils.addFormRow(form, "Name",      nameField);
        EditorUtils.addFormRow(form, "HP",        hpField);
        EditorUtils.addFormRow(form, "Max Spawn", maxField);
        EditorUtils.addFormRow(form, "XP Reward", xpField);
        EditorUtils.addFormRow(form, "Treasure",  tresField);
        EditorUtils.addFormRow(form, "Item 1 ID", itm1Field);
        EditorUtils.addFormRow(form, "Item 2 ID", itm2Field);

        JPanel flags = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        flags.setOpaque(false);
        flags.add(undeadBox);
        flags.add(amorphBox);
        form.add(flags);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setOpaque(false);
        rightPanel.add(form, BorderLayout.NORTH);

        add(leftPanel,  BorderLayout.WEST);
        add(rightPanel, BorderLayout.CENTER);

        // ── Selection handling ──
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            if (lastIndex >= 0) commitToBlock(lastIndex);
            int sel = list.getSelectedIndex();
            loadToForm(sel);
            lastIndex = sel;
        });

        if (listModel.getSize() > 0) list.setSelectedIndex(0);
    }

    @Override
    public String getTabKey() { return "bestiary:all"; }

    // ── List management ──────────────────────────────────────────────────

    private void rebuildList() {
        listModel.clear();
        EditorState state = EditorState.get();
        for (int i = 0; i < state.getMonsterCount(); i++) {
            byte[] block = state.getMonster(i);
            String name = block != null ? new DataCore(block).getName().trim() : "";
            listModel.addElement(String.format("[%3d] %s", i + 1,
                    name.isEmpty() ? "???" : name));
        }
    }

    private void addMonster() {
        byte[] block = new byte[48];
        new DataCore(block).setName("New Monster");
        EditorState.get().addMonster(block);
        int idx = EditorState.get().getMonsterCount() - 1;
        EditorState.get().markDirty("monster:" + idx);
        rebuildList();
        list.setSelectedIndex(idx);
    }

    private void deleteMonster() {
        int idx = list.getSelectedIndex();
        if (idx < 0) return;
        byte[] block = EditorState.get().getMonster(idx);
        String name = block != null ? new DataCore(block).getName() : "???";
        if (!EditorUtils.confirm(this, "Delete '" + name.trim() + "'?", "Confirm Delete"))
            return;
        EditorState.get().removeMonster(idx);
        EditorState.get().markDirty("monster:deleted");
        lastIndex = -1;
        rebuildList();
        if (listModel.getSize() > 0)
            list.setSelectedIndex(Math.min(idx, listModel.getSize() - 1));
    }

    // ── Form binding ─────────────────────────────────────────────────────

    private void loadToForm(int idx) {
        if (idx < 0) return;
        byte[] block = EditorState.get().getMonster(idx);
        if (block == null) return;
        loading = true;
        DataCore core = new DataCore(block);
        nameField.setText(core.getName());
        hpField  .setText(String.valueOf(core.getStat(DataLayout.MON_HP)));
        maxField .setText(String.valueOf(core.getStat(DataLayout.MON_MAX_SPAWN)));
        xpField  .setText(String.valueOf(core.getShort(DataLayout.MON_XP)));
        tresField.setText(String.valueOf(core.getShort(DataLayout.MON_TREASURE)));
        itm1Field.setText(String.valueOf(core.getStat(DataLayout.MON_ITEM_1)));
        itm2Field.setText(String.valueOf(core.getStat(DataLayout.MON_ITEM_2)));
        int f = core.getStat(DataLayout.MON_FLAGS);
        undeadBox.setSelected((f & DataLayout.MON_FLAG_UNDEAD)    != 0);
        amorphBox.setSelected((f & DataLayout.MON_FLAG_AMORPHOUS) != 0);
        loading = false;
    }

    private void commitToBlock(int idx) {
        if (loading || idx < 0) return;
        byte[] block = EditorState.get().getMonster(idx);
        if (block == null) return;
        DataCore core = new DataCore(block);
        core.setName(nameField.getText());
        core.setStat(DataLayout.MON_HP,        EditorUtils.parseInt(hpField,   1));
        core.setStat(DataLayout.MON_MAX_SPAWN, EditorUtils.parseInt(maxField,  1));
        core.setShort(DataLayout.MON_XP,       EditorUtils.parseInt(xpField,   0));
        core.setShort(DataLayout.MON_TREASURE, EditorUtils.parseInt(tresField, 0));
        core.setStat(DataLayout.MON_ITEM_1,    EditorUtils.parseInt(itm1Field, 0));
        core.setStat(DataLayout.MON_ITEM_2,    EditorUtils.parseInt(itm2Field, 0));
        int flags = 0;
        if (undeadBox.isSelected()) flags |= DataLayout.MON_FLAG_UNDEAD;
        if (amorphBox.isSelected()) flags |= DataLayout.MON_FLAG_AMORPHOUS;
        core.setStat(DataLayout.MON_FLAGS, flags);
        EditorState.get().markDirty("monster:" + idx);
        // Update list label
        listModel.set(idx, String.format("[%3d] %s", idx + 1, core.getName()));
    }
}
