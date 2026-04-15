// phantasia-core/src/main/java/com/phantasia/core/tools/ItemEditorPanel.java
package com.phantasia.core.tools;

import com.phantasia.core.model.item.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ItemEditorPanel extends JPanel {

    private final List<Integer>  ids           = new ArrayList<>();
    private final JList<String>  list          = EditorUtils.styledList();
    private final JTextField     nameField     = EditorUtils.field(20);
    private final JTextField     attackField   = EditorUtils.field(5);
    private final JTextField     defenseField  = EditorUtils.field(5);
    private final JTextField     enchantField  = EditorUtils.field(5);
    private final JTextField     goldField     = EditorUtils.field(8);
    private final JTextField     rankField     = EditorUtils.field(5);
    private final JTextField     scrollIdField = EditorUtils.field(5);  // renamed from spellField
    private final JTextField     jobMaskField  = EditorUtils.field(8);
    private final JTextField     raceMaskField = EditorUtils.field(8);
    private final JCheckBox      questBox      = EditorUtils.checkbox("Quest Item");
    private final JLabel         idLabel       = EditorUtils.label("—");
    private final JLabel         categoryLabel = EditorUtils.label("—");
    private final JLabel         formulaLabel  = EditorUtils.label("");

    public ItemEditorPanel() {
        setLayout(new BorderLayout(8, 8));
        setBackground(PhantasiaEditor.BG_DARK);
        setBorder(new EmptyBorder(12, 12, 12, 12));

        // Populate list from ItemTable
        DefaultListModel<String> model =
                (DefaultListModel<String>) list.getModel();
        for (int id = 1; id <= 180; id++) {
            if (ItemTable.exists(id)) {
                ids.add(id);
                ItemDefinition def = ItemTable.get(id);
                model.addElement(String.format("[%3d] %s", id, def.name()));
            }
        }

        // Left
        JPanel leftPanel = EditorUtils.titledPanel("Items (Read-Only)", new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(220, 0));
        leftPanel.add(EditorUtils.scrollPane(list), BorderLayout.CENTER);

        JLabel note = EditorUtils.label(" Edit ItemTable.java to modify.");
        note.setBorder(new EmptyBorder(4, 4, 0, 0));
        leftPanel.add(note, BorderLayout.SOUTH);

        // Right — display form
        JPanel form = EditorUtils.titledPanel("Item Properties", new GridLayout(0, 1, 4, 4));
        EditorUtils.addFormRow(form, "ID",          idLabel);
        EditorUtils.addFormRow(form, "Name",        nameField);
        EditorUtils.addFormRow(form, "Category",    categoryLabel);
        EditorUtils.addFormRow(form, "Attack Bon",  attackField);
        EditorUtils.addFormRow(form, "Defense Bon", defenseField);
        EditorUtils.addFormRow(form, "Enchant +",   enchantField);
        EditorUtils.addFormRow(form, "Gold Value",  goldField);
        EditorUtils.addFormRow(form, "Potion Rank", rankField);
        EditorUtils.addFormRow(form, "Scroll ID",   scrollIdField);  // label updated
        EditorUtils.addFormRow(form, "Job Mask",    jobMaskField);
        EditorUtils.addFormRow(form, "Race Mask",   raceMaskField);
        EditorUtils.addFormRow(form, "Formula",     formulaLabel);
        form.add(questBox);

        // Items are defined in code — form is display only
        setFieldsEditable(false);

        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(PhantasiaEditor.BG_DARK);
        right.add(form, BorderLayout.NORTH);

        JButton bakeBtn = EditorUtils.accentButton("Bake items.dat");
        bakeBtn.addActionListener(e -> bakeItems());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setBackground(PhantasiaEditor.BG_DARK);
        buttons.add(bakeBtn);

        add(leftPanel, BorderLayout.WEST);
        add(right,     BorderLayout.CENTER);
        add(buttons,   BorderLayout.SOUTH);

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadToForm(list.getSelectedIndex());
        });

        if (!ids.isEmpty()) list.setSelectedIndex(0);
    }

    // -------------------------------------------------------------------------
    // Form population
    // -------------------------------------------------------------------------

    private void loadToForm(int idx) {
        if (idx < 0 || idx >= ids.size()) return;
        ItemDefinition def = ItemTable.get(ids.get(idx));

        idLabel      .setText(String.format("ID: %3d", def.id()));
        nameField    .setText(def.name());
        categoryLabel.setText(def.category().name());
        attackField  .setText(String.valueOf(def.attack()));
        defenseField .setText(String.valueOf(def.defense()));
        enchantField .setText(String.valueOf(def.enchant()));
        goldField    .setText(String.valueOf(def.gold()));

        rankField    .setText(def.potionRank() > 0
                ? String.valueOf(def.potionRank()) : "—");
        scrollIdField.setText(def.scrollId() > 0
                ? String.valueOf(def.scrollId()) : "—");

        questBox     .setSelected(def.isQuestItem());
        jobMaskField .setText(String.format("0x%04X", def.jobRestriction()));
        raceMaskField.setText(String.format("0x%04X", def.raceRestriction()));

        if (def.category() == ItemCategory.HEALING_POTION)
            formulaLabel.setText("Heals " + def.healingAmount() + " HP  (Rank²)");
        else if (def.category() == ItemCategory.MAGIC_POTION)
            formulaLabel.setText("Restores " + def.magicRestoreAmount() + " MP  (3×Rank)");
        else
            formulaLabel.setText("—");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setFieldsEditable(boolean editable) {
        nameField    .setEditable(editable);
        attackField  .setEditable(editable);
        defenseField .setEditable(editable);
        enchantField .setEditable(editable);
        goldField    .setEditable(editable);
        rankField    .setEditable(editable);
        scrollIdField.setEditable(editable);
        jobMaskField .setEditable(editable);
        raceMaskField.setEditable(editable);
        questBox     .setEnabled(editable);
    }

    private void bakeItems() {
        File f = EditorUtils.chooseSaveFile(this, "Bake items.dat", "dat");
        if (f == null) return;
        try {
            new ItemBaker().bake(f.getPath());
            EditorUtils.confirm(this, "Baked 180 items to " + f.getName());
        } catch (IOException ex) {
            EditorUtils.error(this, "Bake failed: " + ex.getMessage());
        }
    }
}