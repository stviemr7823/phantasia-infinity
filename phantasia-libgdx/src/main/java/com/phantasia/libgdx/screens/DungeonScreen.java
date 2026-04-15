package com.phantasia.libgdx.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.world.DungeonFloor;
import com.phantasia.core.world.DungeonFloor.TileType;
import com.phantasia.core.world.DungeonFloorGenerator;
import com.phantasia.core.world.WorldPosition;
import com.phantasia.libgdx.PhantasiaGame;

/**
 * Fully playable dungeon exploration screen for the LibGDX frontend.
 *
 * <p>Matches {@code phantasia-j2d/tour/DungeonPanel} in feature scope:
 * <ul>
 *   <li>Multi-layer rendering: void black → memory tiles (30% alpha) →
 *       active tiles (100%) → torchlight vignette → player token → HUD.
 *   <li>WASD / arrow-key movement with dungeon Y-down coordinate system.
 *   <li>Fog-of-war: only explored tiles are drawn.
 *   <li>Torch radius of 3 tiles; beyond that, explored tiles render
 *       at {@link #MEMORY_ALPHA} as "dungeon memory".
 *   <li>Feature markers: ▲ for STAIRS_UP, ▼ for STAIRS_DOWN, gold
 *       square for CHEST.
 *   <li>Random encounter timer; combat launches {@link CombatScreen}
 *       with {@code this} as the return screen so exploration resumes.
 *   <li>ESC / STAIRS_UP (after ≥1 step) exits to the overworld.
 * </ul>
 *
 * <h3>Coordinate system</h3>
 * Dungeons use Y-down (row 0 is the north edge of the grid, row N is
 * south).  The map camera is configured with {@code setToOrtho(true,...)}
 * so world tile (tx, ty) maps directly to screen position
 * {@code (tx * TILE_SIZE, ty * TILE_SIZE)} without a Y-flip.  The HUD
 * and player token use a separate Y-up projection so that
 * {@code BitmapFont} draws text right-side-up.
 *
 * <h3>Torchlight vignette</h3>
 * A 256×256 RGBA Pixmap is built once per {@link #show()} call (and on
 * {@link #resize}).  It contains a radial gradient from transparent at
 * the centre to opaque black at the edges.  The transparent-zone radius
 * is computed from the actual screen dimensions so the lit area always
 * corresponds to {@link #TORCH_PIXEL_RADIUS} regardless of window size.
 * The texture is drawn at full screen size via SpriteBatch with standard
 * alpha blending, producing the vignette without a FrameBuffer.
 *
 * <h3>Returning from combat</h3>
 * {@link CombatScreen} is created with {@code returnScreen = this}, so
 * when combat ends the engine calls {@link #show()} on this screen again.
 * A {@code floorGenerated} flag prevents {@link #generateFloor()} from
 * running a second time — exploration state is fully preserved.
 */
public class DungeonScreen implements Screen {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Pixels per dungeon tile.  64 matches j2d DungeonPanel exactly. */
    private static final float TILE_SIZE    = 64f;

    /** Number of tiles with full-brightness visibility around the player. */
    private static final int   TORCH_RADIUS = 3;

    /**
     * World-pixel radius of the torchlight gradient.
     * Matches {@code DungeonPanel.TORCH_PIXEL_RADIUS = TILE_SIZE * (TORCH_RADIUS + 1.2f)}.
     */
    private static final float TORCH_PIXEL_RADIUS = TILE_SIZE * (TORCH_RADIUS + 1.2f);

    /** Alpha of explored-but-out-of-range ("memory") tiles. */
    private static final float MEMORY_ALPHA = 0.30f;

    /** How long the "BLOCKED" flash persists in seconds. */
    private static final float BLOCKED_FLASH_S = 0.25f;

    /** Steps between random encounters (base — actual interval is randomised). */
    private static final int BASE_ENCOUNTER_INTERVAL = 18;

    // -------------------------------------------------------------------------
    // Tile colors  (mirrors j2d DungeonPanel palette)
    // -------------------------------------------------------------------------

    private static final Color C_FLOOR      = new Color(0.165f, 0.149f, 0.196f, 1f);
    private static final Color C_WALL       = new Color(0.267f, 0.243f, 0.306f, 1f);
    private static final Color C_DOOR       = new Color(0.471f, 0.353f, 0.216f, 1f);
    private static final Color C_STAIRS_UP  = new Color(0.314f, 0.706f, 0.392f, 1f);
    private static final Color C_STAIRS_DN  = new Color(0.706f, 0.314f, 0.314f, 1f);
    private static final Color C_CHEST      = new Color(0.784f, 0.686f, 0.235f, 1f);
    private static final Color C_TRAP       = new Color(0.627f, 0.196f, 0.196f, 1f);
    private static final Color C_VOID       = new Color(0f,     0f,     0f,     1f);

