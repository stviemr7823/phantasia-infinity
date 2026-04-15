// phantasia-editor/src/main/java/com/phantasia/editor/panels/MapGeneratorPanel.java
package com.phantasia.editor.panels;

import com.phantasia.core.world.*;
import com.phantasia.editor.*;
import com.phantasia.editor.generators.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * Unified procedural map generator panel.
 *
 * Three tabs: World Map, Town Layout, Dungeon Floor.
 * Each tab has parameter controls and a visual preview canvas.
 * "Generate" produces a preview; "Accept" commits to EditorState.
 */
public class MapGeneratorPanel extends JPanel implements EditorFrame.WorkspaceTab {

    private static final int CELL = 8;  // preview cell size

    // ── Current generation results ───────────────────────────────────────
    private WorldMapGenerator.Result worldResult;
    private InteriorMap              townResult;
    private DungeonFloor             dungeonResult;

    private final PreviewCanvas      preview = new PreviewCanvas();
    private String                   activeTab = "world";

    public MapGeneratorPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("World Map",    buildWorldTab());
        tabs.addTab("Town Layout",  buildTownTab());
        tabs.addTab("Dungeon Floor", buildDungeonTab());
        tabs.addChangeListener(e -> {
            activeTab = switch (tabs.getSelectedIndex()) {
                case 0 -> "world";
                case 1 -> "town";
                case 2 -> "dungeon";
                default -> "world";
            };
            preview.repaint();
        });

