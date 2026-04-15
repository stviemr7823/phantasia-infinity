package com.phantasia.libgdx.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.logic.*;
import com.phantasia.core.logic.NavigationManager.MoveResult;
import com.phantasia.core.world.EncounterTimer;
import com.phantasia.core.world.Tile;
import com.phantasia.core.world.WorldMap;
import com.phantasia.core.world.WorldPosition;
import com.phantasia.libgdx.PhantasiaGame;
import com.phantasia.libgdx.SmoothPartyActor;
import com.phantasia.libgdx.renderer.KenneyTileSet;
import com.phantasia.libgdx.renderer.WorldMapRenderer;

import static com.phantasia.libgdx.renderer.WorldMapRenderer.TILE_SIZE;

/**
 * Map tour / exploration screen.
 *
 * <h3>Tile display size</h3>
 * {@code TILE_SIZE} is 32 world units — the authoritative unit used by
 * {@link WorldMapRenderer} and {@link SmoothPartyActor} for all coordinate
 * math.  To display tiles at 64 screen pixels we set {@code camera.zoom = 0.5f}:
 * zoom &lt; 1 zooms in, making each world unit cover more screen pixels.
 * At zoom 0.5, one world unit = 2 screen pixels, so a 32-unit tile = 64px.
 *
 * <h3>WorldEvent routing (all cases implemented)</h3>
 *   EnterTown       → {@link TownScreen} (fully functional inn + guild)
 *   EnterDungeon    → {@link DungeonScreen} (entry diagnostic stub)
 *   RandomEncounter → {@link CombatScreen#forRandom}
 *   ScriptedBattle  → {@link CombatScreen#forScripted}
 *   TileEventPrompt → inline overlay with SPACE/ENTER confirmation
 *   NpcInteraction  → log entry (dialogue UI wired in next stage)
 */
public class TestMapTourScreen implements Screen {

    private static final float BLOCKED_FLASH_S = 0.4f;

    /**
     * Camera zoom — controls display tile size independently of world units.
     * 0.5 = zoomed in 2x → 32-unit tiles display at 64 screen pixels.
     */
    private static final float MAP_ZOOM = 0.5f;

    private final PhantasiaGame     game;
    private final GameSession       session;
    private final WorldMap          worldMap;
    private final NavigationManager navManager;

    private final WorldMapRenderer   mapRenderer;
    private final SmoothPartyActor   partyActor;
    private final ShapeRenderer      shapeRenderer;
    private final OrthographicCamera mapCamera;
    private final ScreenViewport     mapViewport;
    private final Matrix4            hudProjection = new Matrix4();

    private String  tileReadout  = "";
    private float   blockedTimer = 0f;
    private int     moveCount    = 0;

    private WorldEvent.TileEventPrompt pendingPrompt  = null;
    private GameEventBus.Subscription  busSubscription;

    private final WorldEventResolver resolver       = new WorldEventResolver();
    private final EncounterTimer     encounterTimer = new EncounterTimer();

