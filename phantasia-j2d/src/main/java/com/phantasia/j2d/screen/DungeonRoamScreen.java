// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/DungeonRoamScreen.java
package com.phantasia.j2d.screen;

import com.phantasia.core.data.EncounterFactory;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.logic.FormulaEngine;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.Stat;
import com.phantasia.core.world.*;
import com.phantasia.j2d.engine.*;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.phantasia.j2d.render.ParchmentRenderer.*;

/**
 * Dungeon roam screen — tile-based interior movement with fog of war.
 *
 * <h3>Rendering layers</h3>
 * <ol>
 *   <li>Void fill — unexplored tiles show nothing</li>
 *   <li>Memory layer — explored tiles at 30% opacity ("you've been here")</li>
 *   <li>Active layer — tiles within torch radius at 100%</li>
 *   <li>Torchlight mask — RadialGradientPaint vignette centered on player</li>
 *   <li>Player avatar — centered, always on top</li>
 *   <li>HUD — location label, party health strip, tile interaction hint</li>
 * </ol>
 *
 * <h3>Movement model</h3>
 * Tile-snapped (one key press = one tile). {@code MOVE_COOLDOWN} prevents
 * held-key warping. No float drift — feels right in tight dungeon corridors.
 *
 * <h3>Encounters</h3>
 * A step counter ticks on every FLOOR or DOOR move. When it reaches the
 * threshold (randomised 12–24 steps), a random encounter fires.
 *
 * <h3>Overworld position</h3>
 * The party's overworld position is saved on entry and restored on exit so
 * WorldRoamScreen always has valid coordinates when it resumes.
 */
public class DungeonRoamScreen implements Screen {

