// phantasia-editor/src/main/java/com/phantasia/editor/panels/MonsterEditorPanel.java
package com.phantasia.editor.panels;

import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.editor.*;
import com.phantasia.editor.commands.SetFieldCommand;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Monster record editor — migrated from core.tools to the editor package.
 * Reads from / writes to {@link EditorState} via {@link EditCommand}s.
 */
public class MonsterEditorPanel extends JPanel implements EditorFrame.WorkspaceTab {

    private final String tabKey;
    private final int monsterIndex;

    private final JTextField nameField = EditorUtils.field(16);
    private final JTextField hpField = EditorUtils.field(5);
    private final JTextField maxField = EditorUtils.field(5);
    private final JTextField xpField = EditorUtils.field(8);
    private final JTextField tresField = EditorUtils.field(8);
    private final JTextField itm1Field = EditorUtils.field(5);
    private final JTextField itm2Field = EditorUtils.field(5);
    private final JCheckBox undeadBox = EditorUtils.checkbox("Undead");
    private final JCheckBox amorphBox = EditorUtils.checkbox("Amorphous");

    private boolean loading = false; // suppress commit during loadToForm

    public MonsterEditorPanel(int monsterIndex) {
        this.monsterIndex = monsterIndex;
        this.tabKey = "monsters:" + monsterIndex;

        setLayout(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        // ── Header ──
        DataCore core = getCore();
        String title = core != null ? core.getName() : "Monster #" + monsterIndex;
        JLabel header = EditorUtils.headerLabel("Monster: " + title);
        header.setBorder(new EmptyBorder(0, 0, 8, 0));

        // ── Form ──
        JPanel form = new JPanel(new GridLayout(0, 1, 4, 2));
        form.setOpaque(false);
        EditorUtils.addFormRow(form, "Name", nameField);
        EditorUtils.addFormRow(form, "HP", hpField);
        EditorUtils.addFormRow(form, "Max Spawn", maxField);
        EditorUtils.addFormRow(form, "XP Reward", xpField);
        EditorUtils.addFormRow(form, "Treasure", tresField);
        EditorUtils.addFormRow(form, "Item 1 ID", itm1Field);
        EditorUtils.addFormRow(form, "Item 2 ID", itm2Field);

        JPanel flags = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        flags.setOpaque(false);
        flags.add(undeadBox);
        flags.add(amorphBox);

        JPanel formWrapper = new JPanel(new BorderLayout());
        formWrapper.setOpaque(false);
        formWrapper.add(form, BorderLayout.NORTH);
        formWrapper.add(flags, BorderLayout.CENTER);

        // ── Limit form width ──
        JPanel centered = new JPanel(new BorderLayout());
        centered.setOpaque(false);
        centered.setPreferredSize(new Dimension(420, 0));
        centered.add(header, BorderLayout.NORTH);
        centered.add(formWrapper, BorderLayout.CENTER);

        // ── Buttons ──
        JButton saveBtn = EditorUtils.accentButton("Apply Changes");
        saveBtn.addActionListener(e -> commitToEditorState());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(saveBtn);
        centered.add(buttons, BorderLayout.SOUTH);

        add(centered, BorderLayout.WEST);

        // ── Load initial data ──
        loadToForm();

        // ── Auto-commit on focus lost ──
        var focusCommit = new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (!loading)
                    commitToEditorState();
            }
        };
        nameField.addFocusListener(focusCommit);
        hpField.addFocusListener(focusCommit);
        maxField.addFocusListener(focusCommit);
        xpField.addFocusListener(focusCommit);
        tresField.addFocusListener(focusCommit);
        itm1Field.addFocusListener(focusCommit);
        itm2Field.addFocusListener(focusCommit);
    }

    @Override
    public String getTabKey() {
        return tabKey;
    }

    // ── Data binding ─────────────────────────────────────────────────────

    private DataCore getCore() {
        byte[] block = EditorState.get().getMonster(monsterIndex);
        return block != null ? new DataCore(block) : null;
    }

    private void loadToForm() {
        DataCore core = getCore();
        if (core == null)
            return;
        loading = true;
        nameField.setText(core.getName());
        hpField.setText(String.valueOf(core.getStat(DataLayout.MON_HP)));
        maxField.setText(String.valueOf(core.getStat(DataLayout.MON_MAX_SPAWN)));
        xpField.setText(String.valueOf(core.getShort(DataLayout.MON_XP)));
        tresField.setText(String.valueOf(core.getShort(DataLayout.MON_TREASURE)));
        itm1Field.setText(String.valueOf(core.getStat(DataLayout.MON_ITEM_1)));
        itm2Field.setText(String.valueOf(core.getStat(DataLayout.MON_ITEM_2)));
        int flags = core.getStat(DataLayout.MON_FLAGS);
        undeadBox.setSelected((flags & DataLayout.MON_FLAG_UNDEAD) != 0);
        amorphBox.setSelected((flags & DataLayout.MON_FLAG_AMORPHOUS) != 0);
        loading = false;
    }

    private void commitToEditorState() {
        if (loading)
            return;
        byte[] block = EditorState.get().getMonster(monsterIndex);
        if (block == null)
            return;

        // Capture old state for undo
        byte[] oldBlock = block.clone();
        DataCore core = new DataCore(block);

        core.setName(nameField.getText());
        core.setStat(DataLayout.MON_HP, EditorUtils.parseInt(hpField, 1));
        core.setStat(DataLayout.MON_MAX_SPAWN, EditorUtils.parseInt(maxField, 1));
        core.setShort(DataLayout.MON_XP, EditorUtils.parseInt(xpField, 0));
        core.setShort(DataLayout.MON_TREASURE, EditorUtils.parseInt(tresField, 0));
        core.setStat(DataLayout.MON_ITEM_1, EditorUtils.parseInt(itm1Field, 0));
        core.setStat(DataLayout.MON_ITEM_2, EditorUtils.parseInt(itm2Field, 0));
        int flags = 0;
        if (undeadBox.isSelected())
            flags |= DataLayout.MON_FLAG_UNDEAD;
        if (amorphBox.isSelected())
            flags |= DataLayout.MON_FLAG_AMORPHOUS;
        core.setStat(DataLayout.MON_FLAGS, flags);

        // Check if anything actually changed
        byte[] newBlock = block.clone();
        if (java.util.Arrays.equals(oldBlock, newBlock))
            return;

        // Restore old state — the command's execute() will apply the new one
        System.arraycopy(oldBlock, 0, block, 0, 48);

        String name = new DataCore(newBlock).getName();
        EditorState.get().execute(new SetFieldCommand<>(
                "monster:" + monsterIndex,
                "Edit monster '" + name + "'",
                null,
                v -> System.arraycopy(v, 0,
                        EditorState.get().getMonster(monsterIndex), 0, 48),
                oldBlock,
                newBlock));
    }
}