// phantasia-editor/src/main/java/com/phantasia/editor/panels/QuestEditorPanel.java
package com.phantasia.editor.panels;

import com.phantasia.core.data.*;
import com.phantasia.core.model.DialogueNode;
import com.phantasia.core.model.NpcDefinition;
import com.phantasia.editor.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Quest Editor — quest properties, objective sequence, rewards,
 * and flag dependency viewer (Design Document Section 4.D).
 *
 * Layout:
 *   Left:   Quest list with Add/Delete
 *   Center: Quest properties + objective cards + reward form
 *   Right:  Flag dependency viewer (cross-references all data)
 */
public class QuestEditorPanel extends JPanel implements EditorFrame.WorkspaceTab {

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> questList = new JList<>(listModel);

    // Quest properties
    private final JTextField nameField   = EditorUtils.field(25);
    private final JTextArea  descArea    = new JTextArea(3, 30);
    private final JTextField triggerField = EditorUtils.field(20);
    private final JTextField completeField = EditorUtils.field(20);

    // Objectives
    private final DefaultListModel<String> objModel = new DefaultListModel<>();
    private final JList<String> objList = new JList<>(objModel);
    private final JTextField objDescField  = EditorUtils.field(30);
    private final JTextField objFlagField  = EditorUtils.field(15);
    private final JCheckBox  objOptional   = EditorUtils.checkbox("Optional");

    // Rewards
    private final JTextField goldField = EditorUtils.field(8);
    private final JTextField xpField   = EditorUtils.field(8);
    private final JTextField itemsField = EditorUtils.field(20);
    private final JTextField unlockField = EditorUtils.field(15);

    // Dependency viewer
    private final JTextArea depViewer = new JTextArea(10, 25);

    private int currentQuestIdx = -1;
    private int currentObjIdx = -1;
    private boolean loading = false;

    public QuestEditorPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // ── Left: quest list ─────────────────────────────────────────────
        rebuildQuestList();
        questList.setFont(EditorTheme.FONT_FIELD);

        JPanel leftPanel = EditorUtils.titledPanel("Quests", new BorderLayout(0, 4));
        leftPanel.setPreferredSize(new Dimension(200, 0));
        leftPanel.add(new JScrollPane(questList), BorderLayout.CENTER);

        JPanel qBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        qBtns.setOpaque(false);
        JButton addQ = EditorUtils.button("+ New");
        JButton delQ = EditorUtils.button("Delete");
        addQ.addActionListener(e -> addQuest());
        delQ.addActionListener(e -> deleteQuest());
        qBtns.add(addQ);
        qBtns.add(delQ);
        leftPanel.add(qBtns, BorderLayout.SOUTH);

        // ── Center: properties + objectives + rewards ────────────────────
        JPanel propsPanel = EditorUtils.titledPanel("Quest Properties", new GridLayout(0, 1, 4, 2));
        EditorUtils.addFormRow(propsPanel, "Name", nameField);
        descArea.setFont(EditorTheme.FONT_FIELD);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        propsPanel.add(EditorUtils.label("Description:"));
        propsPanel.add(new JScrollPane(descArea));
        EditorUtils.addFormRow(propsPanel, "Trigger Flag", triggerField);
        EditorUtils.addFormRow(propsPanel, "Completion Flag", completeField);

        // Objective list
        JPanel objPanel = EditorUtils.titledPanel("Objectives (ordered)", new BorderLayout(4, 4));
        objList.setFont(EditorTheme.FONT_SMALL);
        objList.setFixedCellHeight(18);
        JScrollPane objScroll = new JScrollPane(objList);
        objScroll.setPreferredSize(new Dimension(0, 100));

        JPanel objBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        objBtns.setOpaque(false);
        JButton addObj = EditorUtils.button("+ Objective");
        JButton delObj = EditorUtils.button("- Objective");
        JButton upObj  = EditorUtils.button("▲");
        JButton downObj = EditorUtils.button("▼");
        addObj.addActionListener(e -> addObjective());
        delObj.addActionListener(e -> deleteObjective());
        upObj.addActionListener(e -> moveObjective(-1));
        downObj.addActionListener(e -> moveObjective(1));
        objBtns.add(addObj);
        objBtns.add(delObj);
        objBtns.add(upObj);
        objBtns.add(downObj);
        objPanel.add(objScroll, BorderLayout.CENTER);
        objPanel.add(objBtns, BorderLayout.SOUTH);

