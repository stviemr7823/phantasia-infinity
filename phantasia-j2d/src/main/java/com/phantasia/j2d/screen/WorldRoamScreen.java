// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/WorldRoamScreen.java
package com.phantasia.j2d.screen;

import com.phantasia.core.data.EncounterFactory;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;
import com.phantasia.core.world.*;
import com.phantasia.core.data.SpellFactory;
import com.phantasia.j2d.engine.*;
import com.phantasia.j2d.render.ParchmentRenderer;
import com.phantasia.j2d.tour.KenneyTileLoader;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.phantasia.j2d.render.ParchmentRenderer.*;

/**
 * The primary gameplay screen — free-roam world exploration.
 *
 * The player moves with float coordinates via WASD/arrow keys.
 * Tile logic (encounters, features, NPCs) fires on boundary crossings.
 * The camera follows the player, keeping them centered.
 *
 * <h3>Rendering layers (back to front):</h3>
 * <ol>
 *   <li>Tile grid (from WorldMap, via KenneyTileLoader or fallback colors)</li>
 *   <li>Feature markers (towns, dungeons) with name labels</li>
 *   <li>Player sprite (centered)</li>
 *   <li>HUD overlay (compass, party health strip, location label)</li>
 * </ol>
 */
public class WorldRoamScreen implements Screen {

    // ─────────────────────────────────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────────────────────────────────

    private static final int   TILE_SIZE      = 64;
    private static final float MOVE_SPEED     = 128f;  // pixels per second (2 tiles/sec)
    private static final int   PLAYER_SIZE    = 24;

    // ─────────────────────────────────────────────────────────────────────
    // Dependencies
    // ─────────────────────────────────────────────────────────────────────

    private final ScreenManager     screenManager;
    private final WorldMap          worldMap;
    private final GameSession       session;
    private final NavigationManager navManager;
    private final FeatureRegistry   featureRegistry;  // nullable
    private final KenneyTileLoader  tileLoader;
    private final EncounterTimer    encounterTimer;
    private final SpellFactory      spellFactory;

    // ─────────────────────────────────────────────────────────────────────
    // Movement state
    // ─────────────────────────────────────────────────────────────────────

    private FloatPosition playerPos;
    private WorldPosition currentTile;

    // Directional input (held keys)
    private final Set<Integer> heldKeys = new HashSet<>();

    // ─────────────────────────────────────────────────────────────────────
    // HUD state
    // ─────────────────────────────────────────────────────────────────────

    private String locationLabel = "";
    private String interactPrompt = "";
    private float  promptAlpha = 0f;

    // ─────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────

