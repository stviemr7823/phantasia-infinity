// phantasia-j2d/src/main/java/com/phantasia/j2d/tour/TourFrame.java
package com.phantasia.j2d.tour;

import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.SaveManager;
import com.phantasia.core.logic.WorldEvent;
import com.phantasia.core.world.WorldMap;
import com.phantasia.core.world.WorldPosition;
import com.phantasia.core.world.DungeonFloor;
import com.phantasia.core.world.DungeonFloorGenerator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import com.phantasia.j2d.tour.TourSettings;
import com.phantasia.j2d.tour.TourSettingsDialog;
import java.io.IOException;

/**
 * Top-level window for the Phantasia Touring Engine.
 *
 * ARCHITECTURE — single-threaded, all on the EDT:
 *
 *   A javax.swing.Timer fires at 30fps and calls mapPanel.tick() which
 *   updates the party actor lerp and repaints. No daemon threads, no
 *   BufferStrategy, no race conditions.
 *
 *   When a WorldEvent fires from MapPanel:
 *     1. The timer is stopped.
 *     2. The appropriate card is initialised and shown.
 *     3. Swing button clicks drive everything from there.
 *     4. When the player leaves, the timer restarts and MAP_CARD is shown.
 *
 * LAYOUT:
 *
 *   +-------------------------------------+------------------+
 *   |                                     |                  |
 *   |   CARD PANEL  (CardLayout)          |  EVENT LOG       |
 *   |                                     |  (always         |
 *   |   MAP / TOWN / COMBAT / DUNGEON     |   visible)       |
 *   |                                     |                  |
 *   +-------------------------------------+------------------+
 *   |  STATUS BAR                                            |
 *   +--------------------------------------------------------+
 */
public class TourFrame extends JFrame {

    // Card names
    public static final String MAP_CARD     = "MAP";
    public static final String TOWN_CARD    = "TOWN";
    public static final String COMBAT_CARD  = "COMBAT";
    public static final String DUNGEON_CARD = "DUNGEON";

    // Sizing
    static final int WINDOW_W  = 1380;
    static final int WINDOW_H  =  760;
    static final int LOG_W     =  300;

    // Colours shared across panels
    static final Color C_BG        = new Color(15, 13, 20);
    static final Color C_PANEL     = new Color(28, 24, 36);
    static final Color C_BORDER    = new Color(65, 55, 80);
    static final Color C_ACCENT    = new Color(190, 145, 55);
    static final Color C_TEXT      = new Color(215, 205, 185);
    static final Color C_DIM       = new Color(120, 112, 95);
    static final Color C_GREEN     = new Color( 70, 200,  85);
    static final Color C_RED       = new Color(220,  75,  70);
    static final Color C_BLUE      = new Color( 85, 145, 220);
    static final Color C_PURPLE    = new Color(155,  85, 210);
    static final Color C_ORANGE    = new Color(225, 130,  40);

    static final Font F_TITLE   = new Font(Font.SERIF,      Font.BOLD,  20);
    static final Font F_BODY    = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    static final Font F_SMALL   = new Font(Font.MONOSPACED, Font.PLAIN, 11);
    static final Font F_BUTTON  = new Font(Font.SANS_SERIF, Font.BOLD,  13);
    static final Font F_LABEL   = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    // -------------------------------------------------------------------------

    private final WorldMap    worldMap;
    private final GameSession session;

    private final CardLayout  cardLayout = new CardLayout();
    private final JPanel      cardPanel  = new JPanel(cardLayout);

    // Panels
    final MapPanel      mapPanel;
    final TownPanel     townPanel;
    final CombatPanel   combatPanel;
    final DungeonPanel  dungeonPanel;
    final TourEventLog  eventLog;

    // Game timer — drives mapPanel.tick() at 30fps on the EDT
    private final Timer gameTimer;

