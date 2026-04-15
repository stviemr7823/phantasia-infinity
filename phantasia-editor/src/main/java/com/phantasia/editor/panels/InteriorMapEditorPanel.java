// phantasia-editor/src/main/java/com/phantasia/editor/panels/InteriorMapEditorPanel.java
package com.phantasia.editor.panels;

import com.phantasia.core.world.*;
import com.phantasia.editor.*;
import com.phantasia.editor.commands.PaintTilesCommand;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Visual tile painter for interior maps — towns, dungeons, and castles.
 * (Design Document Section 4.B: The Visual Cartographer)
 *
 * <p>Layout:</p>
 * <pre>
 * ┌──────────┬─────────────────────────────┬──────────────┐
 * │  TILE    │                             │  FEATURES    │
 * │  PALETTE │    MAP CANVAS               │  & SETTINGS  │
 * │          │    (scroll + zoom)           │              │
 * │          │                             │              │
 * └──────────┴─────────────────────────────┴──────────────┘
 * │  TOOLBAR: Zoom │ Grid │ Fill │ Generate │ Info        │
 * └────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Interactions:</p>
 * <ul>
 *   <li>Left-click / drag: paint selected tile</li>
 *   <li>Right-click: place/edit/remove feature at tile</li>
 *   <li>Ctrl+click: eyedropper (pick tile under cursor)</li>
 *   <li>Scroll wheel: zoom in/out</li>
 * </ul>
 */
public class InteriorMapEditorPanel extends JPanel implements EditorFrame.WorkspaceTab {

    private static final int MIN_CELL = 8;
    private static final int MAX_CELL = 48;
    private static final int DEFAULT_CELL = 24;

    private final InteriorMap       map;
    private final InteriorTileSet   tileSet;
    private final String            tabKey;

    private int                     selectedTile = 0;
    private int                     cellSize     = DEFAULT_CELL;
    private boolean                 showGrid     = true;

    private final MapCanvas         canvas;
    private PaintTilesCommand       activePaintCmd;   // current drag stroke

    // Tile colors — fallback rendering when no sprites are loaded
    private static final Color[] TOWN_TILE_COLORS = {
            new Color(140, 110, 70),   // 0  wood_floor
            new Color(130, 125, 135),  // 1  stone_floor
            new Color(55, 48, 62),     // 2  wall
            new Color(110, 85, 55),    // 3  counter
            new Color(140, 100, 50),   // 4  door
            new Color(100, 130, 160),  // 5  window
            new Color(120, 160, 80),   // 6  stairs
            new Color(200, 100, 30),   // 7  hearth
            new Color(110, 90, 55),    // 8  table
            new Color(80, 60, 40),     // 9  shelf
            new Color(100, 80, 65),    // 10 bed
            new Color(140, 50, 50),    // 11 rug
            new Color(180, 150, 60),   // 12 market_stall
            new Color(80, 140, 180),   // 13 fountain
            new Color(155, 140, 90),   // 14 cobblestone
            new Color(50, 130, 50),    // 15 garden
            new Color(220, 200, 80),   // 16 exit
    };

    private static final Color[] DUNGEON_TILE_COLORS = {
            new Color(100, 95, 110),   // 0  stone_floor
            new Color(45, 40, 55),     // 1  wall
            new Color(140, 100, 50),   // 2  door
            new Color(120, 70, 40),    // 3  locked_door
            new Color(80, 180, 80),    // 4  stairs_up
            new Color(80, 120, 180),   // 5  stairs_down
            new Color(218, 175, 62),   // 6  chest
            new Color(100, 95, 110),   // 7  trap_hidden (looks like floor)
            new Color(200, 60, 60),    // 8  trap_visible
            new Color(70, 65, 75),     // 9  rubble
            new Color(40, 60, 100),    // 10 water
            new Color(180, 160, 80),   // 11 altar
            new Color(80, 70, 60),     // 12 cage
            new Color(160, 140, 60),   // 13 throne
            new Color(65, 60, 75),     // 14 pillar
            new Color(50, 45, 60),     // 15 secret_wall (looks like wall)
    };

    // =====================================================================
    // Construction
    // =====================================================================

