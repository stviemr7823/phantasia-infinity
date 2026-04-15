// phantasia-editor/src/main/java/com/phantasia/editor/panels/NpcEditorPanel.java
package com.phantasia.editor.panels;

import com.phantasia.core.data.QuestFlag;
import com.phantasia.core.model.*;
import com.phantasia.editor.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * NPC Editor — identity, role, asset references, and dialogue authoring.
 * (Design Document Section 4.C: The Dialogue Editor)
 *
 * Layout:
 *   Left:  NPC list (all NPCs) with Add/Delete
 *   Center-top: Identity form (name, role, sprite, portrait, shop link)
 *   Center-bottom: Dialogue node editor (ordered cards with flag badges)
 */
public class NpcEditorPanel extends JPanel implements EditorFrame.WorkspaceTab {

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> npcList = new JList<>(listModel);

    // Identity fields
    private final JTextField    nameField    = EditorUtils.field(20);
    private final JComboBox<NpcRole> roleBox = new JComboBox<>(NpcRole.values());
    private final JTextField    spriteField  = EditorUtils.field(20);
    private final JTextField    portraitField = EditorUtils.field(20);
    private final JTextField    shopIdField  = EditorUtils.field(5);

    // Dialogue editor
    private final DefaultListModel<String> dialogueModel = new DefaultListModel<>();
    private final JList<String> dialogueList = new JList<>(dialogueModel);
    private final JTextArea     dialogueText = new JTextArea(4, 30);
    private final JTextField    reqFlagsField = EditorUtils.field(25);
    private final JTextField    exclFlagsField = EditorUtils.field(25);
    private final JTextField    setFlagField = EditorUtils.field(15);
    private final JTextField    giveItemField = EditorUtils.field(5);

    private int currentNpcIdx = -1;
    private int currentNodeIdx = -1;
    private boolean loading = false;

    public NpcEditorPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // ── Left: NPC list ───────────────────────────────────────────────
        rebuildNpcList();
        npcList.setFont(EditorTheme.FONT_FIELD);
        npcList.setFixedCellHeight(20);

        JPanel leftPanel = EditorUtils.titledPanel("NPCs", new BorderLayout(0, 4));
        leftPanel.setPreferredSize(new Dimension(200, 0));
        leftPanel.add(new JScrollPane(npcList), BorderLayout.CENTER);

        JPanel listBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        listBtns.setOpaque(false);
        JButton addBtn = EditorUtils.button("+ New");
        JButton delBtn = EditorUtils.button("Delete");
        addBtn.addActionListener(e -> addNpc());
        delBtn.addActionListener(e -> deleteNpc());
        listBtns.add(addBtn);
        listBtns.add(delBtn);
        leftPanel.add(listBtns, BorderLayout.SOUTH);

        // ── Center: Identity + Dialogue ──────────────────────────────────
        JPanel identity = EditorUtils.titledPanel("Identity", new GridLayout(0, 1, 4, 2));
        EditorUtils.addFormRow(identity, "Name",       nameField);
        EditorUtils.addFormRow(identity, "Role",       roleBox);
        EditorUtils.addFormRow(identity, "Sprite ID",  spriteField);
        EditorUtils.addFormRow(identity, "Portrait ID", portraitField);
        EditorUtils.addFormRow(identity, "Shop ID",    shopIdField);

        // Dialogue node list
        JPanel dialoguePanel = EditorUtils.titledPanel("Dialogue Nodes (first-match order)",
                new BorderLayout(4, 4));
        dialogueList.setFont(EditorTheme.FONT_SMALL);
        dialogueList.setFixedCellHeight(18);
        JScrollPane dlgScroll = new JScrollPane(dialogueList);
        dlgScroll.setPreferredSize(new Dimension(0, 120));

        JPanel dlgBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        dlgBtns.setOpaque(false);
        JButton addNode = EditorUtils.button("+ Node");
        JButton delNode = EditorUtils.button("- Node");
        JButton moveUp  = EditorUtils.button("▲");
        JButton moveDown = EditorUtils.button("▼");
        addNode.addActionListener(e -> addDialogueNode());
        delNode.addActionListener(e -> deleteDialogueNode());
        moveUp.addActionListener(e -> moveNode(-1));
        moveDown.addActionListener(e -> moveNode(1));
        dlgBtns.add(addNode);
        dlgBtns.add(delNode);
        dlgBtns.add(moveUp);
        dlgBtns.add(moveDown);
        dialoguePanel.add(dlgScroll, BorderLayout.CENTER);
        dialoguePanel.add(dlgBtns, BorderLayout.SOUTH);

        // Node detail editor
        JPanel nodeDetail = EditorUtils.titledPanel("Node Detail", new GridLayout(0, 1, 4, 2));
        dialogueText.setFont(EditorTheme.FONT_FIELD);
        dialogueText.setLineWrap(true);
        dialogueText.setWrapStyleWord(true);
        JScrollPane textScroll = new JScrollPane(dialogueText);
        textScroll.setPreferredSize(new Dimension(0, 80));

