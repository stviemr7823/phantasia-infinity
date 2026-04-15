// phantasia-j2d/src/main/java/com/phantasia/j2d/engine/GameMain.java
package com.phantasia.j2d.engine;

import com.phantasia.core.data.DataPaths;
import com.phantasia.core.data.EncounterFactory;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.SaveManager;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.world.PendragonWorldMap;
import com.phantasia.core.world.WorldMap;
import com.phantasia.core.world.WorldPosition;
import com.phantasia.j2d.screen.PlaceholderScreen;

import java.io.IOException;
import java.util.List;

/**
 * Entry point for the Phantasia: Infinity j2d game frontend.
 *
 * <p>Replaces the prototype {@code TourMain} and {@code TourFrame}.
 * No Swing components. No EDT dependency. The entire game runs on the
 * {@link GameLoop} thread with rendering on a fullscreen {@link GameCanvas}.</p>
 *
 * <h3>Wiring</h3>
 * <pre>
 *   GameMain
 *     ├── GameCanvas (fullscreen AWT Canvas)
 *     ├── GameLoop   (fixed-timestep update thread)
 *     ├── ScreenManager (screen map + transitions)
 *     ├── InputRouter   (keyboard + mouse → active screen)
 *     └── ResourceCache (images, fonts, textures)
 * </pre>
 *
 * <h3>Launch</h3>
 * <pre>
 *   java -cp &lt;classpath&gt; com.phantasia.j2d.engine.GameMain
 *   java -cp &lt;classpath&gt; com.phantasia.j2d.engine.GameMain data/tour_preview.map
 * </pre>
 */
public class GameMain {

    public static final String SAVE_PATH =
            DataPaths.DAT_DIR + "/tour/TOUR.DAT";

    // ─────────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        String mapPath = (args.length > 0) ? args[0] : null;

        // ── Load game data ──────────────────────────────────────────────
        WorldMap    worldMap = loadWorldMap(mapPath);
        GameSession session  = loadOrCreateSession(worldMap, mapPath);

        // ── Build the stage ─────────────────────────────────────────────
        GameCanvas    canvas        = new GameCanvas("Phantasia: Infinity");
        ScreenManager screenManager = new ScreenManager();
        GameLoop      gameLoop      = new GameLoop(canvas, screenManager);
        InputRouter   inputRouter   = new InputRouter(canvas, screenManager, gameLoop);

        // ── Register screens ────────────────────────────────────────────
        // Phase 2 deliverable: blank fullscreen, black background, loop running.
        // Concrete screens (WorldRoamScreen, CombatPlanningScreen, etc.) are
        // registered here in Phase 3. For now, a placeholder verifies the pipeline.

        PlaceholderScreen placeholder = new PlaceholderScreen(gameLoop);
        screenManager.register(GameState.WORLD_ROAM, placeholder);

        // TODO Phase 3: Register all game screens
        // screenManager.register(GameState.ENCOUNTER_SPLASH, new EncounterSplashScreen(...));
        // screenManager.register(GameState.COMBAT_PLANNING, new CombatPlanningScreen(...));
        // screenManager.register(GameState.COMBAT_EXECUTION, new CombatExecutionScreen(...));
        // screenManager.register(GameState.TOWN, new TownScreen(...));
        // screenManager.register(GameState.DIALOGUE, new DialogueScreen(...));

        // ── Enter fullscreen and start ──────────────────────────────────
        canvas.enterFullscreen();
        inputRouter.install();

        screenManager.switchTo(GameState.WORLD_ROAM, null);

        gameLoop.start();

        // ── Shutdown hook ───────────────────────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            gameLoop.stop();
            gameLoop.awaitShutdown();
            canvas.shutdown();
            try {
                SaveManager.save(session, SAVE_PATH);
                System.out.println("[GameMain] Auto-saved to " + SAVE_PATH);
            } catch (IOException e) {
                System.err.println("[GameMain] Auto-save failed: " + e.getMessage());
            }
        }));

        // Block the main thread until the game loop ends
        gameLoop.awaitShutdown();
        System.exit(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data loading (migrated from TourMain)
    // ─────────────────────────────────────────────────────────────────────────

    private static WorldMap loadWorldMap(String mapPath) {
        if (mapPath != null) {
            try {
                return WorldMap.loadFromFile(mapPath);
            } catch (IOException e) {
                System.err.println("[GameMain] Could not load '" + mapPath
                        + "': " + e.getMessage() + " — using built-in map.");
            }
        }
        return PendragonWorldMap.buildWorldMap();
    }

    private static GameSession loadOrCreateSession(WorldMap worldMap, String mapPath) {
        if (mapPath != null) {
            System.out.println("[GameMain] Editor launch — ignoring save, using map start: "
                    + worldMap.getStartPosition());
            List<PlayerCharacter> party = EncounterFactory.generateParty();
            return SaveManager.newGame(party, worldMap.getStartPosition());
        }
        if (SaveManager.saveExists(SAVE_PATH)) {
            try {
                return SaveManager.load(SAVE_PATH);
            } catch (Exception e) {
                System.err.println("[GameMain] Save load failed — starting fresh. ("
                        + e.getMessage() + ")");
            }
        }
        List<PlayerCharacter> party = EncounterFactory.generateParty();
        WorldPosition         start = worldMap.getStartPosition();
        return SaveManager.newGame(party, start);
    }
}