    public InteriorMapEditorPanel(InteriorMap map) {
        this.map     = map;
        this.tileSet = (map.getMapType() == InteriorMapType.TOWN)
                ? InteriorTileSet.standardTown()
                : InteriorTileSet.standardDungeon();
        this.tabKey  = "interiorMap:" + map.getId();

        setLayout(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(4, 4, 4, 4));

        // ── Left: tile palette ───────────────────────────────────────────
        JPanel palette = buildTilePalette();

        // ── Center: scrollable map canvas ────────────────────────────────
        canvas = new MapCanvas();
        JScrollPane canvasScroll = new JScrollPane(canvas);
        canvasScroll.getViewport().setBackground(new Color(10, 8, 18));
        canvasScroll.getVerticalScrollBar().setUnitIncrement(cellSize);
        canvasScroll.getHorizontalScrollBar().setUnitIncrement(cellSize);

        // ── Right: feature list + settings ───────────────────────────────
        JPanel rightPanel = buildRightPanel();

        // ── Bottom: toolbar ──────────────────────────────────────────────
        JPanel toolbar = buildToolbar();

        add(palette,      BorderLayout.WEST);
        add(canvasScroll, BorderLayout.CENTER);
        add(rightPanel,   BorderLayout.EAST);
        add(toolbar,      BorderLayout.SOUTH);
    }

    @Override
    public String getTabKey() { return tabKey; }

    // =====================================================================
    // Tile palette (left sidebar)
    // =====================================================================

