package com.phantasia.libgdx.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.logic.NavigationManager;
import com.phantasia.core.logic.NavigationManager.MoveResult;
import com.phantasia.core.logic.WorldEvent;
import com.phantasia.core.logic.WorldEventResolver;
import com.phantasia.core.world.EncounterTimer;
import com.phantasia.core.world.Tile;
import com.phantasia.core.world.WorldMap;
import com.phantasia.core.world.WorldPosition;
import com.phantasia.libgdx.PhantasiaGame;
import com.phantasia.libgdx.SmoothPartyActor;
import com.phantasia.libgdx.renderer.WorldMapRenderer;

import static com.phantasia.libgdx.renderer.WorldMapRenderer.TILE_SIZE;

/**
 * Main gameplay screen: map viewport (centre), stats pane (west), combat log (south).
 *
 * FIXES APPLIED:
 *   1. PhantasiaInputHandler replaced with a local InputAdapter that routes
 *      movement through NavigationManager — walls now block correctly and
 *      WorldEvents (towns, dungeons, encounters) are dispatched.
 *   2. renderTextured() used when tile textures are available, matching
 *      TestMapTourScreen. Solid-color fallback retained when no tile set loaded.
 *   3. Camera centred on tile centre (+ TILE_SIZE/2) rather than tile corner.
 *   4. Party token drawn via SmoothPartyActor.draw(ShapeRenderer).
 *   5. CombatScreen, TownScreen, DungeonScreen now use their full implementations
 *      (not the old Phase-2 stubs).
 */
public class GameScreen implements Screen {

    private static final float WEST_PANE_W   = 200f;
    private static final float SOUTH_PANE_H  = 150f;
    private static final float BLOCKED_FLASH = 0.4f;

    private final PhantasiaGame     game;
    private final GameSession       session;
    private final WorldMap          worldMap;
    private final NavigationManager navManager;

    // Rendering
    private final OrthographicCamera mapCamera;
    private final FitViewport        mapViewport;
    private final ShapeRenderer      shapeRenderer;
    private final SmoothPartyActor   partyActor;
    private final WorldMapRenderer   mapRenderer;
    private final Matrix4            hudProjection = new Matrix4();

    // UI
    private final Stage stage;
    private final Skin  skin;
    private       Label logLabel;

    // State
    private float   blockedTimer = 0f;
    private int     moveCount    = 0;

    private final WorldEventResolver resolver       = new WorldEventResolver();
    private final EncounterTimer     encounterTimer = new EncounterTimer();