    // ─────────────────────────────────────────────────────────────────────
    // Transition data
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @param dungeonName   HUD label (updated per floor: "Frostpeak — Floor 2")
     * @param floor         the DungeonFloor to explore
     * @param floorNumber   1-based floor index (1 = entry level)
     * @param floorStack    floors above this one, outermost at index 0 (empty on floor 1)
     * @param spawnOverride explicit spawn position, or null to use findStairsUp()
     * @param session       live game session
     * @param returnState   GameState to fadeTo when ascending from floor 1
     */
    public record DungeonData(
            String             dungeonName,
            DungeonFloor       floor,
            int                floorNumber,
            java.util.List<DungeonFloor> floorStack,
            WorldPosition      spawnOverride,
            GameSession        session,
            GameState          returnState
    ) {
        /** Convenience constructor for entering floor 1 (no stack, no override). */
        public DungeonData(String dungeonName, DungeonFloor floor,
                           GameSession session, GameState returnState) {
            this(dungeonName, floor, 1,
                    java.util.List.of(), null, session, returnState);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Layout / rendering constants (design resolution 1920×1080)
    // ─────────────────────────────────────────────────────────────────────

    private static final int   TILE_SIZE          = 32;   // px per tile
    private static final int   TORCH_RADIUS       = 3;    // tiles illuminated
    /** Pixel radius of the torch vignette gradient. */
    private static final float TORCH_PIXEL_RADIUS =
            TILE_SIZE * (TORCH_RADIUS + 1.5f);            // ~144 px

    // Tile fill colours
    private static final Color C_FLOOR      = new Color( 74,  62,  46);
    private static final Color C_WALL       = new Color( 30,  26,  21);
    private static final Color C_DOOR       = new Color(139, 108,  58);
    private static final Color C_STAIRS_UP  = new Color( 80, 180, 100);
    private static final Color C_STAIRS_DN  = new Color( 90, 120, 190);
    private static final Color C_CHEST      = new Color(191, 163,  76);
    private static final Color C_TRAP       = new Color(160,  50,  50);
    private static final Color C_PLAYER     = new Color(232, 201, 106);

    /** Memory-layer opacity for explored-but-dark tiles. */
    private static final float  ALPHA_DIM    = 0.28f;

    /** Seconds between allowed steps when a key is held. */
    private static final float  MOVE_COOLDOWN = 0.13f;

    /** Steps between encounters — range gives natural variance. */
    private static final int    ENC_MIN      = 12;
    private static final int    ENC_MAX      = 24;

    // ─────────────────────────────────────────────────────────────────────
    // Dependencies
    // ─────────────────────────────────────────────────────────────────────

    private final ScreenManager screenManager;

    // ─────────────────────────────────────────────────────────────────────
    // Per-enter state  (all initialised in onEnter)
    // ─────────────────────────────────────────────────────────────────────

    private DungeonData    data;
    private WorldPosition  playerPos;
    private WorldPosition  savedOverworldPos;   // restored when exiting
    private Set<Integer>   heldKeys;
    private float          moveCooldown;
    private int            stepsUntilEncounter;
    private int            stepCount;           // total steps taken this session

    private final WorldClock     clock;
    private final WeatherManager weatherManager;

    // ─────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────

    public DungeonRoamScreen(ScreenManager screenManager, WorldClock clock,
                             WeatherManager weatherManager) {
        this.screenManager = screenManager;
        this.clock          = clock;
        this.weatherManager = weatherManager;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Screen lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onEnter(Object transitionData) {
        this.data      = (DungeonData) transitionData;
        this.heldKeys  = new HashSet<>();
        moveCooldown   = 0f;
        stepCount      = 0;
        stepsUntilEncounter = nextEncounterThreshold();

        // Save overworld position so we can restore it on exit
        savedOverworldPos = data.session().getWorldPosition();

        // Place player at STAIRS_UP; fall back to (1,1)
        WorldPosition stairs = data.floor().findStairsUp();
        playerPos = (stairs != null) ? stairs : new WorldPosition(1, 1);

        // Register in session
        data.session().setCurrentDungeonFloor(data.floor());
        data.session().setPosition(playerPos);

        // Reveal starting area
        data.floor().updateExploration(playerPos.x(), playerPos.y(), TORCH_RADIUS);
    }

    @Override
    public void onExit() {
        // Guard: onExit can theoretically fire before onEnter on first registration
        if (data == null) return;
        data.session().setCurrentDungeonFloor(null);
        // Restore overworld position so WorldRoamScreen gets valid tile coords
        if (savedOverworldPos != null) {
            data.session().setPosition(savedOverworldPos);
        }
    }

    @Override
    public void update(float dt) {
        if (moveCooldown > 0f) {
            moveCooldown -= dt;
            return;
        }

        int dx = 0, dy = 0;
        if (heldKeys.contains(KeyEvent.VK_W) || heldKeys.contains(KeyEvent.VK_UP))    dy = -1;
        if (heldKeys.contains(KeyEvent.VK_S) || heldKeys.contains(KeyEvent.VK_DOWN))  dy =  1;
        if (heldKeys.contains(KeyEvent.VK_A) || heldKeys.contains(KeyEvent.VK_LEFT))  dx = -1;
        if (heldKeys.contains(KeyEvent.VK_D) || heldKeys.contains(KeyEvent.VK_RIGHT)) dx =  1;

        // Diagonal input — prefer horizontal
        if (dx != 0 && dy != 0) dy = 0;
        if (dx == 0 && dy == 0) return;

        attemptMove(dx, dy);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onKeyPressed(KeyEvent e) {
        heldKeys.add(e.getKeyCode());
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE -> screenManager.push(GameState.PAUSE, null);
            case KeyEvent.VK_E      -> onInteract();
        }
    }

    @Override
    public void onKeyReleased(KeyEvent e) {
        heldKeys.remove(e.getKeyCode());
    }

    @Override public void onMouseClicked(MouseEvent e) {}

    // ─────────────────────────────────────────────────────────────────────
    // Movement
    // ─────────────────────────────────────────────────────────────────────

    private void attemptMove(int dx, int dy) {
        int nx = playerPos.x() + dx;
        int ny = playerPos.y() + dy;
        DungeonFloor floor = data.floor();

        if (!floor.inBounds(nx, ny))           return;
        if (!floor.getTile(nx, ny).isPassable()) return;

        playerPos = new WorldPosition(nx, ny);
        data.session().setPosition(playerPos);
        moveCooldown = MOVE_COOLDOWN;

        floor.updateExploration(nx, ny, TORCH_RADIUS);

        DungeonFloor.TileType tile = floor.getTile(nx, ny);

        // Only tick encounter counter on open ground
        if (tile == DungeonFloor.TileType.FLOOR
                || tile == DungeonFloor.TileType.DOOR) {
            stepCount++;
            stepsUntilEncounter--;
            if (stepsUntilEncounter <= 0) {
                stepsUntilEncounter = nextEncounterThreshold();
                triggerEncounter();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Interaction (E key)
    // ─────────────────────────────────────────────────────────────────────

    private void onInteract() {
        DungeonFloor.TileType tile =
                data.floor().getTile(playerPos.x(), playerPos.y());

        switch (tile) {
            case STAIRS_UP -> {
                // Guard: don't exit immediately if we spawned here
                if (stepCount == 0) return;
                exitDungeon();
            }
            case STAIRS_DOWN ->
                // TODO: generate next floor, fadeTo DUNGEON with new DungeonData
                    System.out.println("[DungeonRoam] STAIRS_DOWN — deeper floors not yet wired.");
            case CHEST ->
                    System.out.println("[DungeonRoam] CHEST — not yet implemented.");
            default -> {}
        }
    }

    private void exitDungeon() {
        // onExit() will restore session position and clear dungeon floor
        screenManager.fadeTo(data.returnState(), null);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Encounter
    // ─────────────────────────────────────────────────────────────────────

    private void triggerEncounter() {
        List<Monster> enemies   = EncounterFactory.generateEncounter(
                data.session().getParty());
        var           condition = FormulaEngine.rollEncounterCondition();
        screenManager.fadeTo(GameState.ENCOUNTER_SPLASH,
                new EncounterSplashScreen.EncounterData(
                        enemies, condition, data.session()));
    }

    private static int nextEncounterThreshold() {
        return ENC_MIN + (int)(Math.random() * (ENC_MAX - ENC_MIN + 1));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void render(Graphics2D g) {
        final int W = GameCanvas.DESIGN_WIDTH;
        final int H = GameCanvas.DESIGN_HEIGHT;

        // ── 1. Void background ─────────────────────────────────────
        g.setColor(VOID);
        g.fillRect(0, 0, W, H);

        // Camera: player tile center at screen center
        final int camX = playerPos.x() * TILE_SIZE - W / 2 + TILE_SIZE / 2;
        final int camY = playerPos.y() * TILE_SIZE - H / 2 + TILE_SIZE / 2;

        Set<Long> litTiles = computeLitTiles();

        // ── 2. Memory layer (explored but dim) ──────────────────────
        renderTileLayer(g, camX, camY, litTiles, false);

        // ── 3. Active layer (lit tiles at full brightness) ──────────
        renderTileLayer(g, camX, camY, litTiles, true);

        // ── 4. Torchlight vignette ──────────────────────────────────
        renderTorchMask(g, W, H);

        // ── 5. Player avatar ────────────────────────────────────────
        renderPlayer(g, camX, camY);

        // ── 6. HUD ──────────────────────────────────────────────────
        renderHUD(g);
    }

    private void renderTileLayer(Graphics2D g, int camX, int camY,
                                 Set<Long> litTiles, boolean litPass) {
        DungeonFloor floor  = data.floor();
        int startX = Math.max(0, camX / TILE_SIZE - 1);
        int startY = Math.max(0, camY / TILE_SIZE - 1);
        int endX   = Math.min(floor.getWidth(),
                (camX + GameCanvas.DESIGN_WIDTH)  / TILE_SIZE + 2);
        int endY   = Math.min(floor.getHeight(),
                (camY + GameCanvas.DESIGN_HEIGHT) / TILE_SIZE + 2);

        for (int tx = startX; tx < endX; tx++) {
            for (int ty = startY; ty < endY; ty++) {
                if (!floor.isExplored(tx, ty)) continue;

                boolean lit = litTiles.contains(tileKey(tx, ty));
                if (lit != litPass)  continue;  // only draw the current pass

                int sx = tx * TILE_SIZE - camX;
                int sy = ty * TILE_SIZE - camY;

                DungeonFloor.TileType type = floor.getTile(tx, ty);
                Color base = tileColor(type);

                if (lit) {
                    g.setColor(base);
                    g.fillRect(sx, sy, TILE_SIZE, TILE_SIZE);

                    // Subtle grid lines when lit
                    g.setColor(new Color(0, 0, 0, 60));
                    g.setStroke(new BasicStroke(0.5f));
                    g.drawRect(sx, sy, TILE_SIZE, TILE_SIZE);

                    // Symbol overlay for interactive tiles
                    renderTileSymbol(g, type, sx, sy);
                } else {
                    // Dim pass — paint at reduced opacity
                    Composite old = g.getComposite();
                    g.setComposite(AlphaComposite.getInstance(
                            AlphaComposite.SRC_OVER, ALPHA_DIM));
                    g.setColor(base);
                    g.fillRect(sx, sy, TILE_SIZE, TILE_SIZE);
                    g.setComposite(old);
                }
            }
        }
    }

    private void renderTorchMask(Graphics2D g, int W, int H) {
        // RadialGradient: transparent at center → opaque void at edge
        // Produces a natural torch-circle vignette centered on the player
        Point2D center = new Point2D.Float(W / 2f, H / 2f);
        float[] fractions = { 0f, 0.55f, 1f };
        Color[] colors = {
                new Color(0, 0, 0, 0),
                new Color(0, 0, 0, 0),
                new Color(13, 13, 18, 255)   // VOID color, fully opaque
        };
        g.setPaint(new RadialGradientPaint(center, TORCH_PIXEL_RADIUS,
                fractions, colors));
        g.fillRect(0, 0, W, H);
        g.setPaint(null);
    }

    private void renderPlayer(Graphics2D g, int camX, int camY) {
        int sx   = playerPos.x() * TILE_SIZE - camX + 4;
        int sy   = playerPos.y() * TILE_SIZE - camY + 4;
        int size = TILE_SIZE - 8;

        // Glow halo
        g.setColor(new Color(232, 201, 106, 55));
        g.fillOval(sx - 4, sy - 4, size + 8, size + 8);

        // Body
        g.setColor(C_PLAYER);
        g.fillOval(sx, sy, size, size);

        // Highlight rim
        g.setColor(new Color(255, 255, 255, 100));
        g.setStroke(new BasicStroke(1.2f));
        g.drawOval(sx, sy, size, size);
    }

    private void renderTileSymbol(Graphics2D g, DungeonFloor.TileType type,
                                  int sx, int sy) {
        String sym = switch (type) {
            case STAIRS_UP   -> "↑";
            case STAIRS_DOWN -> "↓";
            case CHEST       -> "□";
            case DOOR        -> "▬";
            case TRAP        -> "×";
            default          -> null;
        };
        if (sym == null) return;

        Color symColor = switch (type) {
            case STAIRS_UP   -> C_STAIRS_UP;
            case STAIRS_DOWN -> C_STAIRS_DN;
            case CHEST       -> C_CHEST;
            case DOOR        -> C_DOOR;
            case TRAP        -> C_TRAP;
            default          -> TEXT;
        };

        g.setFont(ResourceCache.get().getFont(
                ResourceCache.FONT_DATA, Font.BOLD, 16f));
        g.setColor(symColor);
        FontMetrics fm = g.getFontMetrics();
        int tx = sx + (TILE_SIZE - fm.stringWidth(sym)) / 2;
        int ty = sy + (TILE_SIZE + fm.getAscent()) / 2 - 2;
        g.drawString(sym, tx, ty);
    }

    // ─────────────────────────────────────────────────────────────────────
    // HUD
    // ─────────────────────────────────────────────────────────────────────

    private void renderHUD(Graphics2D g) {
        // ── Location label (top-left) ──────────────────────────────
        int lw = 420, lh = 50;
        g.setColor(new Color(42, 33, 24, 216));
        g.fillRoundRect(20, 16, lw, lh, 6, 6);
        g.setColor(BORDER_STR);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(20, 16, lw, lh, 6, 6);
        drawText(g, data.dungeonName(), 36, 48, TextStyle.LABEL, GOLD);

        // ── Tile coords (top-right) ────────────────────────────────
        drawText(g, playerPos.x() + "," + playerPos.y(),
                GameCanvas.DESIGN_WIDTH - 110, 44, TextStyle.DATA, BORDER_STR);

        // ── Party health strip (bottom-left) ──────────────────────
        int stripX = 20, stripY = GameCanvas.DESIGN_HEIGHT - 80;
        int cardW = 160, cardH = 58;

        for (var pc : data.session().getParty()) {
            g.setColor(new Color(42, 33, 24, 200));
            g.fillRoundRect(stripX, stripY, cardW, cardH, 6, 6);
            g.setColor(BORDER_STR);
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(stripX, stripY, cardW, cardH, 6, 6);

            Color nameColor = pc.isAlive() ? TEXT : DANGER;
            drawText(g, pc.getName(), stripX + 8, stripY + 18,
                    TextStyle.DATA, nameColor);

            if (pc.isAlive()) {
                int hp = pc.getStat(Stat.HP), mh = pc.getStat(Stat.MAX_HP);
                drawHealthBar(g, hp, mh, stripX + 8, stripY + 26, cardW - 16, 8);
                drawText(g, hp + "/" + mh, stripX + 8, stripY + 48,
                        TextStyle.DATA, BORDER_STR);
            } else {
                drawText(g, "DEAD", stripX + 8, stripY + 48,
                        TextStyle.DATA, DANGER);
            }
            stripX += cardW + 8;
        }

        // ── Tile hint (bottom-center) ──────────────────────────────
        DungeonFloor.TileType curTile =
                data.floor().getTile(playerPos.x(), playerPos.y());
        String hint = switch (curTile) {
            case STAIRS_UP   -> stepCount > 0 ? "[E] Exit Dungeon" : null;
            case STAIRS_DOWN -> "[E] Descend";
            case CHEST       -> "[E] Open Chest";
            case DOOR        -> "[E] Open Door";
            default          -> null;
        };
        if (hint != null) {
            int hw = 340, hh = 38;
            int hx = (GameCanvas.DESIGN_WIDTH - hw) / 2;
            int hy = GameCanvas.DESIGN_HEIGHT - 58;
            g.setColor(new Color(42, 33, 24, 200));
            g.fillRoundRect(hx, hy, hw, hh, 6, 6);
            g.setColor(BORDER_STR);
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(hx, hy, hw, hh, 6, 6);
            drawTextCentered(g, hint, hx, hy + 25, hw, TextStyle.DATA, GOLD);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Fog of war helpers
    // ─────────────────────────────────────────────────────────────────────

    private Set<Long> computeLitTiles() {
        Set<Long> lit = new HashSet<>();
        int px = playerPos.x(), py = playerPos.y();
        for (int dx = -TORCH_RADIUS; dx <= TORCH_RADIUS; dx++) {
            for (int dy = -TORCH_RADIUS; dy <= TORCH_RADIUS; dy++) {
                if (dx * dx + dy * dy <= TORCH_RADIUS * TORCH_RADIUS) {
                    lit.add(tileKey(px + dx, py + dy));
                }
            }
        }
        return lit;
    }

    private static long tileKey(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static Color tileColor(DungeonFloor.TileType type) {
        return switch (type) {
            case FLOOR       -> C_FLOOR;
            case WALL, VOID  -> C_WALL;
            case DOOR        -> C_DOOR;
            case STAIRS_UP   -> C_STAIRS_UP;
            case STAIRS_DOWN -> C_STAIRS_DN;
            case CHEST       -> C_CHEST;
            case TRAP        -> C_TRAP;
        };
    }
}
