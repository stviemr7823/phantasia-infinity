// phantasia-core/src/main/java/com/phantasia/core/tools/MonsterEditorPanel.java
package com.phantasia.core.tools;

import com.phantasia.core.data.DataPaths;
import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class MonsterEditorPanel extends JPanel {

    private final List<byte[]>  blocks    = new ArrayList<>();
    private final JList<String> list      = EditorUtils.styledList();
    private final JTextField    nameField = EditorUtils.field(16);
    private final JTextField    hpField   = EditorUtils.field(5);
    private final JTextField    maxField  = EditorUtils.field(5);
    private final JTextField    xpField   = EditorUtils.field(8);
    private final JTextField    tresField = EditorUtils.field(8);
    private final JTextField    itm1Field = EditorUtils.field(5);
    private final JTextField    itm2Field = EditorUtils.field(5);
    private final JCheckBox     undeadBox = EditorUtils.checkbox("Undead");
    private final JCheckBox     amorphBox = EditorUtils.checkbox("Amorphous");

    private File loadedFile = null;  // tracks the file we loaded, for direct Save
    private int  lastIndex  = -1;

    public MonsterEditorPanel() {
        setLayout(new BorderLayout(8, 8));
        setBackground(PhantasiaEditor.BG_DARK);
        setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel leftPanel = EditorUtils.titledPanel("Bestiary", new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(220, 0));
        leftPanel.add(EditorUtils.scrollPane(list), BorderLayout.CENTER);

        JPanel form = EditorUtils.titledPanel("Monster Properties",
                new GridLayout(0, 1, 4, 4));
        EditorUtils.addFormRow(form, "Name",      nameField);
        EditorUtils.addFormRow(form, "HP",        hpField);
        EditorUtils.addFormRow(form, "Max Spawn", maxField);
        EditorUtils.addFormRow(form, "XP Reward", xpField);
        EditorUtils.addFormRow(form, "Treasure",  tresField);
        EditorUtils.addFormRow(form, "Item 1 ID", itm1Field);
        EditorUtils.addFormRow(form, "Item 2 ID", itm2Field);
        form.add(undeadBox);
        form.add(amorphBox);

        JPanel rightPanel = new JPanel(new BorderLayout(0, 8));
        rightPanel.setBackground(PhantasiaEditor.BG_DARK);
        rightPanel.add(form, BorderLayout.NORTH);

        JButton loadBtn   = EditorUtils.button("Load .dat");
        JButton saveBtn   = EditorUtils.accentButton("Save");
        JButton saveAsBtn = EditorUtils.button("Save As...");
        JButton addBtn    = EditorUtils.button("Add");
        JButton deleteBtn = EditorUtils.button("Delete");

        loadBtn  .addActionListener(e -> loadFile());
        saveBtn  .addActionListener(e -> saveFile(false));
        saveAsBtn.addActionListener(e -> saveFile(true));
        addBtn   .addActionListener(e -> addMonster());
        deleteBtn.addActionListener(e -> deleteMonster());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setBackground(PhantasiaEditor.BG_DARK);
        buttons.add(loadBtn);
        buttons.add(addBtn);
        buttons.add(deleteBtn);
        buttons.add(saveBtn);
        buttons.add(saveAsBtn);

        add(leftPanel,  BorderLayout.WEST);
        add(rightPanel, BorderLayout.CENTER);
        add(buttons,    BorderLayout.SOUTH);

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                if (lastIndex >= 0 && lastIndex < blocks.size())
                    saveToBlock(lastIndex);
                loadToForm(list.getSelectedIndex());
                lastIndex = list.getSelectedIndex();
            }
        });

        // Auto-load on startup
        EditorUtils.autoLoad(DataPaths.MONSTERS_DAT, blocks,
                (DefaultListModel<String>) list.getModel(),
                list, this::blockLabel);
        if (!blocks.isEmpty()) loadedFile = new File(DataPaths.MONSTERS_DAT);
    }

    // ------------------------------------------------------------------
    // File I/O
    // ------------------------------------------------------------------

    private void loadFile() {
        File f = EditorUtils.chooseDatFile(this, "Load monsters.dat");
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

    private void saveFile(boolean forceChooser) {
        if (list.getSelectedIndex() >= 0)
            saveToBlock(list.getSelectedIndex());

        File target = loadedFile;
        if (forceChooser || target == null) {
            target = EditorUtils.chooseSaveFile(this, "Save monsters.dat", "dat");
            if (target == null) return;
            loadedFile = target;
        }

        try (RandomAccessFile raf = new RandomAccessFile(target, "rw")) {
            raf.setLength(0);
            for (byte[] b : blocks) raf.write(b);
            EditorUtils.confirm(this, "Saved " + blocks.size()
                    + " monsters to " + target.getName());
        } catch (IOException ex) {
            EditorUtils.error(this, "Save failed: " + ex.getMessage());
        }
    }

    private void addMonster() {
        byte[] block = new byte[48];
        new DataCore(block).setName("New Monster");
        blocks.add(block);
        ((DefaultListModel<String>) list.getModel()).addElement(blockLabel(block));
        list.setSelectedIndex(blocks.size() - 1);
    }

    private void deleteMonster() {
        int idx = list.getSelectedIndex();
        if (idx < 0) return;
        if (!EditorUtils.confirm(this, "Delete this monster?", "Confirm")) return;
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
        nameField.setText(core.getName());
        hpField  .setText(String.valueOf(core.getStat(DataLayout.MON_HP)));
        maxField .setText(String.valueOf(core.getStat(DataLayout.MON_MAX_SPAWN)));
        xpField  .setText(String.valueOf(core.getShort(DataLayout.MON_XP)));
        tresField.setText(String.valueOf(core.getShort(DataLayout.MON_TREASURE)));
        itm1Field.setText(String.valueOf(core.getStat(DataLayout.MON_ITEM_1)));
        itm2Field.setText(String.valueOf(core.getStat(DataLayout.MON_ITEM_2)));
        int flags = core.getStat(DataLayout.MON_FLAGS);
        undeadBox.setSelected((flags & DataLayout.MON_FLAG_UNDEAD)    != 0);
        amorphBox.setSelected((flags & DataLayout.MON_FLAG_AMORPHOUS) != 0);
    }

    /**
     * Writes form values back into the raw byte array via EditorUtils.writeBlock.
     * The DataCore operates on the original byte[] reference directly — edits
     * are no longer silently discarded as they were in the previous version.
     */
    private void saveToBlock(int idx) {
        if (idx < 0 || idx >= blocks.size()) return;
        EditorUtils.writeBlock(blocks.get(idx), core -> {
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
        });
        // Refresh list label in case the name changed
        ((DefaultListModel<String>) list.getModel())
                .set(idx, blockLabel(blocks.get(idx)));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Display label: "[NNN] Name" using sequential 1-based position. */
    private String blockLabel(byte[] block) {
        int pos = blocks.indexOf(block) + 1;
        return String.format("[%3d] %s", pos, new DataCore(block).getName());
    }
}