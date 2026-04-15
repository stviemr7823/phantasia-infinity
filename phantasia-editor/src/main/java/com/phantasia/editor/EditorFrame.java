// phantasia-editor/src/main/java/com/phantasia/editor/EditorFrame.java
package com.phantasia.editor;

import com.phantasia.core.data.DataPaths;
import com.phantasia.core.model.DataCore;
import com.phantasia.core.world.InteriorMap;
import com.phantasia.core.world.InteriorMapType;
import com.phantasia.editor.panels.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The Phantasia Editor workbench — a three-panel layout that replaces
 * the old flat tabbed pane (Section 3).
 *
 * <pre>
 * ┌────────────────────────────────────────────────────────────────────┐
 * │  MENU BAR   File │ Edit │ Data │ Maps │ Test │ Help                │
 * ├────────────┬──────────────────────────────┬────────────────────────┤
 * │            │                              │                        │
 * │  ASSET     │   EDITOR WORKSPACE           │  PROPERTIES            │
 * │  EXPLORER  │   (tabbed, multi-document)   │  & TOOLS               │
 * │            │                              │  (context-sensitive)   │
 * │            │                              │                        │
 * ├────────────┴──────────────────────────────┴────────────────────────┤
 * │  STATUS BAR                                           [dirty: 3]  │
 * └────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>The workspace center is a {@link JTabbedPane} that hosts specialized
 * editor panels for each data type. Multiple tabs can be open simultaneously.
 * The asset explorer (left) and properties panel (right) are connected
 * via {@link JSplitPane}s with persistent divider positions.</p>
 */
public class EditorFrame extends JFrame {

    private final EditorMenuBar  menuBar;
    private final AssetExplorer  assetExplorer;
    private final JTabbedPane    workspace;
    private final JPanel         propertiesPanel;
    private final EditorStatusBar statusBar;

    private final JSplitPane     leftSplit;
    private final JSplitPane     rightSplit;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public EditorFrame() {
        setTitle("Phantasia: Infinity — Editor Suite v2.0");
        setSize(1400, 900);
        setMinimumSize(new Dimension(1000, 650));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(EditorTheme.BG_BASE_SOLID);

        // --- Menu bar ---
        menuBar = new EditorMenuBar();
        setJMenuBar(menuBar);
        wireMenuCallbacks();

        // --- Asset Explorer (left sidebar) ---
        assetExplorer = new AssetExplorer();
        assetExplorer.setPreferredSize(new Dimension(EditorTheme.DEFAULT_LEFT_SPLIT, 0));
        assetExplorer.setOpenHandler(this::openAssetInWorkspace);

        // --- Editor Workspace (center) ---
        workspace = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        workspace.setBackground(EditorTheme.BG_PANEL);
        workspace.setForeground(EditorTheme.TEXT);
        workspace.setFont(EditorTheme.FONT_LABEL);

        // Placeholder when no tabs are open
        JPanel emptyWorkspace = new JPanel(new GridBagLayout());
        emptyWorkspace.setBackground(EditorTheme.BG_PANEL);
        JLabel hint = new JLabel("Double-click an asset to open it here.");
        hint.setFont(EditorTheme.FONT_HEADER);
        hint.setForeground(EditorTheme.TEXT_DIM);
        emptyWorkspace.add(hint);
        workspace.addTab("Welcome", emptyWorkspace);

        // --- Properties & Tools (right sidebar) ---
        propertiesPanel = new JPanel(new BorderLayout());
        propertiesPanel.setBackground(EditorTheme.BG_PANEL);
        propertiesPanel.setPreferredSize(new Dimension(EditorTheme.DEFAULT_RIGHT_SPLIT, 0));

        JLabel propsHeader = new JLabel("  PROPERTIES");
        propsHeader.setFont(EditorTheme.FONT_LABEL_B);
        propsHeader.setForeground(EditorTheme.ACCENT);
        propsHeader.setBorder(new EmptyBorder(6, 4, 6, 4));
        propertiesPanel.add(propsHeader, BorderLayout.NORTH);

        JLabel propsHint = new JLabel("Select an asset to see its properties.");
        propsHint.setFont(EditorTheme.FONT_LABEL);
        propsHint.setForeground(EditorTheme.TEXT_DIM);
        propsHint.setHorizontalAlignment(SwingConstants.CENTER);
        propertiesPanel.add(propsHint, BorderLayout.CENTER);

        // --- Split panes ---
        // Right split: workspace | properties
        rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, workspace, propertiesPanel);
        rightSplit.setResizeWeight(1.0);   // workspace gets extra space
        rightSplit.setDividerLocation(getWidth() - EditorTheme.DEFAULT_RIGHT_SPLIT - 50);
        rightSplit.setOneTouchExpandable(true);
        rightSplit.setBorder(null);

