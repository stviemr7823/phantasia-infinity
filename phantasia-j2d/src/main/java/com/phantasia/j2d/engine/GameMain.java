// phantasia-j2d/src/main/java/com/phantasia/j2d/engine/GameMain.java
package com.phantasia.j2d.engine;

import com.phantasia.core.data.*;
import com.phantasia.core.logic.NavigationManager;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.world.*;
import com.phantasia.j2d.screen.*;
import com.phantasia.core.data.ShopInventory;
import com.phantasia.core.data.ShopEntry;
import com.phantasia.j2d.tour.KenneyTileLoader;
import com.phantasia.core.data.NpcRegistry;
import com.phantasia.core.data.ShopRegistry;

import java.io.IOException;
import java.util.List;
import com.phantasia.core.world.WorldClock;
import com.phantasia.core.world.WeatherManager;
import com.phantasia.core.world.TerrainMetadataService;
import com.phantasia.core.world.TerrainMetadata;
import com.phantasia.core.data.InteriorMapRegistry;
import com.phantasia.core.data.InteriorMapLoader;
import com.phantasia.core.world.InteriorMap;

import com.phantasia.core.data.InteriorMapRegistry;
import com.phantasia.core.data.InteriorMapLoader;
import com.phantasia.core.world.InteriorMap;

