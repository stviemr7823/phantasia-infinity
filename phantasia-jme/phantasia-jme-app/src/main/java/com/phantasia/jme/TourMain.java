// phantasia-jme/phantasia-jme-app/src/main/java/com/phantasia/jme/TourMain.java
package com.phantasia.jme;

import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import com.phantasia.core.data.DataPaths;
import com.phantasia.core.data.EncounterFactory;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.SaveManager;
import com.phantasia.core.world.WorldMap;
import com.phantasia.jme.states.HUDState;
import com.phantasia.jme.states.FloatingTextState;
import com.phantasia.jme.states.WorldState;

import java.io.IOException;

/**
 * Graphics testbed — world exploration only, no encounters, no transitions.
 *
 * Intentionally minimal:
 *   - WorldState        — terrain, camera, mouse look, movement
 *   - HUDState          — terrain indicator and party stats visible for reference
 *   - FloatingTextState — required by HUDState
 *
 * Deliberately excluded:
 *   - GameManagerState  — without this, EncounterTriggered and TownEntered
 *                         events fire onto the bus and are simply ignored.
 *                         No combat, no town screen, no interruptions.
 *   - VisualBridgeAppState — not needed without combat
 *   - CombatState       — not needed
 *
 * No save file is written on exit — this is a read-only tour.
 * A fresh party is always generated so there is no dependency on GAME.DAT.
 */
public class TourMain extends SimpleApplication {

    private static final String MAP_PATH = DataPaths.DAT_DIR + "/world.dat";

    private GameSession session;
    private WorldMap    worldMap;

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        TourMain app = new TourMain();
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Phantasia: Infinity — World Tour");
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

        worldMap = loadWorldMap();

        if (worldMap == null) {
            System.err.println("[TourMain] No world.dat found — cannot run tour mode.");
            System.err.println("[TourMain] Run WorldMapBaker first to generate the map.");
            stop();
            return;
        }

        // Always a fresh party — position comes from the map's start position.
        // No save file involvement at all.
        session = SaveManager.newGame(
                EncounterFactory.generateParty(),
                worldMap.getStartPosition());

        System.out.println("[TourMain] Tour mode active — encounters and towns disabled.");
        System.out.println("[TourMain] Controls: W/S move, A/D turn, mouse look when stationary.");

        HUDState          hud         = new HUDState(session);
        FloatingTextState floatingText = new FloatingTextState();
        WorldState        world        = new WorldState(session, worldMap);

        stateManager.attachAll(hud, floatingText, world);
    }

    // -------------------------------------------------------------------------
    // Map loading
    // -------------------------------------------------------------------------

    private WorldMap loadWorldMap() {
        try {
            WorldMap map = WorldMap.loadFromFile(MAP_PATH);
            System.out.println("[TourMain] World map loaded: "
                    + map.getWidth() + "×" + map.getHeight()
                    + "  start=" + map.getStartPosition());
            return map;
        } catch (IOException e) {
            System.out.println("[TourMain] world.dat not found at "
                    + DataPaths.absolute(MAP_PATH));
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // No save on exit — tour is read-only
    // -------------------------------------------------------------------------

    @Override
    public void destroy() {
        System.out.println("[TourMain] Tour session ended — no save written.");
        super.destroy();
    }
}