    // UI colors
    private static final Color C_HUD_BG     = new Color(0f,     0f,     0f,     0.72f);
    private static final Color C_HUD_FG     = new Color(0.843f, 0.804f, 0.725f, 1f);
    private static final Color C_PURPLE     = new Color(0.608f, 0.333f, 0.824f, 1f);
    private static final Color C_RED        = new Color(0.863f, 0.294f, 0.275f, 1f);
    private static final Color C_GRID       = new Color(1f,     1f,     1f,     0.06f);
    private static final Color C_TOKEN_FILL = new Color(1f,     0.902f, 0.235f, 1f);
    private static final Color C_TOKEN_RIM  = new Color(0.784f, 0.510f, 0.078f, 1f);
    private static final Color C_SHADOW     = new Color(0f,     0f,     0f,     0.40f);

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final PhantasiaGame game;
    private final Screen        returnScreen;
    private final GameSession   session;
    private final int           dungeonId;
    private final String        dungeonName;

    private final ShapeRenderer sr;

    /**
     * Y-down orthographic camera for world-space tile rendering.
     * Rebuilt on each {@link #show()} and {@link #resize} so the viewport
     * always matches the current window dimensions.
     */
    private OrthographicCamera mapCamera;

    /**
     * Y-up projection matrix for all HUD and player token drawing.
     * LibGDX's BitmapFont and SpriteBatch expect Y-up by default.
     */
    private final Matrix4 hudProj = new Matrix4();

    /**
     * Radial-gradient vignette texture — transparent centre to opaque black
     * edges.  Rebuilt on each {@link #show()} and {@link #resize} so the
     * torch-radius proportion stays correct if the window is resized while
     * combat is active.
     */
    private Texture vignetteTexture;

    // Dungeon state
    private DungeonFloor  currentFloor;
    private WorldPosition savedOverworldPos;
    private int           stepCount      = 0;
    private int           encounterTimer = BASE_ENCOUNTER_INTERVAL;

    /**
     * Guards against re-generating the floor when {@link #show()} is called
     * a second time after returning from {@link CombatScreen}.
     */
    private boolean floorGenerated = false;

    // Input feedback
    private float blockedTimer = 0f;

    // -------------------------------------------------------------------------
    // Construction — same signature as old stub; callers need no changes
    // -------------------------------------------------------------------------

    /**
     * @param game         shared game instance
     * @param returnScreen screen to restore when the player exits the dungeon
     * @param dungeonId    feature ID of the dungeon entrance tile (for logging)
     * @param dungeonName  display name shown in the HUD
     */
    public DungeonScreen(PhantasiaGame game, Screen returnScreen,
                         int dungeonId, String dungeonName) {
        this.game         = game;
        this.returnScreen = returnScreen;
        this.session      = game.getSession();
        this.dungeonId    = dungeonId;
        this.dungeonName  = dungeonName;
        this.sr           = new ShapeRenderer();
    }

    // -------------------------------------------------------------------------
    // Screen lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void show() {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();

        setupCamera(w, h);
        rebuildHudProj(w, h);

        // Rebuild the vignette texture every show() so the torch proportion is
        // correct even after a resize that occurred while CombatScreen was active.
        if (vignetteTexture != null) vignetteTexture.dispose();
        vignetteTexture = buildVignetteTexture(w, h);

        // Generate the floor only on first entry — not on return from combat.
        if (!floorGenerated) {
            generateFloor();
            floorGenerated = true;
        }

        registerInput();