    public WorldRoamScreen(ScreenManager screenManager, WorldMap worldMap,
                           GameSession session, NavigationManager navManager,
                           FeatureRegistry featureRegistry, KenneyTileLoader tileLoader,
                           SpellFactory spellFactory) {
        this.screenManager   = screenManager;
        this.worldMap        = worldMap;
        this.session         = session;
        this.navManager      = navManager;
        this.featureRegistry = featureRegistry;
        this.tileLoader      = tileLoader;
        this.spellFactory    = spellFactory;
        this.encounterTimer  = new EncounterTimer();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Screen lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onEnter(Object transitionData) {
        // Initialize float position from session's tile position
        WorldPosition wp = session.getPosition();
        playerPos   = FloatPosition.fromTileCenter(wp, TILE_SIZE);
        currentTile = wp;
        heldKeys.clear();
        updateLocationLabel();
    }

    @Override
    public void update(float dt) {
        // ── Compute movement delta from held keys ──────────────────
        float dx = 0f, dy = 0f;
        if (heldKeys.contains(KeyEvent.VK_W) || heldKeys.contains(KeyEvent.VK_UP))    dy -= MOVE_SPEED * dt;
        if (heldKeys.contains(KeyEvent.VK_S) || heldKeys.contains(KeyEvent.VK_DOWN))  dy += MOVE_SPEED * dt;
        if (heldKeys.contains(KeyEvent.VK_A) || heldKeys.contains(KeyEvent.VK_LEFT))  dx -= MOVE_SPEED * dt;
        if (heldKeys.contains(KeyEvent.VK_D) || heldKeys.contains(KeyEvent.VK_RIGHT)) dx += MOVE_SPEED * dt;

        if (dx == 0f && dy == 0f) return;

        // ── Attempt float move ─────────────────────────────────────
        FloatMoveResult result = navManager.move(playerPos, dx, dy, worldMap, TILE_SIZE);

        if (!result.blocked()) {
            playerPos = result.newPosition();
        }

        // ── Boundary crossing logic ────────────────────────────────
        if (result.crossedBoundary()) {
            currentTile = result.newTile();
            session.setPosition(currentTile);
            onTileCrossing(currentTile);
        }

        // ── HUD updates ────────────────────────────────────────────
        updateInteractPrompt();
        if (promptAlpha > 0) promptAlpha = Math.min(1f, promptAlpha + dt * 3f);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tile crossing — encounters, features, NPCs
    // ─────────────────────────────────────────────────────────────────────

    private void onTileCrossing(WorldPosition tile) {
        Tile mapTile = worldMap.getTile(tile);
        TileType terrain = mapTile.getType();

        updateLocationLabel();

        // Feature detection
        WorldFeature feature = mapTile.getFeature();
        if (feature != null && feature.getType() != FeatureType.NONE) {
            onFeatureDetected(feature, tile);
            return;  // Don't trigger random encounters on feature tiles
        }

        // Encounter roll
        if (encounterTimer.step(terrain)) {
            triggerEncounter();
        }
    }

    private void onFeatureDetected(WorldFeature feature, WorldPosition tile) {
        if (featureRegistry == null) return;

        FeatureRecord record = featureRegistry.get(feature.getType(), feature.getId());
        if (record == null) return;

        switch (feature.getType()) {
            case TOWN -> {
                interactPrompt = "Press E to enter " + record.getName();
                promptAlpha = 0.01f;  // triggers fade-in
            }
            case DUNGEON -> {
                interactPrompt = "Press E to enter " + record.getName();
                promptAlpha = 0.01f;
            }
            case NPC -> {
                interactPrompt = "Press E to speak with " + record.getName();
                promptAlpha = 0.01f;
            }
            default -> {}
        }
    }

    private void triggerEncounter() {
        List<Monster> enemies = EncounterFactory.generateEncounter();
        EncounterCondition condition = FormulaEngine.rollEncounterCondition();

        EncounterSplashScreen.EncounterData encounterData =
                new EncounterSplashScreen.EncounterData(
                        enemies, condition, session.getParty()
                );

        screenManager.fadeTo(GameState.ENCOUNTER_SPLASH, encounterData);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void render(Graphics2D g) {
        int W = GameCanvas.DESIGN_WIDTH;
        int H = GameCanvas.DESIGN_HEIGHT;

        // Camera offset — player at screen center
        float camX = playerPos.x() - W / 2f;
        float camY = playerPos.y() - H / 2f;

        // Void background
        g.setColor(VOID);
        g.fillRect(0, 0, W, H);

        // ── Tile grid ──────────────────────────────────────────────
        int startTileX = Math.max(0, (int)(camX / TILE_SIZE) - 1);
        int startTileY = Math.max(0, (int)(camY / TILE_SIZE) - 1);
        int endTileX   = Math.min(worldMap.getWidth(),  startTileX + W / TILE_SIZE + 3);
        int endTileY   = Math.min(worldMap.getHeight(), startTileY + H / TILE_SIZE + 3);

        for (int ty = startTileY; ty < endTileY; ty++) {
            for (int tx = startTileX; tx < endTileX; tx++) {
                int screenX = (int)(tx * TILE_SIZE - camX);
                int screenY = (int)(ty * TILE_SIZE - camY);

                Tile tile = worldMap.getTile(tx, ty);
                TileType type = tile.getType();

                // Try Kenney tile image
                BufferedImage tileImg = (tileLoader != null) ? tileLoader.getTile(type) : null;
                if (tileImg != null) {
                    g.drawImage(tileImg, screenX, screenY, TILE_SIZE, TILE_SIZE, null);
                } else {
                    g.setColor(fallbackColor(type));
                    g.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
                }

                // Feature marker
                WorldFeature feat = tile.getFeature();
                if (feat != null && feat.getType() != FeatureType.NONE) {
                    renderFeatureMarker(g, feat, screenX, screenY);
                }
            }
        }

        // ── Player sprite ──────────────────────────────────────────
        int playerScreenX = W / 2 - PLAYER_SIZE / 2;
        int playerScreenY = H / 2 - PLAYER_SIZE / 2;

        // Gold diamond for the player
        g.setColor(GOLD_BRIGHT);
        int[] xPts = { playerScreenX + PLAYER_SIZE/2, playerScreenX + PLAYER_SIZE,
                playerScreenX + PLAYER_SIZE/2, playerScreenX };
        int[] yPts = { playerScreenY, playerScreenY + PLAYER_SIZE/2,
                playerScreenY + PLAYER_SIZE, playerScreenY + PLAYER_SIZE/2 };
        g.fillPolygon(xPts, yPts, 4);

        g.setColor(GOLD);
        g.drawPolygon(xPts, yPts, 4);

        // ── HUD ────────────────────────────────────────────────────
        renderHUD(g, W, H);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Feature markers on tiles
    // ─────────────────────────────────────────────────────────────────────

    private void renderFeatureMarker(Graphics2D g, WorldFeature feat, int sx, int sy) {
        int cs = TILE_SIZE / 3;
        int cx = sx + TILE_SIZE / 2 - cs / 2;
        int cy = sy + TILE_SIZE / 2 - cs / 2;

        Color color = switch (feat.getType()) {
            case TOWN    -> GOLD_BRIGHT;
            case DUNGEON -> DANGER;
            case NPC     -> MAGIC;
            default      -> BORDER_STR;
        };

        g.setColor(color);
        g.fillRoundRect(cx, cy, cs, cs, 4, 4);
        g.setColor(new Color(0, 0, 0, 80));
        g.drawRoundRect(cx, cy, cs, cs, 4, 4);

        // Label
        if (featureRegistry != null) {
            FeatureRecord rec = featureRegistry.get(feat.getType(), feat.getId());
            if (rec != null) {
                drawTextCentered(g, rec.getName(), sx - 20, sy - 8,
                        TILE_SIZE + 40, TextStyle.DATA, color);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // HUD overlay
    // ─────────────────────────────────────────────────────────────────────

    private void renderHUD(Graphics2D g, int W, int H) {

        // ── Location label (top-left) ──────────────────────────────
        if (!locationLabel.isEmpty()) {
            int lw = 280, lh = 50;
            g.setColor(new Color(42, 33, 24, 216));
            g.fillRoundRect(20, 16, lw, lh, 6, 6);
            g.setColor(BORDER_STR);
            g.drawRoundRect(20, 16, lw, lh, 6, 6);
            drawText(g, locationLabel, 36, 48, TextStyle.LABEL, GOLD);
        }

        // ── Compass (top-right) ────────────────────────────────────
        int compassX = W - 100, compassY = 20, compassR = 40;
        g.setColor(new Color(42, 33, 24, 216));
        g.fillOval(compassX, compassY, compassR * 2, compassR * 2);
        g.setColor(GOLD);
        g.drawOval(compassX, compassY, compassR * 2, compassR * 2);
        drawTextCentered(g, "N", compassX, compassY + 22, compassR * 2,
                TextStyle.HEADER, GOLD_BRIGHT);
        String coords = currentTile.x() + ", " + currentTile.y();
        drawTextCentered(g, coords, compassX, compassY + 54, compassR * 2,
                TextStyle.DATA, new Color(0x8A7F6E));

        // ── Party health strip (bottom-left) ───────────────────────
        int stripX = 20, stripY = H - 56;
        List<PlayerCharacter> party = session.getParty();
        for (int i = 0; i < party.size(); i++) {
            PlayerCharacter pc = party.get(i);
            if (!pc.isAlive()) continue;

            int px = stripX + i * 116;
            g.setColor(new Color(42, 33, 24, 216));
            g.fillRoundRect(px, stripY, 108, 48, 6, 6);
            g.setColor(BORDER_STR);
            g.drawRoundRect(px, stripY, 108, 48, 6, 6);

            drawText(g, pc.getName(), px + 8, stripY + 16, TextStyle.DATA, GOLD);
            drawHealthBar(g, pc.getHp(), pc.getStat(Stat.MAX_HP),
                    px + 8, stripY + 22, 92, 6);
            drawManaBar(g, pc.getStat(Stat.MAGIC_POWER), pc.getStat(Stat.MAX_MAGIC),
                    px + 8, stripY + 32, 92, 6);
        }

        // ── Interaction prompt (bottom-center) ─────────────────────
        if (promptAlpha > 0.01f && !interactPrompt.isEmpty()) {
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    Math.min(1f, promptAlpha)));

            int pw = 400, ph = 36;
            int ppx = (W - pw) / 2;
            int ppy = H - 60;
            g.setColor(new Color(42, 33, 24, 230));
            g.fillRoundRect(ppx, ppy, pw, ph, 6, 6);
            g.setColor(GOLD);
            g.drawRoundRect(ppx, ppy, pw, ph, 6, 6);
            drawTextCentered(g, interactPrompt, ppx, ppy + 24, pw,
                    TextStyle.LABEL, GOLD_BRIGHT);

            g.setComposite(old);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onKeyPressed(KeyEvent e) {
        heldKeys.add(e.getKeyCode());

        if (e.getKeyCode() == KeyEvent.VK_E) {
            handleInteract();
        }
    }

    @Override
    public void onKeyReleased(KeyEvent e) {
        heldKeys.remove(e.getKeyCode());
    }

    @Override
    public void onMouseClicked(MouseEvent e) {}

    @Override
    public void onExit() {
        heldKeys.clear();
        promptAlpha = 0f;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Interaction (E key)
    // ─────────────────────────────────────────────────────────────────────

    private void handleInteract() {
        Tile tile = worldMap.getTile(currentTile);
        WorldFeature feat = tile.getFeature();
        if (feat == null || feat.getType() == FeatureType.NONE) return;
        if (featureRegistry == null) return;

        FeatureRecord record = featureRegistry.get(feat.getType(), feat.getId());
        if (record == null) return;

        switch (feat.getType()) {
            case TOWN, DUNGEON -> {
                // TODO: transition to town interior / dungeon roam
                System.out.println("[WorldRoam] Entering: " + record.getName());
            }
            case NPC -> {
                // Push dialogue overlay
                DialogueContext ctx = DialogueContext.freeze(null, session);
                DialogueScreen.DialogueData dlgData = new DialogueScreen.DialogueData(
                        null, session, ctx, List.of()  // NPC def lookup needed
                );
                screenManager.push(GameState.DIALOGUE, dlgData);
            }
            default -> {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private void updateLocationLabel() {
        Tile tile = worldMap.getTile(currentTile);
        WorldFeature feat = tile.getFeature();

        if (feat != null && feat.getType() != FeatureType.NONE && featureRegistry != null) {
            FeatureRecord rec = featureRegistry.get(feat.getType(), feat.getId());
            if (rec != null) {
                locationLabel = rec.getName();
                return;
            }
        }

        locationLabel = switch (tile.getType()) {
            case OCEAN    -> "Ocean";
            case ROAD     -> "Road";
            case PLAINS   -> "Open Plains";
            case FOREST   -> "Dense Forest";
            case MOUNTAIN -> "Mountain Pass";
            case SWAMP    -> "Murky Swamp";
            case TOWN     -> "Settlement";
            case DUNGEON  -> "Dark Entrance";
        };
    }

    private void updateInteractPrompt() {
        Tile tile = worldMap.getTile(currentTile);
        WorldFeature feat = tile.getFeature();
        if (feat == null || feat.getType() == FeatureType.NONE) {
            promptAlpha = Math.max(0, promptAlpha - 0.05f);
        }
    }

    /** Solid colours when tile images are unavailable — matches KenneyTileLoader palette. */
    private static Color fallbackColor(TileType type) {
        return switch (type) {
            case OCEAN    -> new Color( 25,  50, 140);
            case ROAD     -> new Color(185, 160, 110);
            case PLAINS   -> new Color( 85, 160,  58);
            case FOREST   -> new Color( 22,  95,  22);
            case MOUNTAIN -> new Color(118, 155, 160);
            case SWAMP    -> new Color( 72,  98,  48);
            case TOWN     -> new Color(210, 185,  72);
            case DUNGEON  -> new Color( 30,  10,  45);
        };
    }
}