    private JPanel buildTilePalette() {
        JPanel outer = EditorUtils.titledPanel("Tiles", new BorderLayout());
        outer.setPreferredSize(new Dimension(140, 0));

        JPanel grid = new JPanel(new GridLayout(0, 1, 2, 2));
        grid.setOpaque(false);

        Color[] colors = (map.getMapType() == InteriorMapType.TOWN)
                ? TOWN_TILE_COLORS : DUNGEON_TILE_COLORS;
        ButtonGroup group = new ButtonGroup();

        for (int i = 0; i < tileSet.size(); i++) {
            TileSetEntry entry = tileSet.getEntry(i);
            final int tileIdx = i;

            JToggleButton btn = new JToggleButton(entry.name());
            btn.setFont(new Font("SansSerif", Font.PLAIN, 10));
            btn.setForeground(Color.WHITE);
            btn.setBackground(i < colors.length ? colors[i] : Color.DARK_GRAY);
            btn.setFocusPainted(false);
            btn.setMargin(new Insets(2, 4, 2, 4));
            btn.addActionListener(e -> selectedTile = tileIdx);
            btn.setToolTipText(entry.name()
                    + (entry.passable() ? " (passable)" : " (blocked)")
                    + "  [" + entry.interactionType() + "]");
            group.add(btn);
            grid.add(btn);

            if (i == 0) btn.setSelected(true);
        }

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    // =====================================================================
    // Right panel: features + settings
    // =====================================================================

    private JPanel buildRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setPreferredSize(new Dimension(200, 0));
        panel.setOpaque(false);

        // Feature list
        DefaultListModel<String> featureModel = new DefaultListModel<>();
        rebuildFeatureList(featureModel);
        JList<String> featureList = new JList<>(featureModel);
        featureList.setFont(EditorTheme.FONT_SMALL);

        JPanel featurePanel = EditorUtils.titledPanel("Features", new BorderLayout());
        featurePanel.add(new JScrollPane(featureList), BorderLayout.CENTER);

        // Settings summary
        JPanel settingsPanel = EditorUtils.titledPanel("Settings", new GridLayout(0, 1, 2, 2));
        InteriorSettings s = map.getSettings();
        settingsPanel.add(EditorUtils.label("Type: " + map.getMapType()));
        settingsPanel.add(EditorUtils.label("Size: " + map.getWidth() + "×" + map.getHeight()));
        settingsPanel.add(EditorUtils.label("Fog: " + (s.isFogOfWar() ? "ON" : "OFF")));
        settingsPanel.add(EditorUtils.label("Encounters: " + (s.isEncountersEnabled() ? "ON" : "OFF")));
        settingsPanel.add(EditorUtils.label("Light: " + s.getAmbientLight()));
        settingsPanel.add(EditorUtils.label("Torch: " + s.getTorchRadius()));

        // NPC list
        JPanel npcPanel = EditorUtils.titledPanel("NPCs (" + map.getPlacedNpcs().size() + ")", new BorderLayout());
        DefaultListModel<String> npcModel = new DefaultListModel<>();
        for (var npc : map.getPlacedNpcs())
            npcModel.addElement("NPC #" + npc.npcId() + " at (" + npc.x() + "," + npc.y() + ")");
        npcPanel.add(new JScrollPane(new JList<>(npcModel)), BorderLayout.CENTER);

        panel.add(featurePanel,  BorderLayout.NORTH);
        panel.add(npcPanel,      BorderLayout.CENTER);
        panel.add(settingsPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void rebuildFeatureList(DefaultListModel<String> model) {
        model.clear();
        for (PlacedFeature f : map.getFeatures())
            model.addElement(f.featureType().name() + " (" + f.x() + "," + f.y() + ")");
    }

    // =====================================================================
    // Toolbar
    // =====================================================================

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        bar.setOpaque(false);

        JButton zoomIn  = EditorUtils.button("Zoom +");
        JButton zoomOut = EditorUtils.button("Zoom −");
        JCheckBox gridBox = new JCheckBox("Grid", true);
        gridBox.setOpaque(false);
        gridBox.setForeground(EditorTheme.TEXT);
        JButton fillBtn = EditorUtils.button("Fill All");
        JLabel  infoLabel = EditorUtils.label(map.getWidth() + "×" + map.getHeight()
                + "  " + map.getMapType() + "  id:" + map.getId());

        zoomIn .addActionListener(e -> zoom(4));
        zoomOut.addActionListener(e -> zoom(-4));
        gridBox.addActionListener(e -> { showGrid = gridBox.isSelected(); canvas.repaint(); });
        fillBtn.addActionListener(e -> fillAll());

        bar.add(zoomIn);
        bar.add(zoomOut);
        bar.add(gridBox);
        bar.add(fillBtn);
        bar.add(Box.createHorizontalStrut(20));
        bar.add(infoLabel);
        return bar;
    }

    private void zoom(int delta) {
        cellSize = Math.max(MIN_CELL, Math.min(MAX_CELL, cellSize + delta));
        canvas.updateSize();
        canvas.repaint();
    }

    private void fillAll() {
        PaintTilesCommand cmd = new PaintTilesCommand(map);
        for (int x = 0; x < map.getWidth(); x++)
            for (int y = 0; y < map.getHeight(); y++)
                cmd.addTile(x, y, map.getTile(x, y), selectedTile);
        if (!cmd.isEmpty()) EditorState.get().execute(cmd);
        canvas.repaint();
    }

    // =====================================================================
    // Feature placement dialog (right-click)
    // =====================================================================

    private void showFeatureDialog(int tx, int ty) {
        PlacedFeature existing = map.getFeatureAt(tx, ty);

        JComboBox<PlacedFeatureType> typeBox = new JComboBox<>(PlacedFeatureType.values());
        JTextField dataField = new JTextField(
                existing != null && existing.serviceData() != null
                        ? existing.serviceData().toString() : "", 20);

        if (existing != null) typeBox.setSelectedItem(existing.featureType());

        JPanel panel = new JPanel(new GridLayout(3, 2, 6, 6));
        panel.add(new JLabel("Feature Type:"));
        panel.add(typeBox);
        panel.add(new JLabel("Data (text):"));
        panel.add(dataField);
        panel.add(new JLabel("Position:"));
        panel.add(new JLabel("(" + tx + ", " + ty + ")"));

        String[] options = existing != null
                ? new String[]{"Update", "Remove", "Cancel"}
                : new String[]{"Place", "Cancel"};

        int result = JOptionPane.showOptionDialog(this, panel,
                "Feature at (" + tx + ", " + ty + ")",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);

        if (existing != null && result == 1) {
            // Remove
            map.removeFeature(existing);
            EditorState.get().markDirty(tabKey);
            canvas.repaint();
        } else if (result == 0) {
            // Place or Update
            if (existing != null) map.removeFeature(existing);
            PlacedFeatureType type = (PlacedFeatureType) typeBox.getSelectedItem();
            String data = dataField.getText().trim();
            map.addFeature(new PlacedFeature(tx, ty, type,
                    data.isEmpty() ? null : data));
            EditorState.get().markDirty(tabKey);
            canvas.repaint();
        }
    }

    // =====================================================================
    // Map canvas — the painting surface
    // =====================================================================

    private class MapCanvas extends JPanel {

        MapCanvas() {
            updateSize();
            setBackground(new Color(10, 8, 18));

            MouseAdapter painter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int tx = e.getX() / cellSize;
                    int ty = e.getY() / cellSize;
                    if (!map.inBounds(tx, ty)) return;

                    if (SwingUtilities.isRightMouseButton(e)) {
                        showFeatureDialog(tx, ty);
                        return;
                    }

                    if (e.isControlDown()) {
                        // Eyedropper
                        selectedTile = map.getTile(tx, ty);
                        return;
                    }

                    // Start a paint stroke
                    activePaintCmd = new PaintTilesCommand(map);
                    paintTile(tx, ty);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (activePaintCmd == null) return;
                    if (SwingUtilities.isRightMouseButton(e)) return;
                    int tx = e.getX() / cellSize;
                    int ty = e.getY() / cellSize;
                    if (!map.inBounds(tx, ty)) return;
                    paintTile(tx, ty);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (activePaintCmd != null && !activePaintCmd.isEmpty()) {
                        EditorState.get().execute(activePaintCmd);
                    }
                    activePaintCmd = null;
                }
            };
            addMouseListener(painter);
            addMouseMotionListener(painter);

            // Scroll-wheel zoom
            addMouseWheelListener(e -> {
                if (e.isControlDown()) {
                    zoom(e.getWheelRotation() < 0 ? 4 : -4);
                }
            });
        }