        // Objective detail
        JPanel objDetail = new JPanel(new GridLayout(0, 1, 4, 2));
        objDetail.setOpaque(false);
        EditorUtils.addFormRow(objDetail, "Description", objDescField);
        EditorUtils.addFormRow(objDetail, "Completion Flag", objFlagField);
        objDetail.add(objOptional);

        JButton applyObj = EditorUtils.button("Apply Objective");
        applyObj.addActionListener(e -> commitCurrentObjective());
        objDetail.add(applyObj);

        // Rewards
        JPanel rewardPanel = EditorUtils.titledPanel("Rewards", new GridLayout(0, 1, 4, 2));
        EditorUtils.addFormRow(rewardPanel, "Gold", goldField);
        EditorUtils.addFormRow(rewardPanel, "XP", xpField);
        EditorUtils.addFormRow(rewardPanel, "Item IDs (comma-sep)", itemsField);
        EditorUtils.addFormRow(rewardPanel, "Unlocks Flag", unlockField);

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setOpaque(false);
        center.add(propsPanel, BorderLayout.NORTH);

        JPanel mid = new JPanel(new BorderLayout(0, 4));
        mid.setOpaque(false);
        mid.add(objPanel, BorderLayout.NORTH);
        mid.add(objDetail, BorderLayout.CENTER);
        mid.add(rewardPanel, BorderLayout.SOUTH);
        center.add(mid, BorderLayout.CENTER);

        // ── Right: dependency viewer ─────────────────────────────────────
        JPanel depPanel = EditorUtils.titledPanel("Flag Dependencies", new BorderLayout());
        depPanel.setPreferredSize(new Dimension(250, 0));
        depViewer.setFont(EditorTheme.FONT_SMALL);
        depViewer.setEditable(false);
        depViewer.setLineWrap(true);
        depViewer.setWrapStyleWord(true);
        depPanel.add(new JScrollPane(depViewer), BorderLayout.CENTER);

        JButton refreshDep = EditorUtils.button("Refresh Dependencies");
        refreshDep.addActionListener(e -> refreshDependencyViewer());
        depPanel.add(refreshDep, BorderLayout.SOUTH);

        add(leftPanel, BorderLayout.WEST);
        add(center,    BorderLayout.CENTER);
        add(depPanel,  BorderLayout.EAST);