    public GameScreen(PhantasiaGame game) {
        this.game      = game;
        this.session   = game.getSession();
        this.worldMap  = game.getWorldMap();

        this.navManager = new NavigationManager(worldMap, game.getFeatureRegistry());

        mapCamera     = new OrthographicCamera();
        mapViewport = new FitViewport(
                (800 - WEST_PANE_W),
                (480 - SOUTH_PANE_H),
                mapCamera);
        shapeRenderer = new ShapeRenderer();

        mapRenderer = new WorldMapRenderer(worldMap);
        if (game.getTileSet() != null) {
            mapRenderer.setTileSet(game.getTileSet());
        }
        if (!mapRenderer.hasTileSet()) {
            System.out.println("[GameScreen] No tile textures — using solid-color fallback.");
        }

        partyActor = new SmoothPartyActor(
                session.getParty().get(0),
                session.getWorldPosition()
        );

        WorldPosition start = session.getWorldPosition();
        mapCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        mapCamera.position.set(
                (start.x() + 0.5f) * TILE_SIZE,
                (start.y() + 0.5f) * TILE_SIZE,
                0f);
        mapCamera.update();

        rebuildHudProjection(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        skin  = buildSkin();
        stage = new Stage(new ScreenViewport());
        initUI();
    }

    // -------------------------------------------------------------------------
    // Screen lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                return handleKey(keycode);
            }
        });
    }

    @Override
    public void render(float delta) {
        if (blockedTimer > 0f) blockedTimer -= delta;

        partyActor.update(delta, session.getWorldPosition());

        mapCamera.position.set(
                partyActor.getVisualPos().x + TILE_SIZE / 2f,
                partyActor.getVisualPos().y + TILE_SIZE / 2f,
                0f);
        mapCamera.update();

        ScreenUtils.clear(0.051f, 0.051f, 0.078f, 1f);

        mapViewport.apply();

        if (mapRenderer.hasTileSet()) {
            mapRenderer.renderTextured(game.getBatch(), shapeRenderer, mapCamera);
            shapeRenderer.setProjectionMatrix(mapCamera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            partyActor.draw(shapeRenderer);
            shapeRenderer.end();
        } else {
            shapeRenderer.setProjectionMatrix(mapCamera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            mapRenderer.render(shapeRenderer, mapCamera);
            partyActor.draw(shapeRenderer);
            shapeRenderer.end();
        }

        // HUD
        game.getBatch().setProjectionMatrix(hudProjection);
        game.getBatch().begin();
        drawHud();
        game.getBatch().end();

        // UI overlay
        stage.getViewport().apply();
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        mapViewport.update(width - (int) WEST_PANE_W,
                height - (int) SOUTH_PANE_H, false);
        stage.getViewport().update(width, height, true);
        rebuildHudProjection(width, height);
    }

    @Override public void hide()    { Gdx.input.setInputProcessor(null); }
    @Override public void pause()   {}
    @Override public void resume()  {}

    @Override
    public void dispose() {
        stage.dispose();
        shapeRenderer.dispose();
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    private boolean handleKey(int keycode) {
        String dir = switch (keycode) {
            case Input.Keys.UP,    Input.Keys.W -> "north";
            case Input.Keys.DOWN,  Input.Keys.S -> "south";
            case Input.Keys.LEFT,  Input.Keys.A -> "west";
            case Input.Keys.RIGHT, Input.Keys.D -> "east";
            default -> null;
        };

        if (dir == null) return false;

        MoveResult result = navManager.attemptMove(dir, session, worldMap);

        if (!result.moved) {
            blockedTimer = BLOCKED_FLASH;
            return true;
        }

        session.setPosition(result.newPosition);
        moveCount++;

        Tile tile = worldMap.getTile(result.newPosition);
        resolver.resolve(tile).ifPresent(this::handleWorldEvent);

        if (!tile.hasFeature() && encounterTimer.step(tile.getType())) {
            handleWorldEvent(new WorldEvent.RandomEncounter());
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // WorldEvent routing — uses full screen implementations
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
                System.out.println("[GameScreen] TileEventPrompt: "
                        + p.tileEvent().description);
                p.tileEvent().resolve();
            }
            case WorldEvent.NpcInteraction npc ->
                    System.out.println("[GameScreen] NpcInteraction: "
                            + npc.npcName() + " (dialogue UI — next stage)");
        }
    }

    // -------------------------------------------------------------------------
    // HUD
    // -------------------------------------------------------------------------

    private void drawHud() {
        float x = 10f, y = Gdx.graphics.getHeight() - 10f, lineH = 20f;

        game.getFont().setColor(Color.WHITE);
        game.getFont().draw(game.getBatch(), "PHANTASIA: INFINITY", x, y);
        game.getFont().draw(game.getBatch(),
                "Pos: " + session.getWorldPosition(), x, y -= lineH);
        game.getFont().draw(game.getBatch(), "Moves: " + moveCount, x, y -= lineH);
        game.getFont().draw(game.getBatch(),
                "FPS: " + Gdx.graphics.getFramesPerSecond(), x, y - lineH);

        if (blockedTimer > 0f) {
            game.getFont().setColor(Color.RED);
            game.getFont().draw(game.getBatch(), "[BLOCKED]", x, y - lineH * 2);
            game.getFont().setColor(Color.WHITE);
        }
    }

    // -------------------------------------------------------------------------
    // UI
    // -------------------------------------------------------------------------

    private Skin buildSkin() {
        Skin s = new Skin();
        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = game.getFont();
        s.add("default", labelStyle);
        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        s.add("default", scrollStyle);
        return s;
    }

    private void initUI() {
        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        Table westPane = new Table(skin);
        root.add(westPane).width(WEST_PANE_W).fillY();

        root.add(new Container<>()).expand().fill().row();

        Table southPane = new Table(skin);
        logLabel = new Label("Exploring the world...", skin);
        logLabel.setWrap(true);
        southPane.add(new ScrollPane(logLabel, skin)).expand().fill().pad(10);
        root.add(southPane).colspan(2).height(SOUTH_PANE_H).fillX();
    }

    private void rebuildHudProjection(int width, int height) {
        hudProjection.setToOrtho2D(0, 0, width, height);
    }
}