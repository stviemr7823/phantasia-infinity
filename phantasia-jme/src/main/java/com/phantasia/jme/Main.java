// phantasia-jme/src/main/java/com/phantasia/jme/Main.java
package com.phantasia.jme;

import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import com.phantasia.core.data.DataPaths;
import com.phantasia.core.data.EncounterFactory;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.SaveManager;
import com.phantasia.core.world.WorldMap;
import com.phantasia.jme.states.CombatState;
import com.phantasia.jme.states.FloatingTextState;
import com.phantasia.jme.states.HUDState;
import com.phantasia.jme.states.VisualBridgeAppState;
import com.phantasia.jme.states.WorldState;

import java.io.IOException;

/**
 * Application entry point and GameSession owner.
 *
 * FIX APPLIED (Fix 5 — hardcoded start position):
 *   The previous code used a hardcoded constant DEFAULT_START = (13, 10)
 *   as the "Pendragon Start". That coordinate appears in two places:
 *     1. Baked into the .map file header via WorldMapBaker (correct home)
 *     2. Here in Main as a literal fallback (wrong — now removed)
 *
 *   New game sessions now use worldMap.getStartPosition(), which reads
 *   the coordinate from the map file's header — the single source of truth.
 *   The magic number (13, 10) now lives only in the WorldMapBaker authoring
 *   tool, not in runtime code.
 *
 *   For the combat-demo fallback (no world.dat), we use a sentinel
 *   WorldPosition(0, 0) since no map is loaded and position is irrelevant.
 *
 * STARTUP SEQUENCE (unchanged):
 *   1. Attempt to load a saved game from GAME.DAT.
 *   2. If no save exists, generate a fresh party and start at the
 *      map's start position (from the .map header).
 *   3. Load the world map from world.dat (or fall back to combat demo).
 *   4. Attach all AppStates, passing the session to each.
 */
public class Main extends SimpleApplication {

    // -------------------------------------------------------------------------
    // File paths
    // -------------------------------------------------------------------------

    private static final String SAVE_PATH = DataPaths.DAT_DIR + "/GAME.DAT";
    private static final String MAP_PATH  = DataPaths.DAT_DIR + "/world.dat";

    // -------------------------------------------------------------------------
    // Session — owned here, passed by reference to every AppState
    // -------------------------------------------------------------------------

    private GameSession session;
    private WorldMap    worldMap;

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        Main app = new Main();
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Phantasia: Infinity");
        settings.setResolution(1280, 720);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    // -------------------------------------------------------------------------
    // SimpleApplication lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void simpleInitApp() {
        flyCam.setEnabled(false);

        // --- 1. Load the world map first so we can read its start position ---
        worldMap = loadWorldMap();

        // --- 2. Load or create the session ---
        //   Pass worldMap so newGame() can use worldMap.getStartPosition().
        //   If no map loaded, fall back to (0,0) — position is irrelevant
        //   in the combat-demo fallback mode.
        session = loadOrCreateSession();

        // --- 3. Launch the appropriate mode ---
        if (worldMap != null) {
            launchWorldMode();
        } else {
            System.out.println("[Main] No world map found — launching combat demo.");
            launchCombatDemo();
        }
    }

    // -------------------------------------------------------------------------
    // Session bootstrap
    // -------------------------------------------------------------------------

    private GameSession loadOrCreateSession() {
        if (SaveManager.saveExists(SAVE_PATH)) {
            try {
                GameSession loaded = SaveManager.load(SAVE_PATH);
                System.out.println("[Main] Save loaded: " + loaded);
                return loaded;
            } catch (SaveManager.SaveFormatException e) {
                System.err.println("[Main] Save file corrupt — starting new game. ("
                        + e.getMessage() + ")");
            } catch (IOException e) {
                System.err.println("[Main] Save load failed — starting new game. ("
                        + e.getMessage() + ")");
            }
        }

        System.out.println("[Main] No save found — generating new game.");

        // FIX: use worldMap.getStartPosition() instead of DEFAULT_START (13, 10).
        // The start position is authored in WorldMapBaker and baked into the
        // .map header — it should not be duplicated as a magic number here.
        var startPos = (worldMap != null)
                ? worldMap.getStartPosition()
                : new com.phantasia.core.world.WorldPosition(0, 0);

        return SaveManager.newGame(EncounterFactory.generateParty(), startPos);
    }

    // -------------------------------------------------------------------------
    // Map loading
    // -------------------------------------------------------------------------

    private WorldMap loadWorldMap() {
        try {
            WorldMap map = WorldMap.loadFromFile(MAP_PATH);
            System.out.println("[Main] World map loaded: "
                    + map.getWidth() + "×" + map.getHeight()
                    + "  start=" + map.getStartPosition());
            return map;
        } catch (IOException e) {
            System.out.println("[Main] world.dat not found at "
                    + DataPaths.absolute(MAP_PATH)
                    + " — run WorldMapBaker to generate it.");
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // World exploration mode
    // -------------------------------------------------------------------------

    private void launchWorldMode() {
        HUDState             hud         = new HUDState(session);
        VisualBridgeAppState visualBridge = new VisualBridgeAppState();
        FloatingTextState    floatingText = new FloatingTextState();
        WorldState           world        = new WorldState(session, worldMap);

        stateManager.attachAll(hud, visualBridge, floatingText, world);
    }

    // -------------------------------------------------------------------------
    // Combat demo fallback (original behaviour when no map exists)
    // -------------------------------------------------------------------------

    private void launchCombatDemo() {
        HUDState             hud         = new HUDState(session);
        VisualBridgeAppState visualBridge = new VisualBridgeAppState();
        FloatingTextState    floatingText = new FloatingTextState();
        CombatState          combat       = new CombatState(session);

        stateManager.attachAll(hud, visualBridge, floatingText, combat);
    }

    // -------------------------------------------------------------------------
    // Save on exit
    // -------------------------------------------------------------------------

    @Override
    public void destroy() {
        if (session != null) {
            try {
                SaveManager.save(session, SAVE_PATH);
                System.out.println("[Main] Game saved on exit.");
            } catch (IOException e) {
                System.err.println("[Main] Auto-save failed: " + e.getMessage());
            }
        }
        super.destroy();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public GameSession getSession()  { return session; }
    public WorldMap    getWorldMap() { return worldMap; }
}