        JScrollPane previewScroll = new JScrollPane(preview);
        previewScroll.getViewport().setBackground(new Color(10, 8, 18));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabs, previewScroll);
        split.setDividerLocation(300);
        split.setResizeWeight(0.0);

        add(split, BorderLayout.CENTER);
    }

    @Override
    public String getTabKey() { return "generator:maps"; }

    // =====================================================================
    // World Map tab
    // =====================================================================

    private JTextField wWidthField, wHeightField, wTownsField, wDungeonsField, wSeedField;
    private JSlider    wLandSlider, wForestSlider, wMountainSlider;

    private JPanel buildWorldTab() {
        JPanel p = new JPanel(new GridLayout(0, 1, 4, 2));
        p.setBorder(new EmptyBorder(8, 8, 8, 8));

        wWidthField    = addField(p, "Width",     "64");
        wHeightField   = addField(p, "Height",    "64");
        wTownsField    = addField(p, "Towns",     "4");
        wDungeonsField = addField(p, "Dungeons",  "2");
        wSeedField     = addField(p, "Seed (-1=random)", "-1");
        wLandSlider    = addSlider(p, "Land Mass %",   20, 70, 45);
        wForestSlider  = addSlider(p, "Forest %",       5, 40, 20);
        wMountainSlider= addSlider(p, "Mountain %",     5, 30, 12);

        JButton genBtn    = EditorUtils.accentButton("Generate World");
        JButton acceptBtn = EditorUtils.button("Accept → EditorState");
        genBtn.addActionListener(e -> generateWorld());
        acceptBtn.addActionListener(e -> acceptWorld());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        btns.setOpaque(false);
        btns.add(genBtn);
        btns.add(acceptBtn);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(p, BorderLayout.NORTH);
        wrapper.add(btns, BorderLayout.SOUTH);
        return wrapper;
    }

    private void generateWorld() {
        WorldMapGenerator.Config cfg = new WorldMapGenerator.Config(
                parseInt(wWidthField, 64), parseInt(wHeightField, 64),
                wLandSlider.getValue() / 100.0,
                parseInt(wTownsField, 4), parseInt(wDungeonsField, 2),
                wForestSlider.getValue() / 100.0,
                wMountainSlider.getValue() / 100.0,
                0.08,
                Long.parseLong(wSeedField.getText().trim())
        );
        worldResult = WorldMapGenerator.generate(cfg);
        activeTab = "world";
        preview.setPreferredSize(new Dimension(
                worldResult.width() * CELL, worldResult.height() * CELL));
        preview.revalidate();
        preview.repaint();
    }

    private void acceptWorld() {
        if (worldResult == null) { EditorUtils.info(this, "Generate a world first."); return; }
        // Build a WorldMap from the result and set it in EditorState
        // (WorldMap's constructor is package-private, so we store terrain/features
        //  for baking via WorldMapBaker — the EditorState world map setter handles this)
        EditorState state = EditorState.get();
        try {
            // Bake to a temp file and reload — roundtrip ensures compatibility
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("phantasia_gen_", ".map");
            WorldMapBaker.bake(tmp.toString(),
                    worldResult.width(), worldResult.height(),
                    worldResult.startPos().x(), worldResult.startPos().y(),
                    worldResult.terrain(), worldResult.features());
            WorldMap wm = WorldMap.loadFromFile(tmp.toString());
            state.setWorldMap(wm);
            state.markDirty("worldMap");
            java.nio.file.Files.deleteIfExists(tmp);
            EditorUtils.info(this,
                    "World map accepted: " + worldResult.width() + "×" + worldResult.height()
                            + ", " + worldResult.towns().size() + " towns, "
                            + worldResult.dungeons().size() + " dungeons.");
        } catch (Exception ex) {
            EditorUtils.error(this, "Failed to accept world: " + ex.getMessage());
        }
    }

    // =====================================================================
    // Town Layout tab
    // =====================================================================

    private JTextField tWidthField, tHeightField, tNameField, tSeedField;
    private JSlider    tDensitySlider;

    private JPanel buildTownTab() {
        JPanel p = new JPanel(new GridLayout(0, 1, 4, 2));
        p.setBorder(new EmptyBorder(8, 8, 8, 8));

        tNameField    = addField(p, "Town Name",  "New Town");
        tWidthField   = addField(p, "Width",      "24");
        tHeightField  = addField(p, "Height",     "20");
        tSeedField    = addField(p, "Seed (-1=random)", "-1");
        tDensitySlider = addSlider(p, "Building Density %", 20, 80, 50);

        JButton genBtn    = EditorUtils.accentButton("Generate Town");
        JButton acceptBtn = EditorUtils.button("Accept → EditorState");
        genBtn.addActionListener(e -> generateTown());
        acceptBtn.addActionListener(e -> acceptTown());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        btns.setOpaque(false);
        btns.add(genBtn);
        btns.add(acceptBtn);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(p, BorderLayout.NORTH);
        wrapper.add(btns, BorderLayout.SOUTH);
        return wrapper;
    }

    private void generateTown() {
        int w = parseInt(tWidthField, 24);
        int h = parseInt(tHeightField, 20);
        long seed = Long.parseLong(tSeedField.getText().trim());
        double density = tDensitySlider.getValue() / 100.0;
        int id = EditorState.get().nextInteriorMapId();

        townResult = TownLayoutGenerator.generate(id, tNameField.getText().trim(),
                w, h, density, seed);
        activeTab = "town";
        preview.setPreferredSize(new Dimension(w * CELL * 2, h * CELL * 2));
        preview.revalidate();
        preview.repaint();
    }

    private void acceptTown() {
        if (townResult == null) { EditorUtils.info(this, "Generate a town first."); return; }
        EditorState state = EditorState.get();
        state.putInteriorMap(townResult);
        state.markDirty("interiorMap:" + townResult.getId());
        EditorUtils.info(this, "Town '" + townResult.getName() + "' accepted ("
                + townResult.getWidth() + "×" + townResult.getHeight() + ").");
    }

    // =====================================================================
    // Dungeon Floor tab
    // =====================================================================

    private JTextField dWidthField, dHeightField, dRoomsField, dSeedField;
    private JSlider    dTrapSlider;
    private JCheckBox  dDoorsBox, dSecretBox;
    private JComboBox<String> dPresetBox;

    private JPanel buildDungeonTab() {
        JPanel p = new JPanel(new GridLayout(0, 1, 4, 2));
        p.setBorder(new EmptyBorder(8, 8, 8, 8));

        dPresetBox = new JComboBox<>(new String[]{"Custom", "Small (24×18)", "Default (40×30)", "Large (55×40)"});
        EditorUtils.addFormRow(p, "Preset", dPresetBox);

        dWidthField  = addField(p, "Width",     "40");
        dHeightField = addField(p, "Height",    "30");
        dRoomsField  = addField(p, "Max Rooms", "10");
        dSeedField   = addField(p, "Seed (-1=random)", "-1");
        dTrapSlider  = addSlider(p, "Trap Density %", 0, 30, 10);
        dDoorsBox    = new JCheckBox("Place Doors", true);
        dSecretBox   = new JCheckBox("Secret Rooms", true);
        p.add(dDoorsBox);
        p.add(dSecretBox);

        dPresetBox.addActionListener(e -> applyDungeonPreset());

        JButton genBtn    = EditorUtils.accentButton("Generate Dungeon");
        JButton acceptBtn = EditorUtils.button("Accept → EditorState");
        genBtn.addActionListener(e -> generateDungeon());
        acceptBtn.addActionListener(e -> acceptDungeon());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        btns.setOpaque(false);
        btns.add(genBtn);
        btns.add(acceptBtn);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(p, BorderLayout.NORTH);
        wrapper.add(btns, BorderLayout.SOUTH);
        return wrapper;
    }

    private void applyDungeonPreset() {
        switch (dPresetBox.getSelectedIndex()) {
            case 1 -> { dWidthField.setText("24"); dHeightField.setText("18"); dRoomsField.setText("6"); }
            case 2 -> { dWidthField.setText("40"); dHeightField.setText("30"); dRoomsField.setText("10"); }
            case 3 -> { dWidthField.setText("55"); dHeightField.setText("40"); dRoomsField.setText("16"); }
        }
    }

    private void generateDungeon() {
        EnhancedDungeonGenerator.Config cfg = new EnhancedDungeonGenerator.Config(
                parseInt(dWidthField, 40), parseInt(dHeightField, 30),
                parseInt(dRoomsField, 10),
                4, 8, 1,
                dTrapSlider.getValue() / 100.0,
                0.5,
                dDoorsBox.isSelected(),
                dSecretBox.isSelected(),
                Long.parseLong(dSeedField.getText().trim())
        );
        dungeonResult = EnhancedDungeonGenerator.generate(cfg);
        activeTab = "dungeon";
        preview.setPreferredSize(new Dimension(
                cfg.width() * CELL * 2, cfg.height() * CELL * 2));
        preview.revalidate();
        preview.repaint();
    }

    private void acceptDungeon() {
        if (dungeonResult == null) { EditorUtils.info(this, "Generate a dungeon first."); return; }
        // Convert DungeonFloor to an InteriorMap for EditorState
        int w = dungeonResult.getWidth(), h = dungeonResult.getHeight();
        int id = EditorState.get().nextInteriorMapId();
        InteriorMap map = new InteriorMap(id, "Generated Dungeon", InteriorMapType.DUNGEON,
                w, h, "dungeon_stone");
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                map.setTile(x, y, dungeonResult.getTile(x, y).ordinal());

        EditorState state = EditorState.get();
        state.putInteriorMap(map);
        state.markDirty("interiorMap:" + id);
        EditorUtils.info(this, "Dungeon floor accepted (" + w + "×" + h + ").");
    }

    // =====================================================================
    // Preview canvas
    // =====================================================================

    private static final Map<TileType, Color> TILE_COLORS = new EnumMap<>(TileType.class);
    static {
        TILE_COLORS.put(TileType.OCEAN,    new Color(25,  50, 110));
        TILE_COLORS.put(TileType.PLAINS,   new Color(75, 125,  55));
        TILE_COLORS.put(TileType.ROAD,     new Color(155, 135, 85));
        TILE_COLORS.put(TileType.FOREST,   new Color(25,  75,  25));
        TILE_COLORS.put(TileType.MOUNTAIN, new Color(115, 105, 85));
        TILE_COLORS.put(TileType.SWAMP,    new Color(55,  85,  55));
        TILE_COLORS.put(TileType.TOWN,     new Color(200, 180, 90));
        TILE_COLORS.put(TileType.DUNGEON,  new Color(150,  60, 60));
    }

    private static final Color DNG_WALL    = new Color(40,  35,  50);
    private static final Color DNG_FLOOR   = new Color(90,  85, 100);
    private static final Color DNG_DOOR    = new Color(140, 100,  50);
    private static final Color DNG_STAIRS  = new Color(100, 200, 100);
    private static final Color DNG_CHEST   = new Color(218, 175,  62);
    private static final Color DNG_TRAP    = new Color(200,  60,  60);

    // Town tile preview colors
    private static final Color[] TOWN_COLORS = {
            new Color(120, 115, 130),  // 0 stone floor
            new Color(140, 110,  70),  // 1 wood floor
            new Color(55,   48,  65),  // 2 wall
            new Color(140, 100,  50),  // 3 door
            new Color(100,  80,  50),  // 4 counter
            new Color(200, 100,  30),  // 5 hearth
            new Color(180, 150,  60),  // 6 market stall
            new Color(100,  85,  60),  // 7 crate
            new Color(50,  130,  50),  // 8 plant
            new Color(80,  140, 180),  // 9 well
            new Color(200, 200, 100),  // 10 exit
            new Color(155, 135,  85),  // 11 road
            new Color(130,  50,  50),  // 12 carpet
            new Color(80,   60,  40),  // 13 bookshelf
            new Color(100,  80,  60),  // 14 bed
            new Color(110,  90,  55),  // 15 table
    };

    private class PreviewCanvas extends JPanel {

        PreviewCanvas() {
            setBackground(new Color(10, 8, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            switch (activeTab) {
                case "world"   -> paintWorld(g2);
                case "town"    -> paintTown(g2);
                case "dungeon" -> paintDungeon(g2);
            }
        }

        private void paintWorld(Graphics2D g) {
            if (worldResult == null) { drawHint(g, "Click 'Generate World' to preview"); return; }
            var t = worldResult.terrain();
            int w = worldResult.width(), h = worldResult.height();
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    g.setColor(TILE_COLORS.getOrDefault(t[x][y], Color.BLACK));
                    g.fillRect(x * CELL, y * CELL, CELL, CELL);
                }
            }
            // Town/dungeon labels
            g.setFont(new Font("SansSerif", Font.BOLD, 9));
            for (var town : worldResult.towns()) {
                g.setColor(Color.WHITE);
                g.drawString(town.name(), town.x() * CELL + 2, town.y() * CELL - 2);
            }
            for (var dng : worldResult.dungeons()) {
                g.setColor(new Color(255, 150, 150));
                g.drawString(dng.name(), dng.x() * CELL + 2, dng.y() * CELL - 2);
            }
        }

        private void paintTown(Graphics2D g) {
            if (townResult == null) { drawHint(g, "Click 'Generate Town' to preview"); return; }
            int cs = CELL * 2;  // larger cells for towns
            for (int x = 0; x < townResult.getWidth(); x++) {
                for (int y = 0; y < townResult.getHeight(); y++) {
                    int idx = townResult.getTile(x, y);
                    Color c = (idx >= 0 && idx < TOWN_COLORS.length) ? TOWN_COLORS[idx] : Color.DARK_GRAY;
                    g.setColor(c);
                    g.fillRect(x * cs, y * cs, cs, cs);
                }
            }
            // Grid
            g.setColor(new Color(0, 0, 0, 40));
            for (int x = 0; x <= townResult.getWidth(); x++)
                g.drawLine(x * cs, 0, x * cs, townResult.getHeight() * cs);
            for (int y = 0; y <= townResult.getHeight(); y++)
                g.drawLine(0, y * cs, townResult.getWidth() * cs, y * cs);
        }

        private void paintDungeon(Graphics2D g) {
            if (dungeonResult == null) { drawHint(g, "Click 'Generate Dungeon' to preview"); return; }
            int cs = CELL * 2;
            int w = dungeonResult.getWidth(), h = dungeonResult.getHeight();
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    DungeonFloor.TileType t = dungeonResult.getTile(x, y);
                    Color c = switch (t) {
                        case WALL       -> DNG_WALL;
                        case FLOOR      -> DNG_FLOOR;
                        case DOOR       -> DNG_DOOR;
                        case STAIRS_UP, STAIRS_DOWN -> DNG_STAIRS;
                        case CHEST      -> DNG_CHEST;
                        case TRAP       -> DNG_TRAP;
                        case VOID       -> Color.BLACK;
                    };
                    g.setColor(c);
                    g.fillRect(x * cs, y * cs, cs, cs);
                }
            }
        }

        private void drawHint(Graphics2D g, String text) {
            g.setColor(EditorTheme.TEXT_DIM);
            g.setFont(EditorTheme.FONT_HEADER);
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(text);
            g.drawString(text, (getWidth() - tw) / 2, getHeight() / 2);
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private JTextField addField(JPanel parent, String label, String initial) {
        JTextField f = EditorUtils.field(8);
        f.setText(initial);
        EditorUtils.addFormRow(parent, label, f);
        return f;
    }

    private JSlider addSlider(JPanel parent, String label, int min, int max, int value) {
        JSlider s = new JSlider(min, max, value);
        s.setMajorTickSpacing((max - min) / 4);
        s.setPaintTicks(true);
        s.setPaintLabels(true);
        s.setOpaque(false);
        s.setForeground(EditorTheme.TEXT_DIM);
        EditorUtils.addFormRow(parent, label, s);
        return s;
    }

    private int parseInt(JTextField f, int fallback) {
        try { return Integer.parseInt(f.getText().trim()); }
        catch (NumberFormatException e) { return fallback; }
    }
}