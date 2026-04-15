// phantasia-core/src/main/java/com/phantasia/core/tools/WorldEditorPanel.java
package com.phantasia.core.tools;

import com.phantasia.core.data.DataPaths;
import com.phantasia.core.world.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;

import com.phantasia.core.tools.TourLauncher;

/**
 * Tile-painter for the overland world map.
 * Left-click paints the selected TileType.
 * Right-click opens a single dialog to place or clear a feature.
 *
 * FEATURE METADATA:
 *   Feature tile references (type + id) are stored in features[][].
 *   Full metadata (name, description, services) is stored in editorRegistry,
 *   a FeatureRegistry kept in sync with features[][] at all times.
 *
 *   When baking, both world.dat (tile data) and features.dat (metadata)
 *   are written. When loading, the registry is rebuilt from world.dat with
 *   placeholder names — the user can edit each feature to fill in real names
 *   before baking again.
 */
public class WorldEditorPanel extends JPanel
{

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    private static final int CELL      = 14;
    private static final int DEFAULT_W = 64;
    private static final int DEFAULT_H = 64;

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private TileType[][]    terrain       = new TileType[DEFAULT_W][DEFAULT_H];
    private WorldFeature[][] features     = new WorldFeature[DEFAULT_W][DEFAULT_H];
    private FeatureRegistry  editorRegistry = new FeatureRegistry();

    private int           mapW      = DEFAULT_W;
    private int           mapH      = DEFAULT_H;
    private WorldPosition startPos  = new WorldPosition(8, 8);

    private TileType      selectedTile = TileType.PLAINS;
    private final MapCanvas canvas     = new MapCanvas();

    // ------------------------------------------------------------------
    // Tile color palette
    // ------------------------------------------------------------------

    private static final java.util.Map<TileType, Color> TILE_COLORS =
            new java.util.EnumMap<>(TileType.class);

