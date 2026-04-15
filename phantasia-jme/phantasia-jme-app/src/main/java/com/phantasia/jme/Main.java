package com.phantasia.jme;

import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import com.phantasia.core.data.DataPaths;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.SaveManager;
import com.phantasia.core.world.WorldMap;
import com.phantasia.jme.states.GameManagerState;
import com.phantasia.jme.states.HUDState;
import com.phantasia.jme.states.VisualBridgeAppState;
import com.phantasia.jme.states.WorldState;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PHANTASIA: INFINITY — Revision 4.0
 * Application entry point and GameSession owner.
 */
public class Main extends SimpleApplication {

    private static final Logger logger = Logger.getLogger(Main.class.getName());
    private static final String SAVE_PATH = "data/GAME.DAT";

    private GameSession session;
    private WorldMap worldMap;

    public static void main(String[] args) {
        Main app = new Main();
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Phantasia: Infinity - Rev 4.0");
        settings.setResolution(1280, 720);
        settings.setSamples(4); // Anti-aliasing for the "Face"
        
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        // 1. Silent the noise
        Logger.getLogger("com.jme3").setLevel(Level.WARNING);
        
        // 2. Load the Skeleton (Data & Session)
        loadGameSession();

        // 3. Attach the Core-to-JME Bridge
        stateManager.attach(new VisualBridgeAppState());
        stateManager.attach(new HUDState(session));

        // 4. Attach the Conductor (Handles Combat/Judgment transitions)
        stateManager.attach(new GameManagerState(session));

        // 5. Kick off the World State (The exploration Face)
        if (worldMap != null) {
            stateManager.attach(new WorldState(session, worldMap));
        } else {
            logger.severe("Critical Failure: WorldMap could not be loaded.");
        }
        
        // Remove default flycam to use our WorldCameraRig
        flyCam.setEnabled(false);
    }

    private void loadGameSession() {
        try {
            // Try to inhale an existing save
            session = SaveManager.load(SAVE_PATH);
            logger.info("Save file 'GAME.DAT' inhaled successfully.");
        } catch (Exception e) {
            logger.warning("No save found or load failed. Generating fresh party...");
            session = GameSession.freshStart();
        }

        try {
            // Load the grid-based world binary
            worldMap = WorldMap.loadFromFile(DataPaths.WORLD_DAT);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load world.dat!", e);
        }
    }

    @Override
    public void destroy() {
        // Auto-save on exit - Preservation of the Skeleton
        if (session != null) {
            try {
                SaveManager.save(session, SAVE_PATH);
                logger.info("Game state persisted to GAME.DAT.");
            } catch (IOException e) {
                logger.severe("Auto-save failed: " + e.getMessage());
            }
        }
        super.destroy();
    }

    // Accessors for states to peek at the session
    public GameSession getSession() { return session; }
    public WorldMap getWorldMap() { return worldMap; }
}