        // ── Listeners ────────────────────────────────────────────────────
        questList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            commitCurrentQuest();
            currentQuestIdx = questList.getSelectedIndex();
            loadQuestToForm(currentQuestIdx);
        });
        objList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            currentObjIdx = objList.getSelectedIndex();
            loadObjectiveToForm(currentObjIdx);
        });

        if (listModel.getSize() > 0) questList.setSelectedIndex(0);
    }

    @Override
    public String getTabKey() { return "quests:all"; }

    // ── Quest list ───────────────────────────────────────────────────────

    private void rebuildQuestList() {
        listModel.clear();
        for (var entry : EditorState.get().getQuests().entrySet())
            listModel.addElement("[" + entry.getKey() + "] " + entry.getValue().getName());
    }

    private void addQuest() {
        EditorState state = EditorState.get();
        int id = state.nextQuestId();
        Quest q = new Quest(id, "New Quest", "Quest description.", null, null);
        state.putQuest(q);
        state.markDirty("quest:" + id);
        rebuildQuestList();
        questList.setSelectedIndex(listModel.getSize() - 1);
    }

    private void deleteQuest() {
        if (currentQuestIdx < 0) return;
        List<Integer> ids = new ArrayList<>(EditorState.get().getQuests().keySet());
        if (currentQuestIdx >= ids.size()) return;
        if (!EditorUtils.confirm(this, "Delete this quest?", "Confirm")) return;
        EditorState.get().removeQuest(ids.get(currentQuestIdx));
        currentQuestIdx = -1;
        rebuildQuestList();
    }

    // ── Quest form binding ───────────────────────────────────────────────

    private Quest getCurrentQuest() {
        List<Integer> ids = new ArrayList<>(EditorState.get().getQuests().keySet());
        if (currentQuestIdx < 0 || currentQuestIdx >= ids.size()) return null;
        return EditorState.get().getQuest(ids.get(currentQuestIdx));
    }

    private void loadQuestToForm(int idx) {
        Quest q = getCurrentQuest();
        if (q == null) return;
        loading = true;
        nameField.setText(q.getName());
        descArea.setText(q.getDescription() != null ? q.getDescription() : "");
        triggerField.setText(q.getTriggerFlag() != null ? q.getTriggerFlag().name() : "");
        completeField.setText(q.getCompletionFlag() != null ? q.getCompletionFlag().name() : "");

        // Objectives
        objModel.clear();
        currentObjIdx = -1;
        for (int i = 0; i < q.getObjectives().size(); i++) {
            QuestObjective obj = q.getObjectives().get(i);
            objModel.addElement((i + 1) + ". " + obj.description()
                    + (obj.optional() ? " [opt]" : "")
                    + " → " + obj.completionFlag());
        }

        // Rewards
        QuestRewards r = q.getRewards();
        goldField.setText(String.valueOf(r.getGold()));
        xpField.setText(String.valueOf(r.getExperience()));
        itemsField.setText(r.getItemIds().isEmpty() ? ""
                : r.getItemIds().toString().replaceAll("[\\[\\]]", ""));
        unlockField.setText(r.getUnlocksFlag() != null ? r.getUnlocksFlag().name() : "");

        clearObjForm();
        loading = false;
    }

    private void commitCurrentQuest() {
        if (loading) return;
        Quest q = getCurrentQuest();
        if (q == null) return;
        q.setName(nameField.getText().trim());
        q.setDescription(descArea.getText().trim());
        q.setTriggerFlag(parseSingleFlag(triggerField.getText()));
        q.setCompletionFlag(parseSingleFlag(completeField.getText()));

        // Rewards
        QuestRewards r = q.getRewards();
        try { r.gold(Integer.parseInt(goldField.getText().trim())); } catch (NumberFormatException ignored) {}
        try { r.experience(Integer.parseInt(xpField.getText().trim())); } catch (NumberFormatException ignored) {}
        QuestFlag unlock = parseSingleFlag(unlockField.getText());
        if (unlock != null) r.unlocksFlag(unlock);

        EditorState.get().markDirty("quest:" + q.getId());
    }

    // ── Objective management ─────────────────────────────────────────────

    private void addObjective() {
        Quest q = getCurrentQuest();
        if (q == null) return;
        q.addObjective(new QuestObjective("New objective",
                QuestFlag.values()[0], q.getObjectives().size()));
        loadQuestToForm(currentQuestIdx);
        objList.setSelectedIndex(objModel.getSize() - 1);
    }

    private void deleteObjective() {
        Quest q = getCurrentQuest();
        if (q == null || currentObjIdx < 0) return;
        // Quest.objectives is unmodifiable from getObjectives, need to rebuild
        List<QuestObjective> objs = new ArrayList<>(q.getObjectives());
        if (currentObjIdx >= objs.size()) return;
        objs.remove(currentObjIdx);
        // Rebuild quest objectives (Quest only has addObjective, no remove)
        // We need to create a new quest or use reflection... for now recreate
        Quest newQ = new Quest(q.getId(), q.getName(), q.getDescription(),
                q.getTriggerFlag(), q.getCompletionFlag());
        objs.forEach(newQ::addObjective);
        newQ.setRewards(q.getRewards());
        EditorState.get().putQuest(newQ);
        currentObjIdx = -1;
        loadQuestToForm(currentQuestIdx);
    }

    private void moveObjective(int direction) {
        Quest q = getCurrentQuest();
        if (q == null || currentObjIdx < 0) return;
        List<QuestObjective> objs = new ArrayList<>(q.getObjectives());
        int newIdx = currentObjIdx + direction;
        if (newIdx < 0 || newIdx >= objs.size()) return;
        Collections.swap(objs, currentObjIdx, newIdx);
        Quest newQ = new Quest(q.getId(), q.getName(), q.getDescription(),
                q.getTriggerFlag(), q.getCompletionFlag());
        objs.forEach(newQ::addObjective);
        newQ.setRewards(q.getRewards());
        EditorState.get().putQuest(newQ);
        loadQuestToForm(currentQuestIdx);
        objList.setSelectedIndex(newIdx);
    }

    private void loadObjectiveToForm(int idx) {
        Quest q = getCurrentQuest();
        if (q == null || idx < 0 || idx >= q.getObjectives().size()) {
            clearObjForm();
            return;
        }
        loading = true;
        QuestObjective obj = q.getObjectives().get(idx);
        objDescField.setText(obj.description());
        objFlagField.setText(obj.completionFlag() != null ? obj.completionFlag().name() : "");
        objOptional.setSelected(obj.optional());
        loading = false;
    }

    private void commitCurrentObjective() {
        if (loading) return;
        Quest q = getCurrentQuest();
        if (q == null || currentObjIdx < 0 || currentObjIdx >= q.getObjectives().size()) return;

        QuestFlag flag = parseSingleFlag(objFlagField.getText());
        if (flag == null) flag = QuestFlag.values()[0];
        QuestObjective newObj = new QuestObjective(
                objDescField.getText().trim(), flag, objOptional.isSelected(), currentObjIdx);

        List<QuestObjective> objs = new ArrayList<>(q.getObjectives());
        objs.set(currentObjIdx, newObj);
        Quest newQ = new Quest(q.getId(), q.getName(), q.getDescription(),
                q.getTriggerFlag(), q.getCompletionFlag());
        objs.forEach(newQ::addObjective);
        newQ.setRewards(q.getRewards());
        EditorState.get().putQuest(newQ);
        EditorState.get().markDirty("quest:" + q.getId());
        loadQuestToForm(currentQuestIdx);
    }

    private void clearObjForm() {
        objDescField.setText("");
        objFlagField.setText("");
        objOptional.setSelected(false);
    }

    // ── Flag dependency viewer ───────────────────────────────────────────

    private void refreshDependencyViewer() {
        Quest q = getCurrentQuest();
        if (q == null) { depViewer.setText("Select a quest."); return; }

        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(q.getName()).append(" ===\n\n");

        // Gather all flags this quest uses
        Set<QuestFlag> flags = new LinkedHashSet<>();
        if (q.getTriggerFlag() != null) flags.add(q.getTriggerFlag());
        if (q.getCompletionFlag() != null) flags.add(q.getCompletionFlag());
        for (QuestObjective obj : q.getObjectives())
            if (obj.completionFlag() != null) flags.add(obj.completionFlag());

        // Cross-reference each flag across all game data
        EditorState state = EditorState.get();
        for (QuestFlag flag : flags) {
            sb.append("► ").append(flag.name()).append("\n");

            // Check other quests
            for (var entry : state.getQuests().entrySet()) {
                Quest other = entry.getValue();
                if (other == q) continue;
                if (flag.equals(other.getTriggerFlag()))
                    sb.append("  triggers quest '").append(other.getName()).append("'\n");
                for (QuestObjective obj : other.getObjectives())
                    if (flag.equals(obj.completionFlag()))
                        sb.append("  completes objective in '").append(other.getName()).append("'\n");
            }

            // Check NPCs for dialogue flag references
            for (var entry : state.getNpcs().entrySet()) {
                NpcDefinition npc = entry.getValue();
                for (DialogueNode node : npc.getDialogue().getNodes()) {
                    if (node.requiredFlags().contains(flag))
                        sb.append("  required by ").append(npc.getName()).append("'s dialogue\n");
                    if (node.excludedFlags().contains(flag))
                        sb.append("  excluded by ").append(npc.getName()).append("'s dialogue\n");
                    if (flag.equals(node.flagToSet()))
                        sb.append("  set by ").append(npc.getName()).append("'s dialogue\n");
                }
            }
            sb.append("\n");
        }

        depViewer.setText(sb.toString());
        depViewer.setCaretPosition(0);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static QuestFlag parseSingleFlag(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return null;
        try { return QuestFlag.valueOf(trimmed); }
        catch (IllegalArgumentException e) { return null; }
    }
}
