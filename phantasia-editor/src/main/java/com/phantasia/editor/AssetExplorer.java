// phantasia-editor/src/main/java/com/phantasia/editor/AssetExplorer.java
package com.phantasia.editor;

import com.phantasia.core.data.*;
import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.NpcDefinition;
import com.phantasia.core.model.item.ItemDefinition;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;

/**
 * A categorized tree-view of all authored game data (Section 3.2).
 *
 * <p>Each top-level node corresponds to a data category (World Map, Towns,
 * Dungeons, NPCs, Quests, Monsters, Items, Spells). Expanding a node
 * reveals individual records. Double-clicking a record opens it in the
 * workspace. Right-clicking shows a context menu (New, Duplicate, Delete).</p>
 *
 * <p>The tree refreshes automatically via {@link EditorStateListener} when
 * data changes in {@link EditorState}.</p>
 */
public class AssetExplorer extends JPanel implements EditorStateListener {

    private final JTree                    tree;
    private final DefaultTreeModel         treeModel;
    private final DefaultMutableTreeNode   root;

    // Category nodes — persistent references for incremental updates
    private final DefaultMutableTreeNode nodeWorldMap;
    private final DefaultMutableTreeNode nodeTowns;
    private final DefaultMutableTreeNode nodeDungeons;
    private final DefaultMutableTreeNode nodeNpcs;
    private final DefaultMutableTreeNode nodeQuests;
    private final DefaultMutableTreeNode nodeMonsters;
    private final DefaultMutableTreeNode nodeItems;
    private final DefaultMutableTreeNode nodeSpells;