    public TestMapTourScreen(PhantasiaGame game) {
        this.game      = game;
        this.session   = game.getSession();
        this.worldMap  = game.getWorldMap();

        this.navManager = new NavigationManager(worldMap);

        mapRenderer = new WorldMapRenderer(worldMap);
        if (game.getTileSet() != null) {
            mapRenderer.setTileSet(game.getTileSet());
        }

        shapeRenderer = new ShapeRenderer();

        partyActor = new SmoothPartyActor(
                session.getParty().get(0),
                session.getWorldPosition()
        );

        mapCamera      = new OrthographicCamera();
        mapCamera.zoom = MAP_ZOOM;
        mapViewport    = new ScreenViewport(mapCamera);
        mapViewport.apply(true);

        WorldPosition start = session.getWorldPosition();
        mapCamera.position.set(
                (start.x() + 0.5f) * TILE_SIZE,
                (start.y() + 0.5f) * TILE_SIZE,
                0f
        );
        mapCamera.update();

        rebuildHudProjection(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        updateTileReadout(session.getWorldPosition());
    }

    // -------------------------------------------------------------------------
    // Screen lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void show() {
        System.out.println("[TestMapTourScreen] show() — "
                + (mapRenderer.hasTileSet() ? "textured" : "solid-color") + " mode");

        busSubscription = GameEventBus.get().subscribe(event -> {
            if (event instanceof GameEvent.PartyMoved moved) {
                updateTileReadout(moved.newPosition());
            }
        });

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int keycode) { return handleKey(keycode); }
        });
    }

    @Override
    public void render(float delta) {
        if (blockedTimer > 0f) blockedTimer -= delta;

        partyActor.update(delta, session.getWorldPosition());

        mapCamera.position.set(
                partyActor.getVisualPos().x + TILE_SIZE / 2f,
                partyActor.getVisualPos().y + TILE_SIZE / 2f,
                0f
        );
        mapCamera.update();

        ScreenUtils.clear(0.051f, 0.051f, 0.078f, 1f);

        mapViewport.apply();

        if (mapRenderer.hasTileSet()) {
            KenneyTileSet ts            = game.getTileSet();
            Texture       playerTexture = ts.getPlayerTexture();

            mapRenderer.renderTextured(game.getBatch(), shapeRenderer, mapCamera);

            game.getBatch().setProjectionMatrix(mapCamera.combined);
            game.getBatch().begin();
            if (playerTexture != null) {
                partyActor.draw(game.getBatch(), playerTexture);
            }
            game.getBatch().end();

            if (playerTexture == null) {
                shapeRenderer.setProjectionMatrix(mapCamera.combined);
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                partyActor.draw(shapeRenderer);
                shapeRenderer.end();
            }

        } else {
            shapeRenderer.setProjectionMatrix(mapCamera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            mapRenderer.render(shapeRenderer, mapCamera);
            partyActor.draw(shapeRenderer);
            shapeRenderer.end();
        }

        // Reset GL viewport to full window so HUD covers the whole screen
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        game.getBatch().setProjectionMatrix(hudProjection);
        game.getBatch().begin();
        drawHud();
        if (pendingPrompt != null) drawPromptOverlay();
        game.getBatch().end();
    }

    @Override
    public void resize(int width, int height) {
        mapViewport.update(width, height, true);
        rebuildHudProjection(width, height);
    }

    @Override
    public void hide() {
        if (busSubscription != null) { busSubscription.cancel(); busSubscription = null; }
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        if (busSubscription != null) busSubscription.cancel();
        shapeRenderer.dispose();
    }

    @Override public void pause()  {}
    @Override public void resume() {}

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    private boolean handleKey(int keycode) {
        if (pendingPrompt != null) {
            if (keycode == Input.Keys.SPACE || keycode == Input.Keys.ENTER) {
                pendingPrompt.tileEvent().resolve();
                System.out.println("[TestMapTourScreen] TileEvent resolved: "
                        + pendingPrompt.tileEvent().description);
                pendingPrompt = null;
            }
            return true;
        }

        String dir = switch (keycode) {
            case Input.Keys.UP,    Input.Keys.W -> "north";
            case Input.Keys.DOWN,  Input.Keys.S -> "south";
            case Input.Keys.LEFT,  Input.Keys.A -> "west";
            case Input.Keys.RIGHT, Input.Keys.D -> "east";
            default -> null;
        };

        if (dir == null) return false;

        MoveResult result = navManager.attemptMove(dir, session, worldMap);
        if (!result.moved) { blockedTimer = BLOCKED_FLASH_S; return true; }
        session.setPosition(result.newPosition);
        moveCount++;

        Tile tile = worldMap.getTile(result.newPosition);

// Towns, dungeons, scripted battles, tile events
        resolver.resolve(tile).ifPresent(this::handleWorldEvent);

// Random encounter timer — never triggers on feature tiles
        if (!tile.hasFeature() && encounterTimer.step(tile.getType())) {
            handleWorldEvent(new WorldEvent.RandomEncounter());
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // WorldEvent routing — all cases handled, using full screen implementations
    // -------------------------------------------------------------------------

    private void handleWorldEvent(WorldEvent event) {
        switch (event) {
            case WorldEvent.EnterTown t ->
                    game.setScreen(new TownScreen(game, this, t.id(), t.name()));
            case WorldEvent.EnterDungeon d ->
                    game.setScreen(new DungeonScreen(game, this, d.id(), d.name()));
            case WorldEvent.RandomEncounter ignored ->
                    game.setScreen(CombatScreen.forRandom(game, this, session));
            case WorldEvent.ScriptedBattle s ->
                    game.setScreen(CombatScreen.forScripted(
                            game, this, session, s.monsterName(), s.count()));
            case WorldEvent.TileEventPrompt p -> {
                System.out.println("[TestMapTourScreen] TileEventPrompt: "
                        + p.tileEvent().description);
                pendingPrompt = p;
            }
            case WorldEvent.NpcInteraction npc ->
                    System.out.println("[TestMapTourScreen] NpcInteraction: "
                            + npc.npcName() + " (dialogue UI — next stage)");
        }
    }

    // -------------------------------------------------------------------------
    // HUD
    // -------------------------------------------------------------------------

    private void drawHud() {
        float x = 10f, y = Gdx.graphics.getHeight() - 10f, lineH = 20f;

        game.getFont().setColor(Color.WHITE);
        game.getFont().draw(game.getBatch(), "[ MAP TOUR \u2014 LIBGDX ]", x, y);
        game.getFont().draw(game.getBatch(), tileReadout,                  x, y -= lineH);
        game.getFont().draw(game.getBatch(), "Moves: " + moveCount,       x, y -= lineH);
        game.getFont().draw(game.getBatch(),
                "FPS:   " + Gdx.graphics.getFramesPerSecond(),            x, y -= lineH);

        if (blockedTimer > 0f) {
            game.getFont().setColor(Color.RED);
            game.getFont().draw(game.getBatch(), "[BLOCKED]", x, y - lineH);
            game.getFont().setColor(Color.WHITE);
        }

        String tileMode = mapRenderer.hasTileSet() ? "Kenney tiles" : "solid colors";
        game.getFont().setColor(Color.LIGHT_GRAY);
        game.getFont().draw(game.getBatch(), tileMode, x, 40f);
        game.getFont().setColor(Color.WHITE);
        game.getFont().draw(game.getBatch(), "ESC \u2014 back to menu", x, 20f);
    }

    private void drawPromptOverlay() {
        float lineH = 20f, midY = Gdx.graphics.getHeight() / 2f;
        game.getFont().setColor(Color.YELLOW);
        game.getFont().draw(game.getBatch(),
                pendingPrompt.tileEvent().description, 20f, midY + lineH * 2);
        game.getFont().draw(game.getBatch(),
                pendingPrompt.tileEvent().prompt,      20f, midY + lineH);
        game.getFont().setColor(Color.WHITE);
        game.getFont().draw(game.getBatch(),
                "[SPACE / ENTER to confirm]",          20f, midY);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void updateTileReadout(WorldPosition pos) {
        tileReadout = "Pos: " + pos + "  Tile: "
                + worldMap.getTile(pos).getType().name();
    }

    private void rebuildHudProjection(int width, int height) {
        hudProjection.setToOrtho2D(0, 0, width, height);
    }
}