    static
    {
        TILE_COLORS.put(TileType.OCEAN,    new Color(30,  60,  120));
        TILE_COLORS.put(TileType.PLAINS,   new Color(80,  130,  60));
        TILE_COLORS.put(TileType.ROAD,     new Color(160, 140,  90));
        TILE_COLORS.put(TileType.FOREST,   new Color(30,   80,  30));
        TILE_COLORS.put(TileType.MOUNTAIN, new Color(120, 110,  90));
        TILE_COLORS.put(TileType.SWAMP,    new Color(60,   90,  60));
        TILE_COLORS.put(TileType.TOWN,     new Color(200, 180, 100));
        TILE_COLORS.put(TileType.DUNGEON,  new Color(80,   30,  30));
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    public WorldEditorPanel()
    {
        setLayout(new BorderLayout(8, 8));
        setBackground(PhantasiaEditor.BG_DARK);
        setBorder(new EmptyBorder(12, 12, 12, 12));

        fillOcean();

        // Tile palette
        JPanel palette = EditorUtils.titledPanel("Tile Palette",
                new GridLayout(0, 1, 4, 4));
        palette.setPreferredSize(new Dimension(130, 0));
        ButtonGroup group = new ButtonGroup();
        for (TileType type : TileType.values())
        {
            JToggleButton btn = new JToggleButton(type.name());
            btn.setBackground(TILE_COLORS.getOrDefault(type, PhantasiaEditor.BG_PANEL));
            btn.setForeground(Color.WHITE);
            btn.setFont(new Font("SansSerif", Font.BOLD, 10));
            btn.setFocusPainted(false);
            btn.addActionListener(e -> selectedTile = type);
            group.add(btn);
            palette.add(btn);
            if (type == TileType.PLAINS) btn.setSelected(true);
        }

        // Toolbar buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setBackground(PhantasiaEditor.BG_DARK);

        JButton newBtn  = EditorUtils.button("New Map");
        JButton loadBtn = EditorUtils.button("Load .map");
        JButton saveBtn = EditorUtils.accentButton("Bake .map");
        JButton fillBtn = EditorUtils.button("Fill Ocean");
        JButton tourBtn = makeTourButton();

        newBtn.addActionListener(e  -> newMap());
        loadBtn.addActionListener(e -> loadMap());
        saveBtn.addActionListener(e -> bakeMap());
        fillBtn.addActionListener(e -> { fillOcean(); canvas.repaint(); });

        buttons.add(newBtn);
        buttons.add(loadBtn);
        buttons.add(fillBtn);
        buttons.add(saveBtn);
        buttons.add(tourBtn);

        add(palette, BorderLayout.WEST);
        add(EditorUtils.scrollPane(canvas), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        // Auto-load world.map on startup if it exists
        autoLoad();
    }

    // ------------------------------------------------------------------
    // Map operations
    // ------------------------------------------------------------------

    private void fillOcean()
    {
        for (int x = 0; x < mapW; x++)
            for (int y = 0; y < mapH; y++)
                terrain[x][y] = TileType.OCEAN;
    }

    private void autoLoad()
    {
        File f = new File(DataPaths.DAT_DIR + "/world.map");
        if (!f.exists()) return;
        try
        {
            applyWorldMap(WorldMap.loadFromFile(f.getPath()));
        } catch (IOException ex)
        {
            System.err.println("[WorldEditor] autoLoad failed: " + ex.getMessage());
        }
    }

    private void newMap()
    {
        JTextField wField = new JTextField("64", 5);
        JTextField hField = new JTextField("64", 5);
        JPanel panel = new JPanel(new GridLayout(2, 2, 6, 6));
        panel.add(new JLabel("Width:"));  panel.add(wField);
        panel.add(new JLabel("Height:")); panel.add(hField);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "New Map Size", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        try
        {
            mapW = Math.max(1, Integer.parseInt(wField.getText().trim()));
            mapH = Math.max(1, Integer.parseInt(hField.getText().trim()));
        } catch (NumberFormatException ex)
        {
            mapW = 64;
            mapH = 64;
        }

        terrain        = new TileType[mapW][mapH];
        features       = new WorldFeature[mapW][mapH];
        editorRegistry = new FeatureRegistry();
        fillOcean();
        canvas.setPreferredSize(new Dimension(mapW * CELL, mapH * CELL));
        canvas.repaint();
    }

    private void loadMap()
    {
        File f = EditorUtils.chooseMapFile(this, "Load world.map");
        if (f == null) return;
        try
        {
            applyWorldMap(WorldMap.loadFromFile(f.getPath()));
        } catch (IOException ex)
        {
            EditorUtils.error(this, "Load failed: " + ex.getMessage());
        }
    }

    /**
     * Applies a loaded WorldMap to the editor state.
     *
     * The registry is rebuilt from the tile data with placeholder names.
     * If a features.dat exists alongside world.dat, it is loaded to
     * restore real names. If not, the user can edit each feature tile
     * to fill in proper names before the next bake.
     */
    private void applyWorldMap(WorldMap wm)
    {
        mapW     = wm.getWidth();
        mapH     = wm.getHeight();
        terrain  = new TileType[mapW][mapH];
        features = new WorldFeature[mapW][mapH];

        for (int x = 0; x < mapW; x++)
            for (int y = 0; y < mapH; y++)
            {
                Tile t = wm.getTile(x, y);
                terrain[x][y]  = t.getType();
                features[x][y] = t.hasFeature() ? t.getFeature() : null;
            }

        startPos = wm.getStartPosition();

        // Rebuild registry — try loading features.dat first for real names
        editorRegistry = new FeatureRegistry();
        String featuresPath = DataPaths.DAT_DIR + "/features.dat";
        boolean loadedFromDat = false;
        try
        {
            FeatureRegistry loaded = FeatureRegistry.load(featuresPath);
            if (loaded.size() > 0)
            {
                editorRegistry = loaded;
                loadedFromDat  = true;
                System.out.println("[WorldEditor] Loaded " + loaded.size()
                        + " feature records from features.dat");
            }
        } catch (IOException ex)
        {
            System.out.println("[WorldEditor] features.dat not found — "
                    + "using placeholder names.");
        }

        // For any tile with a feature not already in the registry,
        // add a placeholder so the dialog has something to show
        if (!loadedFromDat)
        {
            rebuildRegistryFromTiles();
        }

        canvas.setPreferredSize(new Dimension(mapW * CELL, mapH * CELL));
        canvas.repaint();
    }

    /**
     * Populates editorRegistry from the current features[][] array using
     * placeholder names. Used when features.dat is unavailable.
     * Placeholder format: "TYPE_id" e.g. "TOWN_0", "DUNGEON_1".
     */
    private void rebuildRegistryFromTiles()
    {
        editorRegistry = new FeatureRegistry();
        for (int x = 0; x < mapW; x++)
        {
            for (int y = 0; y < mapH; y++)
            {
                WorldFeature f = features[x][y];
                if (f == null || f.isNone()) continue;

                String placeholder = f.getType().name() + "_" + f.getId();
                byte services = f.getType() == FeatureType.TOWN
                        ? (byte)(FeatureRecord.SERVICE_INN
                                 | FeatureRecord.SERVICE_SHOP
                                 | FeatureRecord.SERVICE_GUILD
                                 | FeatureRecord.SERVICE_BANK)
                        : (byte) 0;

                editorRegistry.add(new FeatureRecord(
                        f.getId(), f.getType(), x, y,
                        placeholder, "", services));
            }
        }
    }

    // ------------------------------------------------------------------
    // Bake
    // ------------------------------------------------------------------

    /**
     * Bakes world.dat (tile geometry) and features.dat (feature metadata)
     * to the data directory.
     */
    private void bakeMap()
    {
        File f = EditorUtils.chooseSaveFile(this, "Bake world.map", "map");
        if (f == null) return;
        try
        {
            // Write world.dat
            WorldMapBaker.bake(f.getPath(), mapW, mapH,
                    startPos.x(), startPos.y(), terrain, features);

            // Write features.dat alongside world.dat
            String featuresPath = DataPaths.DAT_DIR + "/features.dat";
            editorRegistry.save(featuresPath);

            EditorUtils.confirm(this,
                    "Baked " + mapW + "x" + mapH + " map to " + f.getName()
                            + "\nfeatures.dat written: "
                            + editorRegistry.size() + " feature records.");

        } catch (IOException ex)
        {
            EditorUtils.error(this, "Bake failed: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Feature placement
    // ------------------------------------------------------------------

    /**
     * Opens a dialog to place or clear a feature at tile (x, y).
     * Reads current values from editorRegistry; writes back to both
     * features[][] and editorRegistry on OK.
     */
    private void placeFeature(int x, int y)
    {
        JComboBox<String> typeBox = new JComboBox<>(
                new String[]{"None", "Town", "Dungeon"});

        // Read current metadata from registry
        FeatureRecord existing = editorRegistry.getAt(x, y);

        JTextField nameField = new JTextField(
                existing != null ? existing.getName() : "", 16);
        JTextField idField   = new JTextField(
                existing != null ? String.valueOf(existing.getId()) : "0", 5);
        JTextField descField = new JTextField(
                existing != null ? existing.getDescription() : "", 30);

        // Services checkboxes (towns only — dungeons have none)
        JCheckBox innBox   = new JCheckBox("Inn",   existing != null && existing.hasInn());
        JCheckBox shopBox  = new JCheckBox("Shop",  existing != null && existing.hasShop());
        JCheckBox guildBox = new JCheckBox("Guild", existing != null && existing.hasGuild());
        JCheckBox bankBox  = new JCheckBox("Bank",  existing != null && existing.hasBank());

        JPanel servicePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        servicePanel.add(innBox);
        servicePanel.add(shopBox);
        servicePanel.add(guildBox);
        servicePanel.add(bankBox);

        JPanel panel = new JPanel(new GridLayout(5, 2, 6, 6));
        panel.add(new JLabel("Type:"));        panel.add(typeBox);
        panel.add(new JLabel("Name:"));        panel.add(nameField);
        panel.add(new JLabel("ID:"));          panel.add(idField);
        panel.add(new JLabel("Description:")); panel.add(descField);
        panel.add(new JLabel("Services:"));    panel.add(servicePanel);

        // Pre-select current type
        if (features[x][y] != null)
        {
            typeBox.setSelectedIndex(
                    features[x][y].getType() == FeatureType.TOWN ? 1 : 2);
        }

        // Disable service checkboxes for dungeons
        typeBox.addActionListener(e -> {
            boolean isTown = typeBox.getSelectedIndex() == 1;
            innBox.setEnabled(isTown);
            shopBox.setEnabled(isTown);
            guildBox.setEnabled(isTown);
            bankBox.setEnabled(isTown);
        });
        // Fire once to set initial enabled state
        boolean initiallyTown = typeBox.getSelectedIndex() == 1;
        innBox.setEnabled(initiallyTown);
        shopBox.setEnabled(initiallyTown);
        guildBox.setEnabled(initiallyTown);
        bankBox.setEnabled(initiallyTown);

        int result = JOptionPane.showConfirmDialog(canvas, panel,
                "Feature at (" + x + "," + y + ")",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        int choice = typeBox.getSelectedIndex();

        if (choice == 0)
        {
            // Clear — remove from both structures
            if (existing != null)
                editorRegistry.remove(existing.getType(), existing.getId());
            features[x][y] = null;
            if (terrain[x][y] == TileType.TOWN || terrain[x][y] == TileType.DUNGEON)
                terrain[x][y] = TileType.PLAINS;
        }
        else
        {
            String name = nameField.getText().trim();
            if (name.isEmpty()) return;

            String desc = descField.getText().trim();
            int id = 0;
            try
            {
                id = Integer.parseInt(idField.getText().trim());
            } catch (NumberFormatException ignored) {}

            FeatureType type = (choice == 1) ? FeatureType.TOWN : FeatureType.DUNGEON;

            // Compute service flags
            byte services;
            if (type == FeatureType.TOWN)
            {
                services = 0;
                if (innBox.isSelected())   services |= FeatureRecord.SERVICE_INN;
                if (shopBox.isSelected())  services |= FeatureRecord.SERVICE_SHOP;
                if (guildBox.isSelected()) services |= FeatureRecord.SERVICE_GUILD;
                if (bankBox.isSelected())  services |= FeatureRecord.SERVICE_BANK;
            }
            else
            {
                services = 0; // dungeons have no services
            }

            // Update tile reference (slim — type + id only)
            features[x][y] = (type == FeatureType.TOWN)
                    ? WorldFeature.town(id)
                    : WorldFeature.dungeon(id);
            terrain[x][y] = (type == FeatureType.TOWN)
                    ? TileType.TOWN : TileType.DUNGEON;

            // Remove old registry entry at this coord (id or type may have changed)
            if (existing != null)
                editorRegistry.remove(existing.getType(), existing.getId());

            // Add updated registry entry with full metadata
            editorRegistry.add(new FeatureRecord(
                    id, type, x, y, name, desc, services));
        }
    }

    // ------------------------------------------------------------------
    // Tour launcher
    // ------------------------------------------------------------------

    private JButton makeTourButton()
    {
        JButton btn = new JButton("▶  Bake & Tour");
        btn.setBackground(new Color(60, 50, 20));
        btn.setForeground(new Color(220, 180, 60));
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 100, 40)),
                new EmptyBorder(4, 14, 4, 14)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText("Bake the current map and launch the Touring Engine");
        btn.addActionListener(e -> launchTour());
        return btn;
    }

    private void launchTour()
    {
        TourLauncher.bakeAndLaunch(
                terrain, features,
                mapW, mapH,
                startPos.x(), startPos.y(),
                this);
    }

    // ------------------------------------------------------------------
    // Inner canvas
    // ------------------------------------------------------------------

    private class MapCanvas extends JPanel
    {
        MapCanvas()
        {
            setPreferredSize(new Dimension(mapW * CELL, mapH * CELL));
            setBackground(PhantasiaEditor.BG_DARK);

            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent e) { paint(e); }
            });
            addMouseMotionListener(new MouseMotionAdapter()
            {
                @Override
                public void mouseDragged(MouseEvent e) { paint(e); }
            });
        }

        private void paint(MouseEvent e)
        {
            int tx = e.getX() / CELL;
            int ty = e.getY() / CELL;
            if (tx < 0 || tx >= mapW || ty < 0 || ty >= mapH) return;

            if (SwingUtilities.isRightMouseButton(e))
            {
                showTileMenu(tx, ty);
            } else
            {
                terrain[tx][ty] = selectedTile;
            }
            repaint();
        }

        private void showTileMenu(int tx, int ty)
        {
            JPopupMenu menu = new JPopupMenu();

            JMenuItem featureItem = new JMenuItem("Place / Clear Feature");
            featureItem.addActionListener(e -> {
                placeFeature(tx, ty);
                repaint();
            });

            JMenuItem startItem = new JMenuItem(
                    "Set Start Position  →  (" + tx + ", " + ty + ")");
            startItem.addActionListener(e -> {
                startPos = new WorldPosition(tx, ty);
                repaint();
            });

            menu.add(featureItem);
            menu.add(startItem);

            menu.show(this,
                    tx * CELL + CELL / 2,
                    ty * CELL + CELL / 2);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            for (int x = 0; x < mapW; x++)
            {
                for (int y = 0; y < mapH; y++)
                {
                    // Tile base color
                    g.setColor(TILE_COLORS.getOrDefault(terrain[x][y], Color.BLACK));
                    g.fillRect(x * CELL, y * CELL, CELL, CELL);

                    // Feature dot — color by type
                    if (features[x][y] != null)
                    {
                        g.setColor(features[x][y].isTown()
                                ? new Color(255, 220, 50)   // gold for towns
                                : new Color(180, 80, 220)); // purple for dungeons
                        g.fillOval(x * CELL + 3, y * CELL + 3,
                                CELL - 6, CELL - 6);
                    }

                    // Start position marker
                    if (startPos.x() == x && startPos.y() == y)
                    {
                        g.setColor(Color.WHITE);
                        g.drawString("S", x * CELL + 2, y * CELL + CELL - 2);
                    }
                }
            }

            // Grid lines when cells are large enough to read
            if (CELL >= 10)
            {
                g.setColor(new Color(0, 0, 0, 60));
                for (int x = 0; x <= mapW; x++)
                    g.drawLine(x * CELL, 0, x * CELL, mapH * CELL);
                for (int y = 0; y <= mapH; y++)
                    g.drawLine(0, y * CELL, mapW * CELL, y * CELL);
            }
        }
    }
}