        private void paintTile(int tx, int ty) {
            int oldTile = map.getTile(tx, ty);
            if (oldTile == selectedTile) return;
            activePaintCmd.addTile(tx, ty, oldTile, selectedTile);
            map.setTile(tx, ty, selectedTile);
            repaint();
        }

        void updateSize() {
            setPreferredSize(new Dimension(
                    map.getWidth() * cellSize, map.getHeight() * cellSize));
            revalidate();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            int w = map.getWidth(), h = map.getHeight();

            Color[] colors = (map.getMapType() == InteriorMapType.TOWN)
                    ? TOWN_TILE_COLORS : DUNGEON_TILE_COLORS;

            // ── Draw tiles ───────────────────────────────────────────────
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    int idx = map.getTile(x, y);
                    Color c = (idx >= 0 && idx < colors.length)
                            ? colors[idx] : Color.DARK_GRAY;
                    g2.setColor(c);
                    g2.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
                }
            }

            // ── Draw grid ────────────────────────────────────────────────
            if (showGrid && cellSize >= 12) {
                g2.setColor(new Color(0, 0, 0, 50));
                for (int x = 0; x <= w; x++)
                    g2.drawLine(x * cellSize, 0, x * cellSize, h * cellSize);
                for (int y = 0; y <= h; y++)
                    g2.drawLine(0, y * cellSize, w * cellSize, y * cellSize);
            }

            // ── Draw features ────────────────────────────────────────────
            g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(8, cellSize / 3)));
            for (PlacedFeature f : map.getFeatures()) {
                int px = f.x() * cellSize;
                int py = f.y() * cellSize;

                // Gold border around feature tile
                g2.setColor(new Color(218, 175, 62, 180));
                g2.drawRect(px + 1, py + 1, cellSize - 3, cellSize - 3);
                g2.drawRect(px + 2, py + 2, cellSize - 5, cellSize - 5);

                // Label
                String label = featureLabel(f.featureType());
                g2.setColor(new Color(255, 230, 150));
                g2.drawString(label, px + 3, py + cellSize - 4);
            }

            // ── Draw placed NPCs ─────────────────────────────────────────
            for (var npc : map.getPlacedNpcs()) {
                int px = npc.x() * cellSize;
                int py = npc.y() * cellSize;
                g2.setColor(new Color(100, 180, 255, 180));
                g2.fillOval(px + 3, py + 3, cellSize - 6, cellSize - 6);
                g2.setColor(Color.WHITE);
                g2.drawString("N", px + cellSize / 3, py + cellSize * 2 / 3);
            }

            // ── Cursor hover highlight ───────────────────────────────────
            Point mouse = getMousePosition();
            if (mouse != null) {
                int hx = mouse.x / cellSize;
                int hy = mouse.y / cellSize;
                if (map.inBounds(hx, hy)) {
                    g2.setColor(new Color(255, 255, 255, 40));
                    g2.fillRect(hx * cellSize, hy * cellSize, cellSize, cellSize);
                }
            }
        }

        private String featureLabel(PlacedFeatureType type) {
            return switch (type) {
                case SHOP_COUNTER  -> "Sh";
                case INN_COUNTER   -> "In";
                case GUILD_COUNTER -> "Gu";
                case BANK_COUNTER  -> "Bk";
                case CHEST         -> "Ch";
                case LOCKED_DOOR   -> "Lk";
                case STAIRS_LINK   -> "St";
                case EXIT          -> "Ex";
                case ALTAR         -> "Al";
                case SIGN          -> "Si";
                case TRAP          -> "Tr";
            };
        }
    }
}