        System.out.println("[DungeonScreen] show()  dungeon=" + dungeonName
                + "  id=" + dungeonId
                + "  floor=" + (currentFloor == null ? "null"
                : currentFloor.getWidth() + "x" + currentFloor.getHeight())
                + "  pos=" + session.getWorldPosition());
    }

    @Override
    public void render(float delta) {
        if (currentFloor == null) return;
        if (blockedTimer > 0f) blockedTimer -= delta;

        WorldPosition player = session.getWorldPosition();

        // Centre the Y-down camera on the player's world-pixel position
        mapCamera.position.set(
                player.x() * TILE_SIZE + TILE_SIZE / 2f,
                player.y() * TILE_SIZE + TILE_SIZE / 2f,
                0f);
        mapCamera.update();

        ScreenUtils.clear(0f, 0f, 0f, 1f);

        // Rendering order matches j2d DungeonPanel.paintComponent():
        //   1+2. Tiles (memory + active layers, alpha-blended)
        //   3.   Torchlight vignette (screen-space overlay)
        //   4.   Player token (always screen centre)
        //   5.   HUD
        renderTiles(player);
        drawVignette();
        drawPlayer();
        drawHud(player);
    }

    @Override
    public void resize(int width, int height) {
        setupCamera(width, height);
        rebuildHudProj(width, height);
        // Rebuild vignette immediately — the screen is currently visible.
        if (vignetteTexture != null) vignetteTexture.dispose();
        vignetteTexture = buildVignetteTexture(width, height);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        sr.dispose();
        if (vignetteTexture != null) { vignetteTexture.dispose(); vignetteTexture = null; }
        floorGenerated = false;
    }

    @Override public void pause()  {}
    @Override public void resume() {}

    // -------------------------------------------------------------------------
    // Camera / projection setup
    // -------------------------------------------------------------------------

    /**
     * Configures a Y-down orthographic camera sized to the window.
     * With yDown=true, world tile (tx, ty) draws at screen position
     * (tx * TILE_SIZE, ty * TILE_SIZE) without a Y-flip — dungeons
     * use Y-down natively (row 0 = north).
     */
    private void setupCamera(int w, int h) {
        mapCamera = new OrthographicCamera();
        mapCamera.setToOrtho(true, w, h);   // yDown = true
        mapCamera.zoom = 1f;
        mapCamera.update();
    }

    private void rebuildHudProj(int w, int h) {
        hudProj.setToOrtho2D(0, 0, w, h);  // Y-up for BitmapFont compatibility
    }

    // -------------------------------------------------------------------------
    // Floor generation
    // -------------------------------------------------------------------------

    private void generateFloor() {
        // Capture the overworld tile so we can restore it on exit.
        savedOverworldPos = session.getWorldPosition();

        DungeonFloor floor = DungeonFloorGenerator.generate(50, 50, 12);
        currentFloor = floor;

        // Place the player at the entry stairs.
        WorldPosition start = floor.findStairsUp();
        if (start == null) {
            start = new WorldPosition(floor.getWidth() / 2, floor.getHeight() / 2);
        }
        session.setPosition(start);
        floor.updateExploration(start.x(), start.y(), TORCH_RADIUS);

        // Register with session so NavigationManager can query dungeon state.
        session.setCurrentDungeonFloor(floor);

        stepCount      = 0;
        encounterTimer = BASE_ENCOUNTER_INTERVAL;
        blockedTimer   = 0f;

        System.out.println("[DungeonScreen] Generated "
                + floor.getWidth() + "x" + floor.getHeight()
                + " floor  entry=" + start);
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    private void registerInput() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                return handleKey(keycode);
            }
        });
    }

    private boolean handleKey(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            exitDungeon("Escaped from " + dungeonName);
            return true;
        }

        // Dungeon coordinate system is Y-down (matches j2d DungeonPanel):
        // "north" (up on screen) = y-1, "south" (down on screen) = y+1.
        WorldPosition current = session.getWorldPosition();
        WorldPosition target = switch (keycode) {
            case Input.Keys.UP,    Input.Keys.W ->
                    new WorldPosition(current.x(),     current.y() - 1);
            case Input.Keys.DOWN,  Input.Keys.S ->
                    new WorldPosition(current.x(),     current.y() + 1);
            case Input.Keys.LEFT,  Input.Keys.A ->
                    new WorldPosition(current.x() - 1, current.y());
            case Input.Keys.RIGHT, Input.Keys.D ->
                    new WorldPosition(current.x() + 1, current.y());
            default -> null;
        };
        if (target == null) return false;

        TileType tileType = currentFloor.getTile(target.x(), target.y());

        if (!tileType.isPassable()) {
            blockedTimer = BLOCKED_FLASH_S;
            return true;
        }

        // Commit the move.
        session.setPosition(target);
        currentFloor.updateExploration(target.x(), target.y(), TORCH_RADIUS);
        stepCount++;

        // Special tile interactions
        switch (tileType) {
            case STAIRS_UP -> {
                // Ascending on step > 1 means we are past the spawn tile.
                if (stepCount > 1) {
                    exitDungeon("Ascended stairs in " + dungeonName);
                    return true;
                }
            }
            case STAIRS_DOWN ->
                // Future: DungeonFloorGenerator.generate() next level, update depth
                    System.out.println("[DungeonScreen] STAIRS_DOWN at " + target
                            + " — deeper floors not yet implemented.");
            case CHEST ->
                // Future: LootManager.generateChestLoot(); award items / gold
                    System.out.println("[DungeonScreen] Chest at " + target
                            + " — loot not yet implemented.");
            case TRAP ->
                // Future: FormulaEngine.applyTrapDamage(session.getParty())
                    System.out.println("[DungeonScreen] Trap triggered at " + target
                            + " — trap damage not yet implemented.");
            default -> {}
        }

        // Encounter timer
        encounterTimer--;
        if (encounterTimer <= 0) {
            encounterTimer = BASE_ENCOUNTER_INTERVAL / 2
                    + (int)(Math.random() * BASE_ENCOUNTER_INTERVAL);
            triggerEncounter();
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Exit and encounters
    // -------------------------------------------------------------------------

    private void exitDungeon(String reason) {
        session.setCurrentDungeonFloor(null);
        if (savedOverworldPos != null) {
            session.setPosition(savedOverworldPos);
        }
        System.out.println("[DungeonScreen] Exit: " + reason);
        game.setScreen(returnScreen);
    }

    private void triggerEncounter() {
        System.out.println("[DungeonScreen] Random encounter at "
                + session.getWorldPosition());
        // CombatScreen returns to *this* screen so dungeon exploration resumes.
        game.setScreen(CombatScreen.forRandom(game, this, session));
    }

    // -------------------------------------------------------------------------
    // Rendering — tiles (layers 1 + 2)
    // -------------------------------------------------------------------------

    /**
     * Draws all explored tiles in three sub-passes:
     * <ol>
     *   <li>Tile fills — memory tiles at {@link #MEMORY_ALPHA}, active tiles
     *       at 1.0, in a single loop.
     *   <li>Subtle grid lines on active tiles only.
     *   <li>Feature markers (stairs, chests) at full brightness.
     * </ol>
     *
     * Frustum culling in tile space is applied before any draw call to keep
     * the render loop tight.
     */
    private void renderTiles(WorldPosition player) {
        int floorW = currentFloor.getWidth();
        int floorH = currentFloor.getHeight();
        int pw     = Gdx.graphics.getWidth();
        int ph     = Gdx.graphics.getHeight();

        // Tile-space frustum cull (Y-down, zoom=1: one tile = TILE_SIZE pixels)
        int halfTW = (int)Math.ceil(pw / (2f * TILE_SIZE)) + 1;
        int halfTH = (int)Math.ceil(ph / (2f * TILE_SIZE)) + 1;
        int x0 = Math.max(0,      player.x() - halfTW);
        int y0 = Math.max(0,      player.y() - halfTH);
        int x1 = Math.min(floorW, player.x() + halfTW + 1);
        int y1 = Math.min(floorH, player.y() + halfTH + 1);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        sr.setProjectionMatrix(mapCamera.combined);

        // Pass 1: tile fills (memory + active blended in one loop)
        sr.begin(ShapeType.Filled);
        for (int ty = y0; ty < y1; ty++) {
            for (int tx = x0; tx < x1; tx++) {
                if (!currentFloor.isExplored(tx, ty)) continue;
                TileType type = currentFloor.getTile(tx, ty);
                if (type == TileType.VOID) continue;

                float dist  = dst(tx - player.x(), ty - player.y());
                float alpha = dist <= TORCH_RADIUS + 0.5f ? 1f : MEMORY_ALPHA;

                Color c = tileColor(type);
                sr.setColor(c.r, c.g, c.b, alpha);
                sr.rect(tx * TILE_SIZE, ty * TILE_SIZE, TILE_SIZE, TILE_SIZE);
            }
        }
        sr.end();

        // Pass 2: subtle grid lines on active tiles only
        sr.begin(ShapeType.Line);
        for (int ty = y0; ty < y1; ty++) {
            for (int tx = x0; tx < x1; tx++) {
                if (!currentFloor.isExplored(tx, ty)) continue;
                if (dst(tx - player.x(), ty - player.y()) > TORCH_RADIUS + 0.5f) continue;
                TileType type = currentFloor.getTile(tx, ty);
                if (type == TileType.VOID) continue;
                sr.setColor(C_GRID);
                sr.rect(tx * TILE_SIZE, ty * TILE_SIZE, TILE_SIZE, TILE_SIZE);
            }
        }
        sr.end();

        // Pass 3: feature markers at full alpha
        sr.begin(ShapeType.Filled);
        for (int ty = y0; ty < y1; ty++) {
            for (int tx = x0; tx < x1; tx++) {
                if (!currentFloor.isExplored(tx, ty)) continue;
                drawFeatureMarker(currentFloor.getTile(tx, ty),
                        tx * TILE_SIZE, ty * TILE_SIZE);
            }
        }
        sr.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Draws a small shape marker for notable tile types.
     * Must be called with ShapeRenderer in {@link ShapeType#Filled} mode,
     * using the Y-down map camera projection.
     *
     * <p>In Y-down space, smaller Y is higher on screen, so the STAIRS_UP
     * triangle apex is placed at a smaller Y value to appear as "▲".
     *
     * @param type  tile type to mark (non-feature types are silently ignored)
     * @param sx    world-pixel X of the tile's top-left corner
     * @param sy    world-pixel Y of the tile's top-left corner (Y-down)
     */
    private void drawFeatureMarker(TileType type, float sx, float sy) {
        float cx   = sx + TILE_SIZE / 2f;
        float cy   = sy + TILE_SIZE / 2f;
        float half = TILE_SIZE * 0.20f;

        switch (type) {
            case STAIRS_UP -> {
                // Apex at smaller Y = higher on screen (upward-pointing ▲)
                sr.setColor(C_STAIRS_UP);
                sr.triangle(cx,         cy - half,
                        cx - half,  cy + half,
                        cx + half,  cy + half);
            }
            case STAIRS_DOWN -> {
                // Apex at larger Y = lower on screen (downward-pointing ▼)
                sr.setColor(C_STAIRS_DN);
                sr.triangle(cx,         cy + half,
                        cx - half,  cy - half,
                        cx + half,  cy - half);
            }
            case CHEST -> {
                // Gold square centred in the tile
                sr.setColor(C_CHEST);
                float cs = half * 1.1f;
                sr.rect(cx - cs / 2f, cy - cs / 2f, cs, cs);
            }
            default -> { /* no marker for this type */ }
        }
    }

    // -------------------------------------------------------------------------
    // Rendering — torchlight vignette (layer 3)
    // -------------------------------------------------------------------------

    /**
     * Draws the pre-built vignette texture at full screen size.
     *
     * <p>The texture's transparent centre aligns with the player (always at
     * screen centre), fading to opaque black at the edges — matching j2d's
     * {@code RadialGradientPaint} mask in {@code applyTorchlightMask()}.
     */
    private void drawVignette() {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        game.getBatch().setProjectionMatrix(hudProj);
        game.getBatch().begin();
        game.getBatch().setColor(Color.WHITE);
        game.getBatch().draw(vignetteTexture, 0, 0, w, h);
        game.getBatch().end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // -------------------------------------------------------------------------
    // Rendering — player token (layer 4)
    // -------------------------------------------------------------------------

    /**
     * Draws a circular gold party token always centred on the screen.
     *
     * <p>Because the camera follows the player every frame, the world scrolls
     * beneath the token rather than the token moving across the screen.
     * Uses the Y-up {@link #hudProj} so the circle geometry is unaffected
     * by the Y-down map camera.
     */
    private void drawPlayer() {
        int   sw     = Gdx.graphics.getWidth();
        int   sh     = Gdx.graphics.getHeight();
        float cx     = sw / 2f;
        float cy     = sh / 2f;
        float radius = TILE_SIZE * 0.25f;   // 16px — 50% of tile width

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        sr.setProjectionMatrix(hudProj);
        sr.begin(ShapeType.Filled);

        // Drop shadow
        sr.setColor(C_SHADOW);
        sr.circle(cx + 2f, cy - 3f, radius + 1.5f, 28);

        // Outer rim (dark gold)
        sr.setColor(C_TOKEN_RIM);
        sr.circle(cx, cy, radius + 2.5f, 28);

        // Main fill (yellow)
        sr.setColor(C_TOKEN_FILL);
        sr.circle(cx, cy, radius, 28);

        // Specular highlight — small bright oval offset to top-left
        sr.setColor(1f, 1f, 0.88f, 0.55f);
        sr.ellipse(cx - radius * 0.28f, cy + radius * 0.08f,
                radius * 0.38f,       radius * 0.24f);

        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // -------------------------------------------------------------------------
    // Rendering — HUD (layer 5)
    // -------------------------------------------------------------------------

    /**
     * Draws the top-left HUD panel: dungeon name, current tile and position,
     * step count, next-encounter estimate, and a BLOCKED flash on collision.
     */
    private void drawHud(WorldPosition pos) {
        int   w   = Gdx.graphics.getWidth();
        int   h   = Gdx.graphics.getHeight();
        float lh  = 20f;
        float padX = 10f;
        float padY = 10f;

        TileType standing = currentFloor.getTile(pos.x(), pos.y());

        String[] lines = {
                dungeonName.toUpperCase() + "  [id=" + dungeonId + "]",
                pos + "  [" + standing.name() + "]",
                "Steps: " + stepCount + "   Next enc: ~" + encounterTimer,
                blockedTimer > 0f
                        ? "[ BLOCKED — impassable ]"
                        : "WASD: move    ESC: exit dungeon"
        };

        float boxW = 380f;
        float boxH = lines.length * lh + 20f;
        // In Y-up coords, near the top of the screen
        float boxY = h - padY - boxH;

        // Translucent backing panel
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.setProjectionMatrix(hudProj);
        sr.begin(ShapeType.Filled);
        sr.setColor(C_HUD_BG);
        sr.rect(padX, boxY, boxW, boxH);
        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Text lines — draw top-to-bottom inside the panel
        game.getBatch().setProjectionMatrix(hudProj);
        game.getBatch().begin();

        float ty = boxY + boxH - lh;    // start at top row (high Y = near top)
        for (String line : lines) {
            Color c = line.startsWith(dungeonName.toUpperCase()) ? C_PURPLE
                    : line.startsWith("[")                       ? C_RED
                      : C_HUD_FG;
            game.getFont().setColor(c);
            game.getFont().draw(game.getBatch(), line, padX + 10f, ty);
            ty -= lh;
        }

        game.getBatch().end();
    }

    // -------------------------------------------------------------------------
    // Torchlight vignette texture
    // -------------------------------------------------------------------------

    /**
     * Builds a 256×256 RGBA Pixmap radial gradient:
     * fully transparent at the centre, fully opaque black at the edges.
     *
     * <p>The transparent-zone radius ({@code innerR}) is proportional to
     * {@link #TORCH_PIXEL_RADIUS} relative to the screen's smaller
     * half-extent, so the lit area always matches the intended torch size
     * regardless of window dimensions.
     *
     * <p>A cubic smoothstep curve produces the same soft-edge appearance as
     * j2d's four-stop {@code RadialGradientPaint}: gently transparent just
     * inside the torch edge, quickly darkening to full black near the corners.
     *
     * @param screenW  current window width in pixels
     * @param screenH  current window height in pixels
     * @return a new Texture; caller is responsible for disposal
     */
    private static Texture buildVignetteTexture(int screenW, int screenH) {
        int   size   = 256;
        float cx     = size / 2f;
        float cy     = size / 2f;
        float outerR = size / 2f;   // 128 — edge of inscribed circle

        // innerFrac: fraction of outerR that is fully transparent.
        // Derived from TORCH_PIXEL_RADIUS / (screen half-min-dimension) so
        // the lit zone matches the torch radius at any window size.
        float halfScreen = Math.min(screenW, screenH) / 2f;
        float innerFrac  = Math.min(0.88f, TORCH_PIXEL_RADIUS / halfScreen);
        float innerR     = outerR * innerFrac;

        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d = (float)Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                float t = (d - innerR) / (outerR - innerR);
                t = Math.max(0f, Math.min(1f, t));
                t = t * t * (3f - 2f * t);   // cubic smoothstep
                pm.setColor(0f, 0f, 0f, t);
                pm.drawPixel(x, y);
            }
        }

        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /**
     * Maps a {@link TileType} to its display color.
     * Exhaustive over all eight enum values.
     */
    private static Color tileColor(TileType type) {
        return switch (type) {
            case FLOOR       -> C_FLOOR;
            case WALL        -> C_WALL;
            case DOOR        -> C_DOOR;
            case STAIRS_UP   -> C_STAIRS_UP;
            case STAIRS_DOWN -> C_STAIRS_DN;
            case CHEST       -> C_CHEST;
            case TRAP        -> C_TRAP;
            case VOID        -> C_VOID;
        };
    }

    /**
     * Euclidean distance between two integer tile offsets.
     * Zero-allocation — safe to call in the hot render loop.
     */
    private static float dst(int dx, int dy) {
        return (float)Math.sqrt((double)(dx * dx + dy * dy));
    }
}