    // Status bar labels
    private final JLabel lblPos   = statusLabel();
    private final JLabel lblTile  = statusLabel();
    private final JLabel lblGold  = statusLabel();
    private final JLabel lblParty = statusLabel();

    private String activeCard = MAP_CARD;

    // -------------------------------------------------------------------------

    public TourFrame(WorldMap worldMap, GameSession session) {
        super("Phantasia  ·  Touring Engine  ["
                + worldMap.getWidth() + "×" + worldMap.getHeight() + "]");

        this.worldMap = worldMap;
        this.session  = session;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(WINDOW_W, WINDOW_H);
        setLocationRelativeTo(null);
        setResizable(true);

        // Build shared event log
        eventLog = new TourEventLog();

        // Build panels
        mapPanel     = new MapPanel(worldMap, session, this);
        townPanel    = new TownPanel(session, this);
        combatPanel  = new CombatPanel(session, this);
        dungeonPanel = new DungeonPanel(session, this);

        cardPanel.add(mapPanel,     MAP_CARD);
        cardPanel.add(townPanel,    TOWN_CARD);
        cardPanel.add(combatPanel,  COMBAT_CARD);
        cardPanel.add(dungeonPanel, DUNGEON_CARD);

        // Event log sidebar
        JPanel logSidebar = buildLogSidebar();

        // Status bar
        JPanel statusBar = buildStatusBar();

        // Root
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(C_BG);
        root.add(cardPanel,   BorderLayout.CENTER);
        root.add(logSidebar,  BorderLayout.EAST);
        root.add(statusBar,   BorderLayout.SOUTH);
        setContentPane(root);

        setJMenuBar(buildMenuBar());

        // 30fps timer — only ticks the map panel when map is active
        gameTimer = new Timer(33, e -> {
            if (MAP_CARD.equals(activeCard)) {
                mapPanel.tick();
                mapPanel.repaint();
            } else if (DUNGEON_CARD.equals(activeCard)) {
                dungeonPanel.tick();
            }
        });


        refreshStatus();
        showMap();                 // show map card and start timer
        mapPanel.requestFocusInWindow();
    }

    // -------------------------------------------------------------------------
    // Screen transitions — all called on EDT
    // -------------------------------------------------------------------------

    /** Show map and start game timer. */
    public void showMap() {
        activeCard = MAP_CARD;
        cardLayout.show(cardPanel, MAP_CARD);
        gameTimer.start();
        SwingUtilities.invokeLater(mapPanel::requestFocusInWindow);
        refreshStatus();
    }
    /** Route a WorldEvent to the correct screen. */
    public void handleWorldEvent(WorldEvent event) {
        gameTimer.stop();

        switch (event) {
            case WorldEvent.EnterTown t -> {
                eventLog.log("TOWN", t.name() + "  id=" + t.id());
                townPanel.open(t.id(), t.name());
                cardLayout.show(cardPanel, TOWN_CARD);
            }
            case WorldEvent.EnterDungeon d -> {
                eventLog.log("DUNGEON", d.name() + "  id=" + d.id());

                // Generate a dungeon floor (50x50, up to 12 rooms)
                DungeonFloor floor = DungeonFloorGenerator.generate(50, 50, 12);

                // Hand the floor to the panel — this sets player position,
                // reveals starting area, and stores floor on session
                dungeonPanel.enter(d.id(), d.name(), floor);

                // Show the dungeon card and start the timer for it
                activeCard = DUNGEON_CARD;
                cardLayout.show(cardPanel, DUNGEON_CARD);
                gameTimer.start();

                SwingUtilities.invokeLater(dungeonPanel::requestFocusInWindow);
            }
            case WorldEvent.RandomEncounter ignored -> {
                if (TourSettings.get().isAutoWin()) {
                    eventLog.log("COMBAT", "Auto-win: encounter skipped.");
                    gameTimer.start();
                    return;
                }
                WorldPosition pos = session.getWorldPosition();
                String tile = worldMap.getTile(pos).getType().name();
                eventLog.log("ENCOUNTER", "Random  at " + pos + "  [" + tile + "]");
                combatPanel.startRandom();
                cardLayout.show(cardPanel, COMBAT_CARD);
            }
            case WorldEvent.ScriptedBattle s -> {
                eventLog.log("ENCOUNTER", "Scripted: " + s.count()
                        + "x " + s.monsterName());
                combatPanel.startScripted(s.monsterName(), s.count());
                cardLayout.show(cardPanel, COMBAT_CARD);
            }
            case WorldEvent.TileEventPrompt p -> {
                eventLog.log("TILE", p.tileEvent().description);
                handleTilePrompt(p);
                gameTimer.start();   // tile events don't switch cards
            }
            case WorldEvent.NpcInteraction npcInteraction ->
            {
            }
        }
        refreshStatus();
    }


