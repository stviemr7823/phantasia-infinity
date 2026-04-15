// phantasia-editor/src/main/java/com/phantasia/editor/panels/SpellBookPanel.java
package com.phantasia.editor.panels;

import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.editor.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Full spell book editor — list + detail form for all spells.
 * Opened when double-clicking the "Spells" category header.
 */
public class SpellBookPanel extends JPanel implements EditorFrame.WorkspaceTab {

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String>            list      = new JList<>(listModel);
    private final JTextField idField    = EditorUtils.field(5);
    private final JTextField nameField  = EditorUtils.field(16);
    private final JTextField costField  = EditorUtils.field(5);
    private final JTextField powerField = EditorUtils.field(5);
    private final JTextField levelField = EditorUtils.field(5);
    private final JComboBox<String> targetBox     = new JComboBox<>(new String[]{"Single enemy","All enemies","Single ally","All allies"});
    private final JComboBox<String> spellTypeBox  = new JComboBox<>(new String[]{"Wizard","Priest"});
    private final JComboBox<String> effectTypeBox = new JComboBox<>(new String[]{"Damage","Heal","Sleep","Awaken","Buff","Debuff","Utility"});

    private int lastIndex = -1;
    private boolean loading = false;

    public SpellBookPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        list.setFont(EditorTheme.FONT_FIELD);
        list.setFixedCellHeight(20);
        rebuildList();

        JPanel leftPanel = EditorUtils.titledPanel("Spell Book", new BorderLayout(0, 4));
        leftPanel.setPreferredSize(new Dimension(230, 0));
        leftPanel.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel listBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        listBtns.setOpaque(false);
        JButton addBtn = EditorUtils.button("+ New");
        JButton delBtn = EditorUtils.button("Delete");
        addBtn.addActionListener(e -> addSpell());
        delBtn.addActionListener(e -> deleteSpell());
        listBtns.add(addBtn);
        listBtns.add(delBtn);
        leftPanel.add(listBtns, BorderLayout.SOUTH);

        JPanel form = EditorUtils.titledPanel("Spell Properties", new GridLayout(0, 1, 4, 2));
        EditorUtils.addFormRow(form, "Spell ID",    idField);
        EditorUtils.addFormRow(form, "Name",        nameField);
        EditorUtils.addFormRow(form, "MP Cost",     costField);
        EditorUtils.addFormRow(form, "Power",       powerField);
        EditorUtils.addFormRow(form, "Level Req",   levelField);
        EditorUtils.addFormRow(form, "Target",      targetBox);
        EditorUtils.addFormRow(form, "Spell Type",  spellTypeBox);
        EditorUtils.addFormRow(form, "Effect Type", effectTypeBox);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setOpaque(false);
        rightPanel.add(form, BorderLayout.NORTH);

        add(leftPanel,  BorderLayout.WEST);
        add(rightPanel, BorderLayout.CENTER);

        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            if (lastIndex >= 0) commitToBlock(lastIndex);
            int sel = list.getSelectedIndex();
            loadToForm(sel);
            lastIndex = sel;
        });

        if (listModel.getSize() > 0) list.setSelectedIndex(0);
    }

    @Override public String getTabKey() { return "spellbook:all"; }

    private void rebuildList() {
        listModel.clear();
        EditorState state = EditorState.get();
        for (int i = 0; i < state.getSpellCount(); i++) {
            byte[] block = state.getSpell(i);
            if (block == null || EditorUtils.isEmptyBlock(block)) {
                listModel.addElement(String.format("[%3d] (empty)", i + 1));
                continue;
            }
            DataCore c = new DataCore(block);
            int id = c.getStat(DataLayout.SPELL_ID);
            listModel.addElement(String.format("[%3d] %s", id > 0 ? id : i + 1, c.getName()));
        }
    }

    private void addSpell() {
        byte[] block = new byte[48];
        DataCore core = new DataCore(block);
        core.setName("New Spell");
        int nextId = 1;
        EditorState state = EditorState.get();
        for (int i = 0; i < state.getSpellCount(); i++) {
            byte[] b = state.getSpell(i);
            if (b != null) {
                int sid = new DataCore(b).getStat(DataLayout.SPELL_ID);
                if (sid >= nextId) nextId = sid + 1;
            }
        }
        core.setStat(DataLayout.SPELL_ID, Math.min(nextId, 255));
        state.addSpell(block);
        state.markDirty("spell:" + (state.getSpellCount() - 1));
        rebuildList();
        list.setSelectedIndex(listModel.getSize() - 1);
    }

    private void deleteSpell() {
        int idx = list.getSelectedIndex();
        if (idx < 0) return;
        if (!EditorUtils.confirm(this, "Delete this spell?", "Confirm")) return;
        EditorState.get().removeSpell(idx);
        EditorState.get().markDirty("spell:deleted");
        lastIndex = -1;
        rebuildList();
        if (listModel.getSize() > 0) list.setSelectedIndex(Math.min(idx, listModel.getSize() - 1));
    }

    private void loadToForm(int idx) {
        if (idx < 0) return;
        byte[] block = EditorState.get().getSpell(idx);
        if (block == null) return;
        loading = true;
        DataCore c = new DataCore(block);
        idField   .setText(String.valueOf(c.getStat(DataLayout.SPELL_ID)));
        nameField .setText(c.getName());
        costField .setText(String.valueOf(c.getStat(DataLayout.SPELL_MP_COST)));
        powerField.setText(String.valueOf(c.getStat(DataLayout.SPELL_POWER)));
        levelField.setText(String.valueOf(c.getStat(DataLayout.SPELL_LEVEL_REQ)));
        targetBox    .setSelectedIndex(clamp(c.getStat(DataLayout.SPELL_TARGET),     3));
        spellTypeBox .setSelectedIndex(clamp(c.getStat(DataLayout.SPELL_TYPE),        1));
        effectTypeBox.setSelectedIndex(clamp(c.getStat(DataLayout.SPELL_EFFECT_TYPE), 6));
        loading = false;
    }

    private void commitToBlock(int idx) {
        if (loading || idx < 0) return;
        byte[] block = EditorState.get().getSpell(idx);
        if (block == null) return;
        DataCore c = new DataCore(block);
        c.setStat(DataLayout.SPELL_ID,          Math.min(Math.max(EditorUtils.parseInt(idField, idx+1),1),255));
        c.setName(nameField.getText());
        c.setStat(DataLayout.SPELL_MP_COST,     EditorUtils.parseInt(costField,  0));
        c.setStat(DataLayout.SPELL_POWER,       EditorUtils.parseInt(powerField, 0));
        c.setStat(DataLayout.SPELL_LEVEL_REQ,   EditorUtils.parseInt(levelField, 0));
        c.setStat(DataLayout.SPELL_TARGET,      targetBox.getSelectedIndex());
        c.setStat(DataLayout.SPELL_TYPE,        spellTypeBox.getSelectedIndex());
        c.setStat(DataLayout.SPELL_EFFECT_TYPE, effectTypeBox.getSelectedIndex());
        EditorState.get().markDirty("spell:" + idx);
        DataCore updated = new DataCore(block);
        int id = updated.getStat(DataLayout.SPELL_ID);
        listModel.set(idx, String.format("[%3d] %s", id > 0 ? id : idx+1, updated.getName()));
    }

    private static int clamp(int v, int max) { return Math.max(0, Math.min(v, max)); }

    /** Called by EditorUtils.isEmptyBlock — already exists in EditorUtils. */
}