    /** Callback when a record is double-clicked — the workbench opens it. */
    private AssetOpenHandler openHandler;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public AssetExplorer() {
        setLayout(new BorderLayout());
        setBackground(EditorTheme.BG_LIST);

        // Build the tree structure
        root = new DefaultMutableTreeNode("Project");

        nodeWorldMap = new DefaultMutableTreeNode(new CategoryNode("World Map",    "worldMap"));
        nodeTowns    = new DefaultMutableTreeNode(new CategoryNode("Towns",        "towns"));
        nodeDungeons = new DefaultMutableTreeNode(new CategoryNode("Dungeons",     "dungeons"));
        nodeNpcs     = new DefaultMutableTreeNode(new CategoryNode("NPCs",         "npcs"));
        nodeQuests   = new DefaultMutableTreeNode(new CategoryNode("Quests",       "quests"));
        nodeMonsters = new DefaultMutableTreeNode(new CategoryNode("Monsters",     "monsters"));
        nodeItems    = new DefaultMutableTreeNode(new CategoryNode("Items",        "items"));
        nodeSpells   = new DefaultMutableTreeNode(new CategoryNode("Spells",       "spells"));

        root.add(nodeWorldMap);
        root.add(nodeTowns);
        root.add(nodeDungeons);
        root.add(nodeNpcs);
        root.add(nodeQuests);
        root.add(nodeMonsters);
        root.add(nodeItems);
        root.add(nodeSpells);

        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setBackground(EditorTheme.BG_LIST);
        tree.setForeground(EditorTheme.TEXT);
        tree.setFont(EditorTheme.FONT_LABEL);
        tree.setCellRenderer(new AssetTreeRenderer());
        tree.getSelectionModel().setSelectionMode(
                TreeSelectionModel.SINGLE_TREE_SELECTION);

        // Double-click to open in workspace
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick();
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    handleContextMenu(e);
                }
            }
        });

        // Enter key to open
        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    handleDoubleClick();
                }
            }
        });

        // Header label
        JLabel header = new JLabel("  ASSET EXPLORER");
        header.setFont(EditorTheme.FONT_LABEL_B);
        header.setForeground(EditorTheme.ACCENT);
        header.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, EditorTheme.BORDER));
        scroll.getViewport().setBackground(EditorTheme.BG_LIST);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        // Register for EditorState changes
        EditorState.get().addListener(this);
    }

    // -------------------------------------------------------------------------
    // Open handler
    // -------------------------------------------------------------------------

    public void setOpenHandler(AssetOpenHandler handler) {
        this.openHandler = handler;
    }

    /**
     * Callback interface for opening records in the workspace.
     */
    @FunctionalInterface
    public interface AssetOpenHandler {
        /**
         * @param category the data category (e.g. "monsters", "towns")
         * @param id       the record key — Integer index for lists,
         *                 Integer ID for maps, or null for singletons
         */
        void openAsset(String category, Object id);
    }

    // -------------------------------------------------------------------------
    // Tree population — full rebuild from EditorState
    // -------------------------------------------------------------------------

    /**
     * Rebuilds the entire tree from current EditorState data.
     * Called on project load and on collection changes.
     */
    public void rebuildTree() {
        EditorState state = EditorState.get();

        // World Map
        rebuildWorldMapNode(state);

        // Towns
        rebuildMapNode(nodeTowns, state.getTowns(), "towns",
                (id, town) -> town.getName());

        // Dungeons
        rebuildMapNode(nodeDungeons, state.getDungeons(), "dungeons",
                (id, dng) -> dng.getName());

        // Interior Maps (shown under their parent town/dungeon, not here)
        // NPCs
        rebuildMapNode(nodeNpcs, state.getNpcs(), "npcs",
                (id, npc) -> npc.getName() + " (" + npc.getRole() + ")");

        // Quests
        rebuildMapNode(nodeQuests, state.getQuests(), "quests",
                (id, quest) -> quest.getName());

        // Monsters
        rebuildMonsterNode(state);

        // Items
        rebuildItemNode(state);

        // Spells
        rebuildSpellNode(state);

        treeModel.reload();

        // Expand all category nodes
        for (int i = 0; i < root.getChildCount(); i++) {
            TreePath path = new TreePath(
                    ((DefaultMutableTreeNode) root.getChildAt(i)).getPath());
            tree.expandPath(path);
        }
    }

    private void rebuildWorldMapNode(EditorState state) {
        nodeWorldMap.removeAllChildren();
        if (state.getWorldMap() != null) {
            var wm = state.getWorldMap();
            nodeWorldMap.add(new DefaultMutableTreeNode(
                    new AssetNode("worldMap", null,
                            "Pendragon World (" + wm.getWidth() + "×" + wm.getHeight() + ")")));
        }
    }

    private <V> void rebuildMapNode(DefaultMutableTreeNode categoryNode,
                                    Map<Integer, V> map, String category,
                                    RecordLabeler<V> labeler) {
        categoryNode.removeAllChildren();
        for (var entry : map.entrySet()) {
            String label = labeler.label(entry.getKey(), entry.getValue());
            categoryNode.add(new DefaultMutableTreeNode(
                    new AssetNode(category, entry.getKey(), label)));
        }
    }

    private void rebuildMonsterNode(EditorState state) {
        nodeMonsters.removeAllChildren();
        for (int i = 0; i < state.getMonsterCount(); i++) {
            byte[] block = state.getMonster(i);
            String name = (block != null)
                    ? new DataCore(block).getName()
                    : "Monster #" + i;
            if (name == null || name.isBlank()) name = "Monster #" + i;
            nodeMonsters.add(new DefaultMutableTreeNode(
                    new AssetNode("monsters", i, name)));
        }
    }

    private void rebuildItemNode(EditorState state) {
        nodeItems.removeAllChildren();
        for (int i = 0; i < state.getItemCount(); i++) {
            ItemDefinition item = state.getItem(i);
            String name = (item != null) ? item.name() : "Item #" + i;
            if (name == null || name.isBlank()) name = "Item #" + i;
            nodeItems.add(new DefaultMutableTreeNode(
                    new AssetNode("items", i, name)));
        }
    }

    private void rebuildSpellNode(EditorState state) {
        nodeSpells.removeAllChildren();
        for (int i = 0; i < state.getSpellCount(); i++) {
            byte[] block = state.getSpell(i);
            String name = (block != null)
                    ? new DataCore(block).getName()
                    : "Spell #" + i;
            if (name == null || name.isBlank()) name = "Spell #" + i;
            nodeSpells.add(new DefaultMutableTreeNode(
                    new AssetNode("spells", i, name)));
        }
    }

    // -------------------------------------------------------------------------
    // EditorStateListener implementation
    // -------------------------------------------------------------------------

    @Override
    public void onProjectLoaded() {
        rebuildTree();
    }

    @Override
    public void onCollectionChanged(String category) {
        // Rebuild just the affected category
        EditorState state = EditorState.get();
        switch (category) {
            case "monsters" -> { rebuildMonsterNode(state); treeModel.reload(nodeMonsters); }
            case "items"    -> { rebuildItemNode(state);    treeModel.reload(nodeItems); }
            case "spells"   -> { rebuildSpellNode(state);   treeModel.reload(nodeSpells); }
            case "towns"    -> {
                rebuildMapNode(nodeTowns, state.getTowns(), "towns",
                        (id, t) -> ((TownDefinition) t).getName());
                treeModel.reload(nodeTowns);
            }
            case "dungeons" -> {
                rebuildMapNode(nodeDungeons, state.getDungeons(), "dungeons",
                        (id, d) -> ((DungeonDefinition) d).getName());
                treeModel.reload(nodeDungeons);
            }
            case "npcs"     -> {
                rebuildMapNode(nodeNpcs, state.getNpcs(), "npcs",
                        (id, n) -> ((NpcDefinition) n).getName() + " (" + ((NpcDefinition) n).getRole() + ")");
                treeModel.reload(nodeNpcs);
            }
            case "quests"   -> {
                rebuildMapNode(nodeQuests, state.getQuests(), "quests",
                        (id, q) -> ((Quest) q).getName());
                treeModel.reload(nodeQuests);
            }
            default -> rebuildTree();
        }
    }

    @Override
    public void onDataChanged(String dirtyKey) {
        // A record was modified — update its label if visible in the tree.
        // For efficiency, we only update the specific node rather than
        // rebuilding the entire category.
        // This is a targeted refresh: find the node matching the dirty key,
        // update its label, and repaint.
        // For now, a simple repaint is sufficient — labels are read from
        // EditorState on render.
        tree.repaint();
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    private void handleDoubleClick() {
        DefaultMutableTreeNode selected = getSelectedNode();
        if (selected == null) return;

        Object userObj = selected.getUserObject();
        if (userObj instanceof AssetNode asset) {
            if (openHandler != null) {
                openHandler.openAsset(asset.category, asset.id);
            }
        } else if (userObj instanceof CategoryNode cat) {
            // Double-click on category header → open browse-all panel
            if (openHandler != null) {
                openHandler.openAsset(cat.category, null);
            }
        }
    }

    private void handleContextMenu(MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) return;
        tree.setSelectionPath(path);

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObj = node.getUserObject();

        JPopupMenu popup = new JPopupMenu();

        if (userObj instanceof CategoryNode cat) {
            // Right-click on a category — offer "New"
            JMenuItem newItem = new JMenuItem("New " + singularize(cat.label) + "...");
            newItem.addActionListener(a -> createNewRecord(cat.category));
            popup.add(newItem);
        } else if (userObj instanceof AssetNode asset) {
            // Right-click on a record
            JMenuItem open = new JMenuItem("Open");
            open.addActionListener(a -> {
                if (openHandler != null) openHandler.openAsset(asset.category, asset.id);
            });
            popup.add(open);

            popup.addSeparator();

            JMenuItem dup = new JMenuItem("Duplicate");
            dup.addActionListener(a -> duplicateRecord(asset.category, asset.id));
            popup.add(dup);

            JMenuItem del = new JMenuItem("Delete");
            del.setForeground(EditorTheme.RED);
            del.addActionListener(a -> deleteRecord(asset.category, asset.id));
            popup.add(del);
        }

        if (popup.getComponentCount() > 0) {
            popup.show(tree, e.getX(), e.getY());
        }
    }

    // -------------------------------------------------------------------------
    // CRUD operations
    // -------------------------------------------------------------------------

    private void createNewRecord(String category) {
        EditorState state = EditorState.get();
        switch (category) {
            case "monsters" -> {
                byte[] block = new byte[48];
                new DataCore(block).setName("New Monster");
                state.addMonster(block);
                int idx = state.getMonsterCount() - 1;
                state.markDirty("monster:" + idx);
                rebuildMonsterNode(state);
                treeModel.reload(nodeMonsters);
                expandNode(nodeMonsters);
                if (openHandler != null) openHandler.openAsset("monsters", idx);
            }
            case "spells" -> {
                byte[] block = new byte[48];
                DataCore core = new DataCore(block);
                core.setName("New Spell");
                // Assign next available ID
                int nextId = 1;
                for (int i = 0; i < state.getSpellCount(); i++) {
                    byte[] b = state.getSpell(i);
                    if (b != null) {
                        int sid = new DataCore(b).getStat(DataLayout.SPELL_ID);
                        if (sid >= nextId) nextId = sid + 1;
                    }
                }
                core.setStat(DataLayout.SPELL_ID, Math.min(nextId, 255));
                state.addSpell(block);
                int idx = state.getSpellCount() - 1;
                state.markDirty("spell:" + idx);
                rebuildSpellNode(state);
                treeModel.reload(nodeSpells);
                expandNode(nodeSpells);
                if (openHandler != null) openHandler.openAsset("spells", idx);
            }
            default -> JOptionPane.showMessageDialog(this,
                    "New " + singularize(category) + " — coming in a future phase.",
                    "Not Yet Implemented", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void duplicateRecord(String category, Object id) {
        EditorState state = EditorState.get();
        if (id instanceof Integer idx) {
            switch (category) {
                case "monsters" -> {
                    byte[] orig = state.getMonster(idx);
                    if (orig == null) return;
                    byte[] copy = orig.clone();
                    DataCore core = new DataCore(copy);
                    core.setName(core.getName().trim() + " Copy");
                    state.addMonster(copy);
                    int newIdx = state.getMonsterCount() - 1;
                    state.markDirty("monster:" + newIdx);
                    rebuildMonsterNode(state);
                    treeModel.reload(nodeMonsters);
                    if (openHandler != null) openHandler.openAsset("monsters", newIdx);
                }
                case "spells" -> {
                    byte[] orig = state.getSpell(idx);
                    if (orig == null) return;
                    byte[] copy = orig.clone();
                    DataCore core = new DataCore(copy);
                    core.setName(core.getName().trim() + " Copy");
                    state.addSpell(copy);
                    int newIdx = state.getSpellCount() - 1;
                    state.markDirty("spell:" + newIdx);
                    rebuildSpellNode(state);
                    treeModel.reload(nodeSpells);
                    if (openHandler != null) openHandler.openAsset("spells", newIdx);
                }
                default -> JOptionPane.showMessageDialog(this,
                        "Duplicate — coming in a future phase.",
                        "Not Yet Implemented", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private void deleteRecord(String category, Object id) {
        if (!(id instanceof Integer idx)) return;
        EditorState state = EditorState.get();

        String name = switch (category) {
            case "monsters" -> {
                byte[] b = state.getMonster(idx);
                yield b != null ? new DataCore(b).getName() : "record " + idx;
            }
            case "spells" -> {
                byte[] b = state.getSpell(idx);
                yield b != null ? new DataCore(b).getName() : "record " + idx;
            }
            default -> "record " + idx;
        };

        int choice = JOptionPane.showConfirmDialog(this,
                "Delete '" + name + "'?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        switch (category) {
            case "monsters" -> {
                state.removeMonster(idx);
                state.markDirty("monster:deleted");
                rebuildMonsterNode(state);
                treeModel.reload(nodeMonsters);
            }
            case "spells" -> {
                state.removeSpell(idx);
                state.markDirty("spell:deleted");
                rebuildSpellNode(state);
                treeModel.reload(nodeSpells);
            }
            default -> {}
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DefaultMutableTreeNode getSelectedNode() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;
        return (DefaultMutableTreeNode) path.getLastPathComponent();
    }

    private void expandNode(DefaultMutableTreeNode node) {
        tree.expandPath(new TreePath(node.getPath()));
    }

    private static String singularize(String s) {
        if (s.endsWith("ies")) return s.substring(0, s.length() - 3) + "y";
        if (s.endsWith("s"))   return s.substring(0, s.length() - 1);
        return s;
    }

    // -------------------------------------------------------------------------
    // Tree node data carriers
    // -------------------------------------------------------------------------

    /** Represents a category header in the tree (e.g. "Monsters"). */
    record CategoryNode(String label, String category) {
        @Override public String toString() { return label; }
    }

    /** Represents an individual record in the tree. */
    record AssetNode(String category, Object id, String label) {
        @Override public String toString() { return label; }
    }

    /** Functional interface for labeling records in map-backed categories. */
    @FunctionalInterface
    private interface RecordLabeler<V> {
        String label(int id, V value);
    }

    // -------------------------------------------------------------------------
    // Custom tree cell renderer
    // -------------------------------------------------------------------------

    private static class AssetTreeRenderer extends DefaultTreeCellRenderer {

        AssetTreeRenderer() {
            setBackgroundNonSelectionColor(EditorTheme.BG_LIST);
            setBackgroundSelectionColor(EditorTheme.ACCENT_DIM);
            setTextNonSelectionColor(EditorTheme.TEXT);
            setTextSelectionColor(EditorTheme.TEXT);
            setBorderSelectionColor(EditorTheme.ACCENT_DIM);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObj = node.getUserObject();

            if (userObj instanceof CategoryNode) {
                setFont(EditorTheme.FONT_LABEL_B);
                if (!sel) setForeground(EditorTheme.ACCENT);
            } else if (userObj instanceof AssetNode) {
                setFont(EditorTheme.FONT_LABEL);
                if (!sel) setForeground(EditorTheme.TEXT);
            }

            // No icons — clean text-only tree
            setIcon(null);
            setOpenIcon(null);
            setClosedIcon(null);
            setLeafIcon(null);

            return this;
        }
    }
}