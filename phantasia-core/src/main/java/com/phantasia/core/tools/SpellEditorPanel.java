// phantasia-core/src/main/java/com/phantasia/core/tools/SpellEditorPanel.java
package com.phantasia.core.tools;

import com.phantasia.core.data.DataPaths;
import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.Spell;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class SpellEditorPanel extends JPanel {

    private final List<byte[]>  blocks     = new ArrayList<>();
    private final JList<String> list       = EditorUtils.styledList();
    private final JTextField    nameField  = EditorUtils.field(16);
    private final JTextField    costField  = EditorUtils.field(5);
    private final JTextField    powerField = EditorUtils.field(5);
    private final JTextField    levelField = EditorUtils.field(5);
    private final JTextField    idField    = EditorUtils.field(5);

    private final JComboBox<String> targetBox = new JComboBox<>(
            new String[]{"Single enemy", "All enemies", "Single ally", "All allies"});
    private final JComboBox<String> spellTypeBox = new JComboBox<>(
            new String[]{"Wizard", "Priest"});
    private final JComboBox<String> effectTypeBox = new JComboBox<>(
            new String[]{"Damage", "Heal", "Sleep", "Awaken", "Buff", "Debuff", "Utility"});

    private File loadedFile = null;
    private int  lastIndex  = -1;

    public SpellEditorPanel() {
        setLayout(new BorderLayout(8, 8));
        setBackground(PhantasiaEditor.BG_DARK);
        setBorder(new EmptyBorder(12, 12, 12, 12));

        styleCombo(targetBox);
        styleCombo(spellTypeBox);
        styleCombo(effectTypeBox);

        JPanel leftPanel = EditorUtils.titledPanel("Spells", new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(220, 0));
        leftPanel.add(EditorUtils.scrollPane(list), BorderLayout.CENTER);

        JPanel form = EditorUtils.titledPanel("Spell Properties",
                new GridLayout(0, 1, 4, 4));
        EditorUtils.addFormRow(form, "ID",          idField);
        EditorUtils.addFormRow(form, "Name",        nameField);
        EditorUtils.addFormRow(form, "MP Cost",     costField);
        EditorUtils.addFormRow(form, "Power",       powerField);
        EditorUtils.addFormRow(form, "Level Req",   levelField);
        EditorUtils.addFormRow(form, "Target",      targetBox);
        EditorUtils.addFormRow(form, "Spell Type",  spellTypeBox);
        EditorUtils.addFormRow(form, "Effect Type", effectTypeBox);

        JLabel idNote = EditorUtils.label("  ID determines position in spells.dat");
        form.add(idNote);

        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(PhantasiaEditor.BG_DARK);
        right.add(form, BorderLayout.NORTH);

        JButton loadBtn   = EditorUtils.button("Load .dat");
        JButton saveBtn   = EditorUtils.accentButton("Save");
        JButton saveAsBtn = EditorUtils.button("Save As...");
        JButton addBtn    = EditorUtils.button("Add");
        JButton delBtn    = EditorUtils.button("Delete");

        loadBtn  .addActionListener(e -> loadFile());
        saveBtn  .addActionListener(e -> saveFile(false));
        saveAsBtn.addActionListener(e -> saveFile(true));
        addBtn   .addActionListener(e -> addSpell());
        delBtn   .addActionListener(e -> deleteSpell());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setBackground(PhantasiaEditor.BG_DARK);
        buttons.add(loadBtn);
        buttons.add(addBtn);
        buttons.add(delBtn);
        buttons.add(saveBtn);
        buttons.add(saveAsBtn);

        add(leftPanel, BorderLayout.WEST);
        add(right,     BorderLayout.CENTER);
        add(buttons,   BorderLayout.SOUTH);

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                if (lastIndex >= 0 && lastIndex < blocks.size())
                    saveToBlock(lastIndex);
                loadToForm(list.getSelectedIndex());
                lastIndex = list.getSelectedIndex();
            }
        });

        EditorUtils.autoLoad(DataPaths.SPELLS_DAT, blocks,
                (DefaultListModel<String>) list.getModel(),
                list, this::blockLabel);
        if (!blocks.isEmpty()) loadedFile = new File(DataPaths.SPELLS_DAT);
    }

    // ------------------------------------------------------------------
    // File I/O
    // ------------------------------------------------------------------

    private void loadFile() {
        File f = EditorUtils.chooseDatFile(this, "Load spells.dat");
        if (f == null) return;
        try {
            EditorUtils.loadBlocks(f, blocks,
                    (DefaultListModel<String>) list.getModel(),
                    this::blockLabel);
            loadedFile = f;
            lastIndex  = -1;
            if (!blocks.isEmpty()) list.setSelectedIndex(0);
        } catch (IOException ex) {
            EditorUtils.error(this, "Load failed: " + ex.getMessage());
        }
    }

    /**
     * Saves the spells.dat preserving the sparse layout.
     *
     * Each spell has an explicit ID stored in its record (SPELL_ID offset).
     * The file is written sparse: spell N lives at byte offset (N-1)*48.
     * This keeps the file compatible with SpellFactory.getSpell(id).
     *
     * If no ID is stored (legacy records), sequential IDs are assigned.
     */
    private void saveFile(boolean forceChooser) {
        if (list.getSelectedIndex() >= 0)
            saveToBlock(list.getSelectedIndex());

        File target = loadedFile;
        if (forceChooser || target == null) {
            target = EditorUtils.chooseSaveFile(this, "Save spells.dat", "dat");
            if (target == null) return;
            loadedFile = target;
        }

        // Build a sparse map: id -> block
        TreeMap<Integer, byte[]> sparse = new TreeMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            byte[] block = blocks.get(i);
            int id = new DataCore(block).getStat(DataLayout.SPELL_ID);
            if (id < 1 || id > 255) id = i + 1;  // fallback: sequential
            sparse.put(id, block);
        }

        int  maxId    = sparse.lastKey();
        long fileSize = (long) maxId * DataLayout.RECORD_SIZE;

        try (RandomAccessFile raf = new RandomAccessFile(target, "rw")) {
            raf.setLength(0);
            raf.setLength(fileSize);
            for (var entry : sparse.entrySet()) {
                raf.seek((long)(entry.getKey() - 1) * DataLayout.RECORD_SIZE);
                raf.write(entry.getValue());
            }
            EditorUtils.confirm(this, "Saved " + sparse.size()
                    + " spells to " + target.getName());
        } catch (IOException ex) {
            EditorUtils.error(this, "Save failed: " + ex.getMessage());
        }
    }

    private void addSpell() {
        byte[] block = new byte[48];
        DataCore core = new DataCore(block);
        core.setName("New Spell");
        // Assign the next available ID
        int nextId = blocks.stream()
                .mapToInt(b -> new DataCore(b).getStat(DataLayout.SPELL_ID))
                .filter(id -> id > 0)
                .max().orElse(0) + 1;
        core.setStat(DataLayout.SPELL_ID, Math.min(nextId, 255));
        blocks.add(block);
        ((DefaultListModel<String>) list.getModel()).addElement(blockLabel(block));
        list.setSelectedIndex(blocks.size() - 1);
    }

    private void deleteSpell() {
        int idx = list.getSelectedIndex();
        if (idx < 0) return;
        if (!EditorUtils.confirm(this, "Delete this spell?", "Confirm")) return;
        blocks.remove(idx);
        ((DefaultListModel<String>) list.getModel()).remove(idx);
        lastIndex = -1;
    }

    // ------------------------------------------------------------------
    // Form binding
    // ------------------------------------------------------------------

    private void loadToForm(int idx) {
        if (idx < 0 || idx >= blocks.size()) return;
        DataCore core = new DataCore(blocks.get(idx));

        idField   .setText(String.valueOf(core.getStat(DataLayout.SPELL_ID)));
        nameField .setText(core.getName());
        costField .setText(String.valueOf(core.getStat(DataLayout.SPELL_MP_COST)));
        powerField.setText(String.valueOf(core.getStat(DataLayout.SPELL_POWER)));
        levelField.setText(String.valueOf(core.getStat(DataLayout.SPELL_LEVEL_REQ)));

        targetBox    .setSelectedIndex(clamp(core.getStat(DataLayout.SPELL_TARGET),
                targetBox.getItemCount() - 1));
        spellTypeBox .setSelectedIndex(clamp(core.getStat(DataLayout.SPELL_TYPE),
                spellTypeBox.getItemCount() - 1));
        effectTypeBox.setSelectedIndex(clamp(core.getStat(DataLayout.SPELL_EFFECT_TYPE),
                effectTypeBox.getItemCount() - 1));
    }

    private void saveToBlock(int idx) {
        if (idx < 0 || idx >= blocks.size()) return;
        EditorUtils.writeBlock(blocks.get(idx), core -> {
            int id = EditorUtils.parseInt(idField, idx + 1);
            core.setStat(DataLayout.SPELL_ID,          Math.min(Math.max(id, 1), 255));
            core.setName(nameField.getText());
            core.setStat(DataLayout.SPELL_MP_COST,     EditorUtils.parseInt(costField,  0));
            core.setStat(DataLayout.SPELL_POWER,       EditorUtils.parseInt(powerField, 0));
            core.setStat(DataLayout.SPELL_LEVEL_REQ,   EditorUtils.parseInt(levelField, 0));
            core.setStat(DataLayout.SPELL_TARGET,      targetBox.getSelectedIndex());
            core.setStat(DataLayout.SPELL_TYPE,        spellTypeBox.getSelectedIndex());
            core.setStat(DataLayout.SPELL_EFFECT_TYPE, effectTypeBox.getSelectedIndex());
        });
        ((DefaultListModel<String>) list.getModel())
                .set(idx, blockLabel(blocks.get(idx)));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String blockLabel(byte[] block) {
        DataCore core = new DataCore(block);
        int id = core.getStat(DataLayout.SPELL_ID);
        return String.format("[%3d] %s", id > 0 ? id : blocks.indexOf(block) + 1,
                core.getName());
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }

    private void styleCombo(JComboBox<String> combo) {
        combo.setBackground(PhantasiaEditor.BG_LIST);
        combo.setForeground(PhantasiaEditor.TEXT_MAIN);
        combo.setFont(PhantasiaEditor.FONT_FIELD);
    }
}