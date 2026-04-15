// phantasia-editor/src/main/java/com/phantasia/editor/panels/SpellEditorPanel.java
package com.phantasia.editor.panels;

import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.editor.*;
import com.phantasia.editor.commands.SetFieldCommand;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Spell record editor — migrated from core.tools.
 * Reads from / writes to {@link EditorState} via {@link EditCommand}s.
 */
public class SpellEditorPanel extends JPanel implements EditorFrame.WorkspaceTab {

    private final String tabKey;
    private final int spellIndex;

    private final JTextField nameField = EditorUtils.field(16);
    private final JTextField idField = EditorUtils.field(5);
    private final JTextField costField = EditorUtils.field(5);
    private final JTextField powerField = EditorUtils.field(5);
    private final JTextField levelField = EditorUtils.field(5);

    private final JComboBox<String> targetBox = new JComboBox<>(
            new String[] { "Single enemy", "All enemies", "Single ally", "All allies" });
    private final JComboBox<String> spellTypeBox = new JComboBox<>(
            new String[] { "Wizard", "Priest" });
    private final JComboBox<String> effectTypeBox = new JComboBox<>(
            new String[] { "Damage", "Heal", "Sleep", "Awaken", "Buff", "Debuff", "Utility" });

    private boolean loading = false;

    public SpellEditorPanel(int spellIndex) {
        this.spellIndex = spellIndex;
        this.tabKey = "spells:" + spellIndex;

        setLayout(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        DataCore core = getCore();
        String title = core != null ? core.getName() : "Spell #" + spellIndex;
        JLabel header = EditorUtils.headerLabel("Spell: " + title);
        header.setBorder(new EmptyBorder(0, 0, 8, 0));

        JPanel form = new JPanel(new GridLayout(0, 1, 4, 2));
        form.setOpaque(false);
        EditorUtils.addFormRow(form, "Spell ID", idField);
        EditorUtils.addFormRow(form, "Name", nameField);
        EditorUtils.addFormRow(form, "MP Cost", costField);
        EditorUtils.addFormRow(form, "Power", powerField);
        EditorUtils.addFormRow(form, "Level Req", levelField);
        EditorUtils.addFormRow(form, "Target", targetBox);
        EditorUtils.addFormRow(form, "Spell Type", spellTypeBox);
        EditorUtils.addFormRow(form, "Effect Type", effectTypeBox);

        JPanel centered = new JPanel(new BorderLayout());
        centered.setOpaque(false);
        centered.setPreferredSize(new Dimension(420, 0));
        centered.add(header, BorderLayout.NORTH);
        centered.add(form, BorderLayout.CENTER);

        JButton saveBtn = EditorUtils.accentButton("Apply Changes");
        saveBtn.addActionListener(e -> commitToEditorState());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(saveBtn);
        centered.add(buttons, BorderLayout.SOUTH);

        add(centered, BorderLayout.WEST);

        loadToForm();

        var focusCommit = new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (!loading)
                    commitToEditorState();
            }
        };
        nameField.addFocusListener(focusCommit);
        idField.addFocusListener(focusCommit);
        costField.addFocusListener(focusCommit);
        powerField.addFocusListener(focusCommit);
        levelField.addFocusListener(focusCommit);
    }

    @Override
    public String getTabKey() {
        return tabKey;
    }

    private DataCore getCore() {
        byte[] block = EditorState.get().getSpell(spellIndex);
        return block != null ? new DataCore(block) : null;
    }

    private void loadToForm() {
        DataCore core = getCore();
        if (core == null)
            return;
        loading = true;
        idField.setText(String.valueOf(core.getStat(DataLayout.SPELL_ID)));
        nameField.setText(core.getName());
        costField.setText(String.valueOf(core.getStat(DataLayout.SPELL_MP_COST)));
        powerField.setText(String.valueOf(core.getStat(DataLayout.SPELL_POWER)));
        levelField.setText(String.valueOf(core.getStat(DataLayout.SPELL_LEVEL_REQ)));
        targetBox.setSelectedIndex(clamp(core.getStat(DataLayout.SPELL_TARGET), targetBox.getItemCount() - 1));
        spellTypeBox.setSelectedIndex(clamp(core.getStat(DataLayout.SPELL_TYPE), spellTypeBox.getItemCount() - 1));
        effectTypeBox
                .setSelectedIndex(clamp(core.getStat(DataLayout.SPELL_EFFECT_TYPE), effectTypeBox.getItemCount() - 1));
        loading = false;
    }

    private void commitToEditorState() {
        if (loading)
            return;
        byte[] block = EditorState.get().getSpell(spellIndex);
        if (block == null)
            return;

        byte[] oldBlock = block.clone();
        DataCore core = new DataCore(block);

        core.setStat(DataLayout.SPELL_ID, Math.min(Math.max(EditorUtils.parseInt(idField, spellIndex + 1), 1), 255));
        core.setName(nameField.getText());
        core.setStat(DataLayout.SPELL_MP_COST, EditorUtils.parseInt(costField, 0));
        core.setStat(DataLayout.SPELL_POWER, EditorUtils.parseInt(powerField, 0));
        core.setStat(DataLayout.SPELL_LEVEL_REQ, EditorUtils.parseInt(levelField, 0));
        core.setStat(DataLayout.SPELL_TARGET, targetBox.getSelectedIndex());
        core.setStat(DataLayout.SPELL_TYPE, spellTypeBox.getSelectedIndex());
        core.setStat(DataLayout.SPELL_EFFECT_TYPE, effectTypeBox.getSelectedIndex());

        byte[] newBlock = block.clone();
        if (java.util.Arrays.equals(oldBlock, newBlock))
            return;

        System.arraycopy(oldBlock, 0, block, 0, 48);

        String name = new DataCore(newBlock).getName();
        EditorState.get().execute(new SetFieldCommand<>(
                "spell:" + spellIndex,
                "Edit spell '" + name + "'",
                null,
                v -> System.arraycopy(v, 0,
                        EditorState.get().getSpell(spellIndex), 0, 48),
                oldBlock,
                newBlock));
    }

    private static int clamp(int val, int max) {
        return Math.max(0, Math.min(val, max));
    }
}