        nodeDetail.add(EditorUtils.label("Text (use {player_name}, {gold}, {if ...}):"));
        nodeDetail.add(textScroll);
        EditorUtils.addFormRow(nodeDetail, "Required Flags", reqFlagsField);
        EditorUtils.addFormRow(nodeDetail, "Excluded Flags", exclFlagsField);
        EditorUtils.addFormRow(nodeDetail, "Sets Flag",      setFlagField);
        EditorUtils.addFormRow(nodeDetail, "Gives Item ID",  giveItemField);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(identity, BorderLayout.NORTH);

        JPanel dialogueWrapper = new JPanel(new BorderLayout(0, 4));
        dialogueWrapper.setOpaque(false);
        dialogueWrapper.add(dialoguePanel, BorderLayout.NORTH);
        dialogueWrapper.add(nodeDetail, BorderLayout.CENTER);
        center.add(dialogueWrapper, BorderLayout.CENTER);

        add(leftPanel, BorderLayout.WEST);
        add(center,    BorderLayout.CENTER);

        // ── Listeners ────────────────────────────────────────────────────
        npcList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            commitCurrentNpc();
            int sel = npcList.getSelectedIndex();
            currentNpcIdx = sel;
            loadNpcToForm(sel);
        });

        dialogueList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            commitCurrentNode();
            int sel = dialogueList.getSelectedIndex();
            currentNodeIdx = sel;
            loadNodeToForm(sel);
        });

        if (listModel.getSize() > 0) npcList.setSelectedIndex(0);
    }

    @Override
    public String getTabKey() { return "npcs:all"; }

    // ── NPC list management ──────────────────────────────────────────────

    private void rebuildNpcList() {
        listModel.clear();
        for (var entry : EditorState.get().getNpcs().entrySet()) {
            NpcDefinition npc = entry.getValue();
            listModel.addElement("[" + entry.getKey() + "] " + npc.getName()
                    + " (" + npc.getRole() + ")");
        }
    }

    private void addNpc() {
        EditorState state = EditorState.get();
        int id = state.nextNpcId();
        NpcDefinition npc = new NpcDefinition(id, "New NPC", NpcRole.INFORMANT, "npc_default");
        npc.getDialogue().addNode(new DialogueNode("Hello, traveler.",
                null, null, null));
        state.putNpc(npc);
        state.markDirty("npc:" + id);
        rebuildNpcList();
        npcList.setSelectedIndex(listModel.getSize() - 1);
    }

    private void deleteNpc() {
        if (currentNpcIdx < 0) return;
        List<Integer> ids = new ArrayList<>(EditorState.get().getNpcs().keySet());
        if (currentNpcIdx >= ids.size()) return;
        int id = ids.get(currentNpcIdx);
        if (!EditorUtils.confirm(this, "Delete this NPC?", "Confirm")) return;
        EditorState.get().removeNpc(id);
        currentNpcIdx = -1;
        rebuildNpcList();
    }

    // ── NPC form binding ─────────────────────────────────────────────────

    private NpcDefinition getCurrentNpc() {
        List<Integer> ids = new ArrayList<>(EditorState.get().getNpcs().keySet());
        if (currentNpcIdx < 0 || currentNpcIdx >= ids.size()) return null;
        return EditorState.get().getNpc(ids.get(currentNpcIdx));
    }

    private void loadNpcToForm(int idx) {
        NpcDefinition npc = getCurrentNpc();
        if (npc == null) return;
        loading = true;
        nameField.setText(npc.getName());
        roleBox.setSelectedItem(npc.getRole());
        spriteField.setText(npc.getSpriteAssetId() != null ? npc.getSpriteAssetId() : "");
        portraitField.setText(npc.getPortraitAssetId() != null ? npc.getPortraitAssetId() : "");
        shopIdField.setText(String.valueOf(npc.getShopId()));

        // Load dialogue nodes
        dialogueModel.clear();
        currentNodeIdx = -1;
        for (int i = 0; i < npc.getDialogue().size(); i++) {
            DialogueNode node = npc.getDialogue().getNodes().get(i);
            String preview = node.lines().isEmpty() ? "(empty)"
                    : node.lines().get(0);
            if (preview.length() > 40) preview = preview.substring(0, 40) + "...";
            String flags = "";
            if (!node.requiredFlags().isEmpty()) flags += " [req:" + node.requiredFlags().size() + "]";
            if (!node.excludedFlags().isEmpty()) flags += " [excl:" + node.excludedFlags().size() + "]";
            dialogueModel.addElement((i + 1) + ". " + preview + flags);
        }
        clearNodeForm();
        loading = false;
    }

    private void commitCurrentNpc() {
        if (loading) return;
        NpcDefinition npc = getCurrentNpc();
        if (npc == null) return;
        npc.name(nameField.getText().trim());
        npc.role((NpcRole) roleBox.getSelectedItem());
        npc.spriteAssetId(spriteField.getText().trim());
        npc.portraitAssetId(portraitField.getText().trim());
        try { npc.shopId(Integer.parseInt(shopIdField.getText().trim())); }
        catch (NumberFormatException e) { npc.shopId(0); }
        EditorState.get().markDirty("npc:" + npc.getId());
        // Update list label
        List<Integer> ids = new ArrayList<>(EditorState.get().getNpcs().keySet());
        if (currentNpcIdx >= 0 && currentNpcIdx < ids.size())
            listModel.set(currentNpcIdx, "[" + ids.get(currentNpcIdx) + "] "
                    + npc.getName() + " (" + npc.getRole() + ")");
    }

    // ── Dialogue node management ─────────────────────────────────────────

    private void addDialogueNode() {
        NpcDefinition npc = getCurrentNpc();
        if (npc == null) return;
        npc.getDialogue().addNode(new DialogueNode("New dialogue line.",
                null, null, null));
        loadNpcToForm(currentNpcIdx);
        dialogueList.setSelectedIndex(dialogueModel.getSize() - 1);
    }

    private void deleteDialogueNode() {
        NpcDefinition npc = getCurrentNpc();
        if (npc == null || currentNodeIdx < 0) return;
        List<DialogueNode> nodes = new ArrayList<>(npc.getDialogue().getNodes());
        if (currentNodeIdx >= nodes.size()) return;
        nodes.remove(currentNodeIdx);
        // Rebuild the dialogue script
        DialogueScript newScript = new DialogueScript();
        nodes.forEach(newScript::addNode);
        npc.dialogue(newScript);
        currentNodeIdx = -1;
        loadNpcToForm(currentNpcIdx);
    }

    private void moveNode(int direction) {
        NpcDefinition npc = getCurrentNpc();
        if (npc == null || currentNodeIdx < 0) return;
        List<DialogueNode> nodes = new ArrayList<>(npc.getDialogue().getNodes());
        int newIdx = currentNodeIdx + direction;
        if (newIdx < 0 || newIdx >= nodes.size()) return;
        Collections.swap(nodes, currentNodeIdx, newIdx);
        DialogueScript newScript = new DialogueScript();
        nodes.forEach(newScript::addNode);
        npc.dialogue(newScript);
        loadNpcToForm(currentNpcIdx);
        dialogueList.setSelectedIndex(newIdx);
    }

    private void loadNodeToForm(int idx) {
        NpcDefinition npc = getCurrentNpc();
        if (npc == null || idx < 0 || idx >= npc.getDialogue().size()) {
            clearNodeForm();
            return;
        }
        loading = true;
        DialogueNode node = npc.getDialogue().getNodes().get(idx);
        dialogueText.setText(String.join("\n", node.lines()));
        reqFlagsField.setText(flagSetToString(node.requiredFlags()));
        exclFlagsField.setText(flagSetToString(node.excludedFlags()));
        setFlagField.setText(node.flagToSet() != null ? node.flagToSet().name() : "");
        giveItemField.setText(node.giveItemId() > 0 ? String.valueOf(node.giveItemId()) : "");
        loading = false;
    }

    private void commitCurrentNode() {
        if (loading) return;
        NpcDefinition npc = getCurrentNpc();
        if (npc == null || currentNodeIdx < 0 || currentNodeIdx >= npc.getDialogue().size()) return;

        List<String> lines = List.of(dialogueText.getText().split("\n"));
        Set<QuestFlag> reqFlags = parseFlags(reqFlagsField.getText());
        Set<QuestFlag> exclFlags = parseFlags(exclFlagsField.getText());
        QuestFlag setFlag = parseSingleFlag(setFlagField.getText());
        int giveItem = 0;
        try { giveItem = Integer.parseInt(giveItemField.getText().trim()); }
        catch (NumberFormatException ignored) {}

        DialogueNode newNode = new DialogueNode(lines, reqFlags, exclFlags, setFlag, giveItem);

        // Replace the node in the script
        List<DialogueNode> nodes = new ArrayList<>(npc.getDialogue().getNodes());
        nodes.set(currentNodeIdx, newNode);
        DialogueScript newScript = new DialogueScript();
        nodes.forEach(newScript::addNode);
        npc.dialogue(newScript);
        EditorState.get().markDirty("npc:" + npc.getId());
    }

    private void clearNodeForm() {
        dialogueText.setText("");
        reqFlagsField.setText("");
        exclFlagsField.setText("");
        setFlagField.setText("");
        giveItemField.setText("");
    }

    // ── Flag helpers ─────────────────────────────────────────────────────

    private static String flagSetToString(Set<QuestFlag> flags) {
        if (flags.isEmpty()) return "";
        return flags.stream().map(QuestFlag::name)
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static Set<QuestFlag> parseFlags(String text) {
        Set<QuestFlag> flags = new LinkedHashSet<>();
        for (String part : text.split("[,;\\s]+")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try { flags.add(QuestFlag.valueOf(trimmed)); }
            catch (IllegalArgumentException ignored) {}
        }
        return flags;
    }

    private static QuestFlag parseSingleFlag(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return null;
        try { return QuestFlag.valueOf(trimmed); }
        catch (IllegalArgumentException e) { return null; }
    }
}