    // -------------------------------------------------------------------------
    // Status & save
    // -------------------------------------------------------------------------

    public void refreshStatus() {
        WorldPosition pos  = session.getWorldPosition();
        String tileType    = worldMap.inBounds(pos.x(), pos.y())
                ? worldMap.getTile(pos).getType().name() : "?";
        long alive = session.getParty().stream().filter(p -> p.isAlive()).count();

        lblPos  .setText("  " + pos);
        lblTile .setText("  " + tileType);
        lblGold .setText("  " + session.getLedger().getPartyGold() + " gp");
        lblParty.setText("  " + alive + "/" + session.getParty().size() + " alive");
    }

    public void saveSession() {
        try {
            SaveManager.save(session, TourMain.TOUR_SAVE_PATH);
            eventLog.log("SAVE", "Saved to " + TourMain.TOUR_SAVE_PATH);
        } catch (IOException e) {
            eventLog.log("ERROR", "Save failed: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Save failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void newSession() {
        if (JOptionPane.showConfirmDialog(this,
                "Start a new session? Progress will be lost.",
                "New Session", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
            return;
        session.setPosition(worldMap.getStartPosition());
        // Restore party HP/MP for a clean start
        session.getParty().forEach(pc -> {
            pc.setStat(com.phantasia.core.model.Stat.HP,
                    pc.getStat(com.phantasia.core.model.Stat.MAX_HP));
            pc.setStat(com.phantasia.core.model.Stat.MAGIC_POWER,
                    pc.getStat(com.phantasia.core.model.Stat.MAX_MAGIC));
        });
        eventLog.log("SESSION", "New session — start: " + worldMap.getStartPosition());
        returnToMap("New session");
    }

    // -------------------------------------------------------------------------
    // Tile event prompt (modal dialog, no card switch)
    // -------------------------------------------------------------------------

    private void handleTilePrompt(WorldEvent.TileEventPrompt p) {
        var ev = p.tileEvent();
        int choice = JOptionPane.showConfirmDialog(this,
                ev.description + "\n\n" + ev.prompt,
                "Event", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            eventLog.log("TILE", "YES → " + ev.yesOutcome.type);
            ev.resolve();
        } else {
            eventLog.log("TILE", "NO → " + ev.noOutcome.type);
        }
    }

    // -------------------------------------------------------------------------
    // UI builders
    // -------------------------------------------------------------------------

    private JPanel buildLogSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 0));
        sidebar.setPreferredSize(new Dimension(LOG_W, 0));
        sidebar.setBackground(C_BG);
        sidebar.setBorder(new MatteBorder(0, 1, 0, 0, C_BORDER));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(22, 19, 28));
        header.setBorder(new EmptyBorder(6, 10, 6, 8));
        JLabel title = new JLabel("EVENT LOG");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        title.setForeground(C_ACCENT);
        JButton clear = new JButton("Clear");
        clear.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        clear.setMargin(new Insets(1, 5, 1, 5));
        clear.addActionListener(e -> eventLog.clear());
        header.add(title, BorderLayout.WEST);
        header.add(clear, BorderLayout.EAST);

        // Save button
        JButton save = new JButton("💾  Save Session  (Ctrl+S)");
        save.setFont(F_BUTTON);
        save.setBackground(new Color(50, 42, 18));
        save.setForeground(C_ACCENT);
        save.setFocusPainted(false);
        save.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, C_BORDER),
                new EmptyBorder(8, 10, 8, 10)));
        save.addActionListener(e -> saveSession());

        sidebar.add(header,    BorderLayout.NORTH);
        sidebar.add(eventLog,  BorderLayout.CENTER);
        sidebar.add(save,      BorderLayout.SOUTH);
        return sidebar;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        bar.setBackground(new Color(20, 18, 26));
        bar.setPreferredSize(new Dimension(0, 26));
        bar.setBorder(new MatteBorder(1, 0, 0, 0, C_BORDER));
        for (JLabel lbl : new JLabel[]{ lblPos, lblTile, lblGold, lblParty })
            bar.add(lbl);
        return bar;
    }

    private JLabel statusLabel() {
        JLabel l = new JLabel("  —");
        l.setFont(F_SMALL);
        l.setForeground(C_DIM);
        l.setBorder(new MatteBorder(0, 0, 0, 1, C_BORDER));
        l.setPreferredSize(new Dimension(180, 18));
        return l;
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.setBackground(C_BG);
        bar.setBorder(new MatteBorder(0, 0, 1, 0, C_BORDER));

        // ---- File ----
        JMenu file = new JMenu("File");

        JMenuItem save = new JMenuItem("Save Session");
        save.setAccelerator(KeyStroke.getKeyStroke("ctrl S"));
        save.addActionListener(e -> saveSession());

        JMenuItem newS = new JMenuItem("New Session");
        newS.addActionListener(e -> newSession());

        JMenuItem quit = new JMenuItem("Quit");
        quit.addActionListener(e -> { saveSession(); System.exit(0); });

        file.add(save); file.add(newS); file.addSeparator(); file.add(quit);
        bar.add(file);

        // ---- View ----
        JMenu view = new JMenu("View");
        JMenuItem toMap = new JMenuItem("Return to Map");
        toMap.addActionListener(e -> returnToMap("Manual"));
        view.add(toMap);
        bar.add(view);

        // ---- Settings ----
        JMenu settings = new JMenu("Settings");

        JMenuItem openDialog = new JMenuItem("Open Settings…");
        openDialog.setAccelerator(KeyStroke.getKeyStroke("ctrl COMMA"));
        openDialog.addActionListener(e -> openSettingsDialog());
        settings.add(openDialog);

        settings.addSeparator();

        // Quick toggles — always accessible from the menu bar
        TourSettings s = TourSettings.get();

        JCheckBoxMenuItem encToggle = new JCheckBoxMenuItem(
                "Random Encounters", s.isEncountersEnabled());
        encToggle.addActionListener(e -> {
            s.setEncountersEnabled(encToggle.isSelected());
            eventLog.log("SETTINGS", "Encounters "
                    + (encToggle.isSelected() ? "ON" : "OFF"));
        });
        s.addPropertyChangeListener("encountersEnabled", evt ->
                encToggle.setSelected((boolean) evt.getNewValue()));

        JCheckBoxMenuItem autoWinToggle = new JCheckBoxMenuItem(
                "Auto-Win Battles", s.isAutoWin());
        autoWinToggle.addActionListener(e -> {
            s.setAutoWin(autoWinToggle.isSelected());
            eventLog.log("SETTINGS", "Auto-win "
                    + (autoWinToggle.isSelected() ? "ON" : "OFF"));
        });
        s.addPropertyChangeListener("autoWin", evt ->
                autoWinToggle.setSelected((boolean) evt.getNewValue()));

        JCheckBoxMenuItem godToggle = new JCheckBoxMenuItem(
                "God Mode", s.isGodMode());
        godToggle.addActionListener(e -> {
            s.setGodMode(godToggle.isSelected());
            eventLog.log("SETTINGS", "God mode "
                    + (godToggle.isSelected() ? "ON" : "OFF"));
        });
        s.addPropertyChangeListener("godMode", evt ->
                godToggle.setSelected((boolean) evt.getNewValue()));

        JCheckBoxMenuItem fogToggle = new JCheckBoxMenuItem(
                "Dungeon Fog of War", s.isFogOfWarEnabled());
        fogToggle.addActionListener(e -> {
            s.setFogOfWarEnabled(fogToggle.isSelected());
            eventLog.log("SETTINGS", "Fog of war "
                    + (fogToggle.isSelected() ? "ON" : "OFF"));
        });
        s.addPropertyChangeListener("fogOfWarEnabled", evt ->
                fogToggle.setSelected((boolean) evt.getNewValue()));

        settings.add(encToggle);
        settings.add(autoWinToggle);
        settings.add(godToggle);
        settings.addSeparator();
        settings.add(fogToggle);

        bar.add(settings);

        // ---- Cheats ----
        JMenu cheats = new JMenu("Cheats");

        JMenuItem restoreHp = new JMenuItem("Restore All HP/MP");
        restoreHp.addActionListener(e -> {
            session.getParty().forEach(pc -> {
                if (!pc.isAlive()) return;
                pc.setStat(com.phantasia.core.model.Stat.HP,
                        pc.getStat(com.phantasia.core.model.Stat.MAX_HP));
                pc.setStat(com.phantasia.core.model.Stat.MAGIC_POWER,
                        pc.getStat(com.phantasia.core.model.Stat.MAX_MAGIC));
            });
            refreshStatus();
            eventLog.log("CHEATS", "All HP/MP restored.");
        });

        JMenuItem addGold = new JMenuItem("Add 1000 Gold");
        addGold.addActionListener(e -> {
            session.getLedger().addGold(1000);
            refreshStatus();
            eventLog.log("CHEATS", "+1000 gold → "
                    + session.getLedger().getPartyGold() + " gp");
        });

        JMenuItem forceEncounter = new JMenuItem("Force Random Encounter");
        forceEncounter.addActionListener(e -> {
            if (!MAP_CARD.equals(activeCard) && !DUNGEON_CARD.equals(activeCard))
                return;
            eventLog.log("CHEATS", "Forced encounter.");
            gameTimer.stop();
            combatPanel.startRandom();
            activeCard = COMBAT_CARD;
            cardLayout.show(cardPanel, COMBAT_CARD);
        });

        cheats.add(restoreHp);
        cheats.add(addGold);
        cheats.addSeparator();
        cheats.add(forceEncounter);

        bar.add(cheats);

        return bar;
    }

    private void openSettingsDialog() {
        TourSettingsDialog dialog = new TourSettingsDialog(this);
        dialog.setVisible(true);
    }


        public void triggerDungeonEncounter() {
            gameTimer.stop();
            WorldPosition pos = session.getWorldPosition();
            eventLog.log("ENCOUNTER", "Dungeon encounter at " + pos);
            combatPanel.startRandom();
            activeCard = COMBAT_CARD;
            cardLayout.show(cardPanel, COMBAT_CARD);
        }
        public void returnToMap(String reason) {
            eventLog.log("MAP", reason);

            // If we're still in a dungeon (combat ended but dungeon is active),
            // return to the dungeon panel instead of the map
            if (session.getDungeonFloor() != null) {
                activeCard = DUNGEON_CARD;
                cardLayout.show(cardPanel, DUNGEON_CARD);
                gameTimer.start();
                SwingUtilities.invokeLater(dungeonPanel::requestFocusInWindow);
                refreshStatus();
            } else {
                showMap();
            }
        }
}