        // Left split: asset explorer | (workspace + properties)
        leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, assetExplorer, rightSplit);
        leftSplit.setResizeWeight(0.0);    // asset explorer stays fixed width
        leftSplit.setDividerLocation(EditorTheme.DEFAULT_LEFT_SPLIT);
        leftSplit.setOneTouchExpandable(true);
        leftSplit.setBorder(null);

        // --- Status bar ---
        statusBar = new EditorStatusBar();

        // --- Assemble ---
        add(leftSplit, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        // --- Window close interceptor (Section 5.6) ---
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleExit();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Workspace tab management
    // -------------------------------------------------------------------------

    /**
     * Opens an asset in the workspace. If a tab for this asset is already
     * open, it's selected. Otherwise, a new tab is created.
     *
     * @param category the data category (e.g. "monsters", "towns")
     * @param id       the record identifier
     */
    public void openAssetInWorkspace(String category, Object id) {
        String tabKey = category + ":" + id;

        // Check if already open
        for (int i = 0; i < workspace.getTabCount(); i++) {
            Component tab = workspace.getComponentAt(i);
            if (tab instanceof WorkspaceTab wt && tabKey.equals(wt.getTabKey())) {
                workspace.setSelectedIndex(i);
                updatePropertiesPanel(tab);
                return;
            }
        }

        // Create a new editor panel for this record
        JPanel editorPanel;
        try {
            editorPanel = createEditorPanel(category, id);
        } catch (Exception ex) {
            ex.printStackTrace();
            statusBar.setMessage("Error opening " + category + ": " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Failed to create editor panel:\n" + ex.getMessage(),
                    "Editor Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (editorPanel == null) {
            statusBar.setMessage("No editor available for: " + category);
            return;
        }

        String tabTitle = buildTabTitle(category, id);
        workspace.addTab(tabTitle, editorPanel);
        workspace.setSelectedComponent(editorPanel);

        // Add close button to tab
        int index = workspace.indexOfComponent(editorPanel);
        if (index >= 0) {
            workspace.setTabComponentAt(index, new ClosableTabHeader(tabTitle, () -> {
                workspace.remove(editorPanel);
            }));
        }

        updatePropertiesPanel(editorPanel);
        statusBar.setMessage("Opened: " + tabTitle);
    }

    /**
     * Creates the appropriate editor panel for a data type.
     * Handles both individual records (id = Integer) and category-level
     * "browse all" views (id = null, opened from category double-click).
     */
    private JPanel createEditorPanel(String category, Object id) {
        String tabKey = category + ":" + id;
        EditorState state = EditorState.get();

        return switch (category) {
            // Individual record editors
            case "monsters" -> (id instanceof Integer idx) ? new MonsterEditorPanel(idx) : new BestiaryPanel();
            case "spells"   -> (id instanceof Integer idx) ? new SpellEditorPanel(idx) : new SpellBookPanel();

            // Interior map editor — opens the tile painter directly
            case "interiorMaps" -> {
                if (id instanceof Integer mapId) {
                    InteriorMap im = state.getInteriorMap(mapId);
                    if (im != null) {
                        System.out.println("[EditorFrame] Opening InteriorMapEditorPanel for map " + mapId
                                + " (" + im.getName() + ", " + im.getWidth() + "×" + im.getHeight() + ")");
                        yield new InteriorMapEditorPanel(im);
                    }
                    yield placeholder(tabKey, "Interior map " + mapId + " not found");
                }
                // Category-level: trigger wizard
                InteriorMap created = NewMapWizard.show(this, InteriorMapType.TOWN);
                if (created != null) {
                    assetExplorer.rebuildTree();
                    yield new InteriorMapEditorPanel(created);
                }
                yield null; // user cancelled
            }

            // Towns — if specific ID, open its interior map; if category, offer wizard or manager
            case "towns" -> {
                if (id instanceof Integer townId) {
                    InteriorMap townMap = findOrCreateTownMap(townId);
                    if (townMap != null) yield new InteriorMapEditorPanel(townMap);
                    yield placeholder(tabKey, "Town " + townId);
                }
                // Category double-click: if no towns exist, auto-wizard
                if (state.getTowns().isEmpty() && !hasInteriorMapsOfType(InteriorMapType.TOWN)) {
                    InteriorMap created = NewMapWizard.show(this, InteriorMapType.TOWN);
                    if (created != null) {
                        assetExplorer.rebuildTree();
                        yield new InteriorMapEditorPanel(created);
                    }
                    yield null;
                }
                yield new TownEditorPanel();
            }

            // Dungeons — same pattern as towns
            case "dungeons" -> {
                if (id instanceof Integer dngId) {
                    InteriorMap dngMap = findOrCreateDungeonMap(dngId);
                    if (dngMap != null) yield new InteriorMapEditorPanel(dngMap);
                    yield placeholder(tabKey, "Dungeon " + dngId);
                }
                // Category double-click: if no dungeons exist, auto-wizard
                if (state.getDungeons().isEmpty() && !hasInteriorMapsOfType(InteriorMapType.DUNGEON)) {
                    InteriorMap created = NewMapWizard.show(this, InteriorMapType.DUNGEON);
                    if (created != null) {
                        assetExplorer.rebuildTree();
                        yield new InteriorMapEditorPanel(created);
                    }
                    yield null;
                }
                yield new DungeonEditorTab();
            }

            // Category-level browse panels
            case "items"    -> new ItemBrowserTab();
            case "worldMap" -> new WorldMapTab();

            // Map generator (Ctrl+G or Maps menu)
            case "generator" -> new MapGeneratorPanel();

            // NPC editor — all NPCs with dialogue authoring
            case "npcs" -> new NpcEditorPanel();

            // Quest editor — objectives, rewards, flag dependencies
            case "quests" -> new QuestEditorPanel();

            default -> null;
        };
    }

    /** Returns true if any InteriorMaps of the given type exist. */
    private boolean hasInteriorMapsOfType(InteriorMapType type) {
        return EditorState.get().getInteriorMaps().values().stream()
                .anyMatch(m -> m.getMapType() == type);
    }

    /**
     * Finds the interior map for a town, or creates a new blank one.
     */
    private InteriorMap findOrCreateTownMap(int townId) {
        EditorState state = EditorState.get();
        // Check if an interior map with matching ID exists
        InteriorMap existing = state.getInteriorMap(townId);
        if (existing != null) return existing;

        // Create a new blank town map
        int id = state.nextInteriorMapId();
        InteriorMap map = new InteriorMap(id, "Town " + townId,
                InteriorMapType.TOWN, 24, 20, "town_standard");
        // Fill with default floor
        for (int x = 0; x < 24; x++)
            for (int y = 0; y < 20; y++)
                map.setTile(x, y, 0);
        state.putInteriorMap(map);
        state.markDirty("interiorMap:" + id);
        return map;
    }

    /**
     * Finds the interior map for a dungeon floor, or creates a new blank one.
     */
    private InteriorMap findOrCreateDungeonMap(int dungeonId) {
        EditorState state = EditorState.get();
        InteriorMap existing = state.getInteriorMap(dungeonId);
        if (existing != null) return existing;

        int id = state.nextInteriorMapId();
        InteriorMap map = new InteriorMap(id, "Dungeon " + dungeonId,
                InteriorMapType.DUNGEON, 40, 30, "dungeon_standard");
        for (int x = 0; x < 40; x++)
            for (int y = 0; y < 30; y++)
                map.setTile(x, y, 1); // wall by default
        state.putInteriorMap(map);
        state.markDirty("interiorMap:" + id);
        return map;
    }

    /**
     * Placeholder panel for editor types not yet implemented.
     * Implements {@link WorkspaceTab} so the workspace can identify it.
     */
    private JPanel placeholder(String tabKey, String label) {
        return new PlaceholderTab(tabKey, label);
    }

    private String buildTabTitle(String category, Object id) {
        // Category-level panels (id == null)
        if (id == null) {
            return switch (category) {
                case "monsters"  -> "Bestiary";
                case "spells"    -> "Spell Book";
                case "items"     -> "Item Catalog";
                case "worldMap"  -> "World Map";
                case "dungeons"  -> "Dungeons";
                case "generator" -> "Map Generator";
                case "npcs"      -> "NPC Editor";
                case "quests"    -> "Quest Editor";
                case "towns"     -> "Town Manager";
                default -> category;
            };
        }

        EditorState state = EditorState.get();
        return switch (category) {
            case "worldMap" -> "World Map";
            case "monsters" -> {
                if (id instanceof Integer idx) {
                    byte[] block = state.getMonster(idx);
                    if (block != null) {
                        String name = new DataCore(block).getName();
                        if (name != null && !name.isBlank()) yield name;
                    }
                }
                yield "Monster #" + id;
            }
            case "spells" -> {
                if (id instanceof Integer idx) {
                    byte[] block = state.getSpell(idx);
                    if (block != null) {
                        String name = new DataCore(block).getName();
                        if (name != null && !name.isBlank()) yield name;
                    }
                }
                yield "Spell #" + id;
            }
            case "interiorMaps" -> {
                if (id instanceof Integer mapId) {
                    InteriorMap im = state.getInteriorMap(mapId);
                    if (im != null) yield im.getName();
                }
                yield "Map #" + id;
            }
            default -> singularize(category) + " " + id;
        };
    }

    private void updatePropertiesPanel(Component tab) {
        // TODO: Swap the properties panel content based on the active tab type.
        //       Each editor panel type provides its own properties sub-panel.
    }

    private static String singularize(String s) {
        if (s.endsWith("ies")) return s.substring(0, s.length() - 3) + "y";
        if (s.endsWith("s"))   return s.substring(0, s.length() - 1);
        return s;
    }

    // -------------------------------------------------------------------------
    // Menu callbacks
    // -------------------------------------------------------------------------

    private void wireMenuCallbacks() {
        menuBar.setOnNewProject(this::handleNewProject);
        menuBar.setOnOpenProject(this::handleOpenProject);
        menuBar.setOnSaveProject(this::handleSaveProject);
        menuBar.setOnSaveProjectAs(this::handleSaveProjectAs);
        menuBar.setOnBakeAll(this::handleBakeAll);
        menuBar.setOnBakeAndTour(this::handleBakeAndTour);
        menuBar.setOnTourLaunch(this::handleTourLaunch);
        menuBar.setOnMapGenerator(() -> openAssetInWorkspace("generator", null));
        menuBar.setOnOpenWorldMap(() -> openAssetInWorkspace("worldMap", null));
        menuBar.setOnNewTownMap(() -> handleNewMap(InteriorMapType.TOWN));
        menuBar.setOnNewDungeonMap(() -> handleNewMap(InteriorMapType.DUNGEON));
        menuBar.setOnExit(this::handleExit);
    }

    /**
     * Opens the New Map Wizard, creates a map, and opens it in the
     * InteriorMapEditorPanel immediately — no intermediate lookups.
     */
    private void handleNewMap(InteriorMapType type) {
        InteriorMap map = NewMapWizard.show(this, type);
        if (map == null) return; // user cancelled

        assetExplorer.rebuildTree();

        // Directly create and add the editor panel — bypass openAssetInWorkspace
        // to avoid any tab-key matching issues
        try {
            InteriorMapEditorPanel editor = new InteriorMapEditorPanel(map);
            String tabTitle = map.getName();
            workspace.addTab(tabTitle, editor);
            workspace.setSelectedComponent(editor);

            int index = workspace.indexOfComponent(editor);
            if (index >= 0) {
                workspace.setTabComponentAt(index, new ClosableTabHeader(tabTitle, () -> {
                    workspace.remove(editor);
                }));
            }
            statusBar.setMessage("Created: " + map.getName()
                    + " (" + map.getWidth() + "×" + map.getHeight() + " " + map.getMapType() + ")");
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to open map editor:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleNewProject() {
        if (!confirmDiscardDirty("New Project")) return;
        EditorState.get().newProject();
        closeAllTabs();
        statusBar.setMessage("New project created.");
    }

    private void handleOpenProject() {
        if (!confirmDiscardDirty("Open Project")) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Phantasia Project (*.phantasia)", "phantasia"));
        chooser.setDialogTitle("Open Phantasia Project");

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                EditorState.get().openProject(chooser.getSelectedFile().toPath());
                closeAllTabs();
                assetExplorer.rebuildTree();
                statusBar.setMessage("Opened: " + chooser.getSelectedFile().getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Failed to open project:\n" + ex.getMessage(),
                        "Open Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleSaveProject() {
        EditorState state = EditorState.get();
        if (state.getProjectPath() != null) {
            try {
                state.saveProject();
                statusBar.setMessage("Project saved.");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Failed to save project:\n" + ex.getMessage(),
                        "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            handleSaveProjectAs();
        }
    }

    private void handleSaveProjectAs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Phantasia Project (*.phantasia)", "phantasia"));
        chooser.setDialogTitle("Save Phantasia Project As");

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath();
            if (!path.toString().endsWith(".phantasia")) {
                path = Path.of(path + ".phantasia");
            }
            try {
                EditorState.get().saveProject(path);
                setTitle("Phantasia: Infinity — " + path.getFileName());
                statusBar.setMessage("Project saved: " + path.getFileName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Failed to save project:\n" + ex.getMessage(),
                        "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleBakeAll() {
        try {
            Path datDir = Path.of(DataPaths.DAT_DIR);
            EditorState.get().bakeAll(datDir);
            statusBar.setMessage("All data baked to " + datDir);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Bake failed:\n" + ex.getMessage(),
                    "Bake Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleBakeAndTour() {
        handleBakeAll();
        handleTourLaunch();
    }

    private void handleTourLaunch() {
        // TODO: Call TourMain.launch() in-process (Phase 4).
        //       For now, delegate to the existing TourLauncher.
        statusBar.setMessage("Tour launch — wiring in Phase 4.");
        try {
            Class<?> tourMain = Class.forName("com.phantasia.j2d.tour.TourMain");
            java.lang.reflect.Method launch = tourMain.getMethod("main", String[].class);
            launch.invoke(null, (Object) new String[]{});
        } catch (Exception ex) {
            statusBar.setMessage("Tour launch failed: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Exit protection  (Section 5.6)
    // -------------------------------------------------------------------------

    private void handleExit() {
        if (confirmDiscardDirty("Exit")) {
            dispose();
            System.exit(0);
        }
    }

    /**
     * Checks for unsaved changes and prompts the user.
     * Returns true if it's safe to proceed (no dirty data, or user chose
     * to discard/bake), false if the user cancelled.
     */
    private boolean confirmDiscardDirty(String action) {
        EditorState state = EditorState.get();
        if (!state.isDirty()) return true;

        int count = state.getDirtyCount();
        String msg = "Unsaved changes exist (" + count
                + " record" + (count == 1 ? "" : "s") + " dirty).";

        String[] options = { "Bake & " + action, "Discard & " + action, "Cancel" };
        int choice = JOptionPane.showOptionDialog(this, msg,
                "Unsaved Changes",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null, options, options[2]);

        return switch (choice) {
            case 0 -> {
                // Bake first, then proceed
                handleBakeAll();
                yield true;
            }
            case 1 -> true;   // Discard
            default -> false;  // Cancel
        };
    }

    // -------------------------------------------------------------------------
    // Tab helpers
    // -------------------------------------------------------------------------

    private void closeAllTabs() {
        workspace.removeAll();
    }

    // -------------------------------------------------------------------------
    // Public accessors for sub-components
    // -------------------------------------------------------------------------

    public AssetExplorer   getAssetExplorer()  { return assetExplorer; }
    public JTabbedPane     getWorkspace()      { return workspace; }
    public JPanel          getPropertiesPanel() { return propertiesPanel; }
    public EditorStatusBar getStatusBar()       { return statusBar; }
    public EditorMenuBar   getEditorMenuBar()   { return menuBar; }

    // =========================================================================
    // Inner classes
    // =========================================================================

    /**
     * Marker interface for workspace tab panels.
     * Each tab panel carries a key for identity checking.
     */
    public interface WorkspaceTab {
        String getTabKey();
    }

    /**
     * Placeholder tab shown for editor types not yet implemented.
     */
    private static class PlaceholderTab extends JPanel implements WorkspaceTab {
        private final String tabKey;

        PlaceholderTab(String tabKey, String label) {
            this.tabKey = tabKey;
            setLayout(new GridBagLayout());
            setBackground(EditorTheme.BG_PANEL);

            JPanel inner = new JPanel();
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
            inner.setBackground(EditorTheme.BG_PANEL);

            JLabel title = new JLabel(label);
            title.setFont(EditorTheme.FONT_HEADER);
            title.setForeground(EditorTheme.ACCENT);
            title.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel hint = new JLabel("(Editor panel — coming in a future phase)");
            hint.setFont(EditorTheme.FONT_LABEL);
            hint.setForeground(EditorTheme.TEXT_DIM);
            hint.setAlignmentX(Component.CENTER_ALIGNMENT);

            inner.add(title);
            inner.add(Box.createVerticalStrut(8));
            inner.add(hint);
            add(inner);
        }

        @Override
        public String getTabKey() { return tabKey; }
    }

    /**
     * A tab header component with a close button.
     */
    private static class ClosableTabHeader extends JPanel {

        ClosableTabHeader(String title, Runnable onClose) {
            setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
            setOpaque(false);

            JLabel label = new JLabel(title);
            label.setFont(EditorTheme.FONT_LABEL);
            label.setForeground(EditorTheme.TEXT);

            JButton closeBtn = new JButton("×");
            closeBtn.setFont(new Font("SansSerif", Font.PLAIN, 14));
            closeBtn.setForeground(EditorTheme.TEXT_DIM);
            closeBtn.setBorderPainted(false);
            closeBtn.setContentAreaFilled(false);
            closeBtn.setFocusable(false);
            closeBtn.setMargin(new Insets(0, 2, 0, 2));
            closeBtn.addActionListener(e -> onClose.run());
            closeBtn.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    closeBtn.setForeground(EditorTheme.RED);
                }
                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    closeBtn.setForeground(EditorTheme.TEXT_DIM);
                }
            });

            add(label);
            add(closeBtn);
        }
    }
}