import com.phantasia.core.world.WfcPlacementService;
import com.phantasia.core.world.PlacementData;

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
 *     ├── GameCanvas      (fullscreen AWT Canvas)
 *     ├── GameLoop        (fixed-timestep update thread)
 *     ├── ScreenManager   (screen map + transitions)
 *     ├── InputRouter     (keyboard + mouse → active screen)
 *     └── ResourceCache   (images, fonts, textures)
 * </pre>
 *
 * <h3>Screen transition graph</h3>
 * <pre>
 *   WORLD_ROAM ──encounter──→ ENCOUNTER_SPLASH
 *   ENCOUNTER_SPLASH ──fade──→ COMBAT_PLANNING
 *   COMBAT_PLANNING  ──engage──→ COMBAT_EXECUTION
 *   COMBAT_EXECUTION ──victory──→ VICTORY ──any key──→ WORLD_ROAM
 *   COMBAT_EXECUTION ──defeat──→  DEFEAT  ──any key──→ quit
 *   COMBAT_EXECUTION ──ongoing──→ COMBAT_PLANNING (next round)
 *   COMBAT_EXECUTION ──escaped──→ WORLD_ROAM
 *   WORLD_ROAM ──E on NPC──→ push(DIALOGUE) ──ESC──→ pop → WORLD_ROAM
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
        WorldMap        worldMap        = loadWorldMap(mapPath);
        TerrainMetadata terrainMeta = new TerrainMetadataService().compute(worldMap);
        System.out.println("[GameMain] Terrain metadata computed. "
                + "Max height=" + String.format("%.3f", terrainMeta.getMaxHeight()));

        PlacementData placementData = new WfcPlacementService()
                .compute(worldMap, terrainMeta);
        System.out.println("[GameMain] " + placementData);

        FeatureRegistry featureRegistry = loadFeatureRegistry();
        SpellFactory    spellFactory    = loadSpells();
        GameSession     session         = loadOrCreateSession(worldMap, mapPath);

        // ── Support services ────────────────────────────────────────────
        NavigationManager navManager = new NavigationManager(worldMap, featureRegistry);
        KenneyTileLoader  tileLoader = new KenneyTileLoader("assets/tiles");
        tileLoader.load();

        // ── Build the stage ─────────────────────────────────────────────
        GameCanvas    canvas        = new GameCanvas();
        ScreenManager screenManager = new ScreenManager();
        GameLoop      gameLoop      = new GameLoop(canvas, screenManager);
        InputRouter   inputRouter   = new InputRouter(canvas, screenManager, gameLoop);

        NpcRegistry  npcRegistry  = loadNpcs();
        ShopRegistry shopRegistry = loadShops();

        // WorldClock is now owned by GameSession (persisted in save file)
        WorldClock     clock   = session.getWorldClock();
        WeatherManager weather = new WeatherManager();


        InteriorMapRegistry interiorMaps = loadInteriorMaps();

        // ── Register screens ────────────────────────────────────────────
        //
        // Construction rule: screens receive long-lived dependencies (services,
        // managers, session) at construction time. Per-transition data
        // (enemies, round results, etc.) arrives via onEnter(transitionData).

        screenManager.register(GameState.WORLD_ROAM,
                new WorldRoamScreen(screenManager, worldMap, session,
                        navManager, featureRegistry, tileLoader, spellFactory,
                        npcRegistry, shopRegistry, clock, weather, terrainMeta,
                        interiorMaps, placementData));

        screenManager.register(GameState.ENCOUNTER_SPLASH,
                new EncounterSplashScreen(screenManager));

        screenManager.register(GameState.COMBAT_PLANNING,
                new CombatPlanningScreen(screenManager, spellFactory));

        screenManager.register(GameState.COMBAT_EXECUTION,
                new CombatExecutionScreen(screenManager));

        screenManager.register(GameState.VICTORY,
                new VictoryScreen(screenManager));

        // DefeatScreen calls onQuit when the player acknowledges defeat.
        // Stopping the game loop unblocks awaitShutdown() below, which
        // triggers the shutdown hook → auto-save → System.exit.
        screenManager.register(GameState.DEFEAT,
                new DefeatScreen(gameLoop::stop));

        screenManager.register(GameState.DIALOGUE,
                new DialogueScreen(screenManager));

        screenManager.register(GameState.SHOP,
                new ShopOverlay(screenManager));

        screenManager.register(GameState.INN,
                new InnOverlay(screenManager));

        screenManager.register(GameState.GUILD,
                new GuildOverlay(screenManager));

        screenManager.register(GameState.BANK,
                new BankOverlay(screenManager));

        screenManager.register(GameState.DUNGEON,
                new DungeonRoamScreen(screenManager, clock, weather));

        screenManager.register(GameState.PAUSE,
                new PauseScreen(screenManager, gameLoop));

        screenManager.register(GameState.LOCAL_MAP,
                new LocalMapScreen(screenManager, session, npcRegistry, shopRegistry));

        screenManager.register(GameState.TOWN,
                new TownRoamScreen(screenManager, session,
                        npcRegistry, shopRegistry));



        // ── Enter fullscreen and start ──────────────────────────────────
        canvas.enterFullscreen();
        inputRouter.install();
        inputRouter.setEscQuitsGame(false);  // ← add this

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
    // Data loading
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

    /**
     * Loads the feature registry from features.dat.
     * Falls back to the programmatic registry from WorldMapBaker if the file
     * is absent (e.g., first run before a bake). This matches the features
     * embedded in PendragonWorldMap so the game always has valid feature data.
     */
    private static FeatureRegistry loadFeatureRegistry() {
        String path = DataPaths.DAT_DIR + "/features.dat";
        try {
            FeatureRegistry registry = FeatureRegistry.load(path);
            if (registry.size() > 0) {
                System.out.println("[GameMain] Loaded feature registry: "
                        + registry.size() + " records.");
                return registry;
            }
        } catch (IOException e) {
            System.err.println("[GameMain] features.dat load failed ("
                    + e.getMessage() + ") — using built-in registry.");
        }
        System.out.println("[GameMain] Using built-in feature registry.");
        return WorldMapBaker.buildFeatureRegistry();
    }

    /**
     * Loads spells from spells.dat. Non-fatal — combat continues if absent,
     * spell casts resolve as misses via the null-safe path in FormulaEngine.
     */
    private static SpellFactory loadSpells() {
        SpellFactory spellFactory = new SpellFactory();
        try {
            spellFactory.loadSpells();
        } catch (IOException e) {
            System.err.println("[GameMain] Could not load spells.dat — spells"
                    + " will fail gracefully. (" + e.getMessage() + ")");
        }
        return spellFactory;
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

    private static NpcRegistry loadNpcs() {
        try {
            NpcRegistry reg = NpcBaker.load(DataPaths.NPCS_DAT);
            if (reg.size() > 0) {
                System.out.println("[GameMain] Loaded NPC registry: "
                        + reg.size() + " NPCs.");
                return reg;
            }
        } catch (IOException e) {
            System.err.println("[GameMain] npcs.dat load failed ("
                    + e.getMessage() + ") — using built-in NPCs.");
        }
        NpcRegistry fallback = NpcRegistry.buildDefault();
        System.out.println("[GameMain] Using built-in NPC registry ("
                + fallback.size() + " NPCs).");
        return fallback;
    }

    private static ShopRegistry loadShops() {
        try {
            ShopRegistry reg = ShopBaker.load(DataPaths.SHOPS_DAT);
            if (reg.size() > 0) {
                System.out.println("[GameMain] Loaded shop registry: "
                        + reg.size() + " shops.");
                return reg;
            }
        } catch (IOException e) {
            System.err.println("[GameMain] shops.dat load failed ("
                    + e.getMessage() + ") — using built-in shops.");
        }
        ShopRegistry fallback = ShopRegistry.buildDefault();
        System.out.println("[GameMain] Using built-in shop registry ("
                + fallback.size() + " shops).");
        return fallback;
    }

    private static InteriorMapRegistry loadInteriorMaps() {
        if (!java.nio.file.Files.exists(java.nio.file.Path.of(DataPaths.INTERIORS_DAT))) {
            System.out.println("[GameMain] No authored interiors file; using built-in maps.");
            return InteriorMapRegistry.buildDefault();
        }
        try {
            // Existing exports, including an intentionally empty one, are authoritative.
            return InteriorMapLoader.load(DataPaths.INTERIORS_DAT);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Cannot load authored interiors: " + DataPaths.INTERIORS_DAT, e);
        }
    }
}
