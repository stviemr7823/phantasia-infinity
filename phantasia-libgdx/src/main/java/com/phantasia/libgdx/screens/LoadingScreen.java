package com.phantasia.libgdx.screens;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.phantasia.core.data.EncounterFactory;
import com.phantasia.core.data.SaveManager;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.world.PendragonWorldMap;
import com.phantasia.core.world.WorldMap;
import com.phantasia.core.world.WorldPosition;
import com.phantasia.libgdx.PhantasiaGame;
import com.phantasia.libgdx.renderer.KenneyTileSet;
import com.phantasia.core.world.FeatureRegistry;
import com.phantasia.core.data.DataPaths;

import java.io.IOException;
import java.util.List;

/**
 * Bootstraps the world map, tile textures, and session, then transitions
 * to the next screen.
 *
 * <h3>Asset loading</h3>
 * {@link KenneyTileSet#queueAll} is called in {@link #show()} so the tile
 * PNGs are registered with the {@code AssetManager} before the first
 * {@code render()} call.  Each {@code render()} call advances
 * {@code AssetManager.update()} by one step, which is the standard LibGDX
 * async-loading pattern.  The progress bar reflects actual load progress.
 *
 * Once loading completes, {@link KenneyTileSet#fetchAll} retrieves the
 * resolved textures and the tile set is stored on {@link PhantasiaGame} so
 * downstream screens can access it without re-loading.
 *
 * If the {@code tiles/} asset folder is absent or empty, the tile set
 * gracefully queues nothing and every tile falls back to a solid color —
 * the game always runs.
 *
 * <h3>Map loading modes</h3>
 * See previous Javadoc — editor launch vs. normal launch behavior unchanged.
 */
public class LoadingScreen implements Screen {

    public enum Destination { GAME, TOUR }

    private final PhantasiaGame  game;
    private final Destination    destination;
    private final ShapeRenderer  shapeRenderer;
    private final KenneyTileSet  tileSet;

    public LoadingScreen(PhantasiaGame game, Destination destination) {
        this.game          = game;
        this.destination   = destination;
        this.shapeRenderer = new ShapeRenderer();
        this.tileSet       = new KenneyTileSet();
    }

    // -------------------------------------------------------------------------
    // Screen lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void show() {
        // Queue all tile PNGs with the AssetManager.
        // Files that are absent are silently skipped — fallback colors handle them.
        tileSet.queueAll(game.getAssets());
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0, 0, 0, 1);

        if (game.getAssets().update()) {
            // All queued assets loaded — fetch textures and bootstrap session
            tileSet.fetchAll(game.getAssets());
            game.setTileSet(tileSet);

            bootstrapSession();

            Screen next = (destination == Destination.TOUR)
                    ? new TestMapTourScreen(game)
                    : new GameScreen(game);
            game.setScreen(next);
            return;
        }

        // Progress bar — now reflects actual tile-loading progress
        shapeRenderer.setProjectionMatrix(game.getBatch().getProjectionMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.rect(100, 240, 600 * game.getAssets().getProgress(), 20);
        shapeRenderer.end();
    }

    @Override public void resize(int width, int height) {}
    @Override public void pause()                       {}
    @Override public void resume()                      {}
    @Override public void hide()                        {}

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        // tileSet textures are owned by AssetManager — not disposed here
    }

    // -------------------------------------------------------------------------
    // Bootstrap
    // -------------------------------------------------------------------------

    private void bootstrapSession() {
        String editorMapPath = game.getEditorMapPath();
        if (editorMapPath != null) {
            bootstrapEditorSession(editorMapPath);
        } else {
            bootstrapNormalSession();
        }
    }

    private void bootstrapEditorSession(String mapPath) {
        WorldMap map;
        try {
            map = WorldMap.loadFromFile(mapPath);
            System.out.println("[LoadingScreen] Editor launch — loaded: " + mapPath
                    + "  start: " + map.getStartPosition());
        } catch (IOException e) {
            System.err.println("[LoadingScreen] Could not load '" + mapPath
                    + "': " + e.getMessage() + " — falling back to built-in map.");
            map = PendragonWorldMap.buildWorldMap();
        }
        game.setWorldMap(map);
        try {
            FeatureRegistry fr = FeatureRegistry.load(
                    DataPaths.DAT_DIR + "/features.dat");
            game.setFeatureRegistry(fr);
            System.out.println("[LoadingScreen] FeatureRegistry loaded: "
                    + fr.size() + " records.");
        } catch (Exception e) {
            System.out.println("[LoadingScreen] features.dat not found — "
                    + "feature names will use fallback.");
        }
        List<PlayerCharacter> party = EncounterFactory.generateParty();
        game.setSession(SaveManager.newGame(party, map.getStartPosition()));
        System.out.println("[LoadingScreen] Editor session ready — start: "
                + map.getStartPosition());
    }

    private void bootstrapNormalSession() {
        WorldMap map = PendragonWorldMap.buildWorldMap();
        game.setWorldMap(map);
        try {
            FeatureRegistry fr = FeatureRegistry.load(
                    DataPaths.DAT_DIR + "/features.dat");
            game.setFeatureRegistry(fr);
            System.out.println("[LoadingScreen] FeatureRegistry loaded: "
                    + fr.size() + " records.");
        } catch (Exception e) {
            System.out.println("[LoadingScreen] features.dat not found — "
                    + "feature names will use fallback.");
        }
        List<PlayerCharacter> party = EncounterFactory.generateParty();
        game.setSession(SaveManager.newGame(party, map.getStartPosition()));
        System.out.println("[LoadingScreen] Normal session ready — start: "
                + map.getStartPosition());
    }
}