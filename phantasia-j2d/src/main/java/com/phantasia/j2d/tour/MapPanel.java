// phantasia-j2d/src/main/java/com/phantasia/j2d/tour/MapPanel.java
package com.phantasia.j2d.tour;

import com.phantasia.core.data.GameSession;
import com.phantasia.core.logic.NavigationManager;
import com.phantasia.core.world.TileType;
import com.phantasia.core.world.WorldMap;
import com.phantasia.core.world.WorldPosition;
import com.phantasia.core.logic.WorldEvent;
import com.phantasia.core.logic.WorldEventResolver;
import com.phantasia.core.world.EncounterTimer;
import com.phantasia.core.world.Tile;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Map view panel — 64px Kenney tiles, EDT-only Swing Timer rendering.
 *
 * TILE ASSETS:
 *   Loaded from assets/tiles/ relative to the JVM working directory.
 *   Falls back to solid colors if assets are absent — always runnable.
 *
 * COORDINATE SYSTEM:
 *   WorldPosition Y=0 is south (bottom of map). Java2D Y=0 is top.
 *   Conversion: screenY = (mapHeight - 1 - worldY) * TILE
 */
public class MapPanel extends JPanel {

    static final int TILE = KenneyTileLoader.DISPLAY_SIZE;  // 64px
    private static final int BLOCKED_FRAMES = 20;

    private static final Color TOKEN_FILL    = new Color(255, 230, 50);
    private static final Color TOKEN_OUTLINE = new Color(160, 120, 18);
    private static final Color TOKEN_SHADOW  = new Color(0, 0, 0, 90);
    private static final Color FEATURE_TOWN  = new Color(255, 240, 80, 210);
    private static final Color FEATURE_DNG   = new Color(200, 80, 255, 210);
    private static final Color GRID_LINE     = new Color(0, 0, 0, 22);

    private final WorldMap          worldMap;
    private final GameSession       session;
    private final TourFrame         frame;

    private final KenneyTileLoader  tileLoader;

    private final WorldEventResolver resolver      = new WorldEventResolver();
    private final EncounterTimer     encounterTimer = new EncounterTimer();

    // Smooth lerp (pixel coords, float)
    private float visualX, visualY;

    private int  moveCount    = 0;
    private int  blockedTimer = 0;
    private int  fps          = 0;
    private int  fpsFrames    = 0;
    private long fpsTimer     = System.currentTimeMillis();
    private final BufferedImage playerImage;

    // -------------------------------------------------------------------------

    public MapPanel(WorldMap worldMap, GameSession session, TourFrame frame) {
        this.worldMap   = worldMap;
        this.session    = session;
        this.frame      = frame;

        tileLoader = new KenneyTileLoader(KenneyTileLoader.defaultAssetDir());
        tileLoader.load();

        BufferedImage loaded = null;
        String[] playerPaths = {
                "assets/PLAYER.png",
                "../assets/PLAYER.png",
                "assets/player.png",
                "../assets/player.png",
        };
        for (String path : playerPaths) {
            File f = new File(path);
            if (f.exists()) {
                try {
                    BufferedImage raw = ImageIO.read(f);
// Convert to ARGB and make white/near-white pixels transparent
                    loaded = makeTransparent(raw, 240);
                    System.out.println("[MapPanel] Loaded player image: "
                            + f.getAbsolutePath());
                    break;
                } catch (IOException e) {
                    System.err.println("[MapPanel] Failed to load " + path
                            + ": " + e.getMessage());
                }
            }
        }
        playerImage = loaded;

        setBackground(new Color(8, 8, 12));
        setFocusable(true);

        WorldPosition p = session.getWorldPosition();
        visualX = tilePixelX(p);
        visualY = tilePixelY(p);

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { handleKey(e.getKeyCode()); }
        });

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing())
                requestFocusInWindow();
        });
    }

    // -------------------------------------------------------------------------
    // Timer tick (EDT, 30fps)
    // -------------------------------------------------------------------------

    public void tick() {
        float targetX = tilePixelX(session.getWorldPosition());
        float targetY = tilePixelY(session.getWorldPosition());
        visualX += (targetX - visualX) * 0.22f;
        visualY += (targetY - visualY) * 0.22f;
        if (blockedTimer > 0) blockedTimer--;
        fpsFrames++;
        long now = System.currentTimeMillis();
        if (now - fpsTimer >= 1000L) { fps = fpsFrames; fpsFrames = 0; fpsTimer = now; }
    }

    /**
     * Converts an image to ARGB and makes all pixels brighter than
     * the threshold transparent. Handles PNGs saved without alpha.
     *
     * @param src       source image
     * @param threshold brightness threshold 0-255; pixels where all three
     *                  channels exceed this value become fully transparent
     */
    private static BufferedImage makeTransparent(BufferedImage src, int threshold) {
        BufferedImage result = new BufferedImage(
                src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();

        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                int argb = result.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int gr = (argb >> 8)  & 0xFF;
                int b =  argb         & 0xFF;
                if (r > threshold && gr > threshold && b > threshold) {
                    result.setRGB(x, y, 0x00FFFFFF);  // fully transparent
                }
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Paint
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics gx) {
        super.paintComponent(gx);
        Graphics2D g = (Graphics2D) gx;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int w = getWidth(), h = getHeight();
        int camX = (int)(w / 2f - visualX - TILE / 2f);
        int camY = (int)(h / 2f - visualY - TILE / 2f);

        // Terrain
        for (int ty = 0; ty < worldMap.getHeight(); ty++) {
            for (int tx = 0; tx < worldMap.getWidth(); tx++) {
                int sx = camX + tx * TILE;
                int sy = camY + ty * TILE;
                if (sx + TILE < 0 || sx > w || sy + TILE < 0 || sy > h) continue;

                int worldY = worldMap.getHeight() - 1 - ty;
                TileType type = worldMap.getTile(tx, worldY).getType();

                BufferedImage img = tileLoader.getTile(type);
                if (img != null) {
                    g.drawImage(img, sx, sy, TILE, TILE, null);
                } else {
                    g.setColor(tileLoader.getFallbackColor(type));
                    g.fillRect(sx, sy, TILE, TILE);
                }

                // Subtle grid
                g.setColor(GRID_LINE);
                g.drawRect(sx, sy, TILE, TILE);

                // Feature dot
                if (worldMap.getTile(tx, worldY).hasFeature()) {
                    int dr = 7;
                    int dx = sx + TILE - dr * 2 - 5, dy = sy + 5;
                    g.setColor(new Color(0,0,0,100));
                    g.fillOval(dx-1, dy-1, dr*2+2, dr*2+2);
                    g.setColor(type == TileType.DUNGEON ? FEATURE_DNG : FEATURE_TOWN);
                    g.fillOval(dx, dy, dr*2, dr*2);
                }
            }
        }

        // Party token (always at screen centre)
        int ts = (int)(TILE * 0.54f);
        int tx = w/2 - ts/2, tokenY = h/2 - ts/2;
        if (playerImage != null) {
            // Draw drop shadow
            g.setColor(TOKEN_SHADOW);
            g.fillRoundRect(tx+3, tokenY+4, ts, ts, 8, 8);
            // Draw player sprite scaled to token size
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(playerImage, tx, tokenY, ts, ts, null);
        } else {
            // Fallback — yellow box
            g.setColor(TOKEN_SHADOW);
            g.fillRoundRect(tx+3, tokenY+4, ts, ts, 8, 8);
            g.setColor(TOKEN_OUTLINE);
            g.fillRoundRect(tx-1, tokenY-1, ts+2, ts+2, 9, 9);
            g.setColor(TOKEN_FILL);
            g.fillRoundRect(tx, tokenY, ts, ts, 8, 8);
            g.setColor(new Color(255, 248, 150, 130));
            g.fillOval(tx + ts/4, tokenY + ts/5, ts/3, ts/5);
        }

        // HUD
        drawHud(g, w, h);
    }

    private void drawHud(Graphics2D g, int w, int h) {
        g.setFont(TourFrame.F_BODY);
        FontMetrics fm = g.getFontMetrics();
        int lh = fm.getHeight() + 3;
        int px = 10, py = 10, bw = 370;

        WorldPosition pos = session.getWorldPosition();
        String tileType   = worldMap.inBounds(pos.x(), pos.y())
                ? worldMap.getTile(pos).getType().name() : "?";
        long alive = session.getParty().stream().filter(p -> p.isAlive()).count();
        String assets = tileLoader.loadedCount() > 0
                ? tileLoader.loadedCount() + "/8 Kenney tiles" : "fallback colors";

        String[] lines = {
                "PHANTASIA  ·  TOURING ENGINE",
                pos + "  [" + tileType + "]",
                "Party: " + alive + "/" + session.getParty().size()
                        + "   Gold: " + session.getLedger().getPartyGold() + " gp",
                "Moves: " + moveCount + "   FPS: " + fps + "   " + assets,
                blockedTimer > 0 ? "[ BLOCKED — impassable ]"
                        : "WASD / Arrows: move    Ctrl+S: save    ESC: quit"
        };

        int bh = lines.length * lh + 20;
        g.setColor(new Color(0, 0, 0, 168));
        g.fillRoundRect(px, py, bw, bh, 10, 10);
        g.setColor(new Color(255, 255, 255, 20));
        g.drawRoundRect(px, py, bw, bh, 10, 10);

        int ty = py + 13 + fm.getAscent();
        for (String line : lines) {
            g.setColor(line.startsWith("PHANTASIA") ? TourFrame.C_ACCENT
                    : line.startsWith("[ BLOCKED") ? TourFrame.C_RED
                    : TourFrame.C_TEXT);
            g.drawString(line, px + 10, ty);
            ty += lh;
        }
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    private void handleKey(int code) {
        String dir = switch (code) {
            case KeyEvent.VK_UP,    KeyEvent.VK_W -> "north";
            case KeyEvent.VK_DOWN,  KeyEvent.VK_S -> "south";
            case KeyEvent.VK_LEFT,  KeyEvent.VK_A -> "west";
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> "east";
            case KeyEvent.VK_F      -> { frame.newSession();   yield null; }
            case KeyEvent.VK_ESCAPE -> { frame.saveSession();  System.exit(0); yield null; }
            default -> null;
        };
        if (dir == null) return;

        WorldPosition current = session.getWorldPosition();
        WorldPosition target  = current.step(dir);

        // Bounds + passability check
        if (!worldMap.inBounds(target.x(), target.y())
                || !worldMap.getTile(target).isPassable()) {
            blockedTimer = BLOCKED_FRAMES;
            frame.eventLog.log("BLOCKED", "Impassable at " + target);
            return;
        }

        // Move succeeded
        session.setPosition(target);
        moveCount++;

        Tile tile = worldMap.getTile(target);
        frame.eventLog.log("MOVE", target
                + "  [" + tile.getType().name() + "]");
        frame.refreshStatus();

        // Check for world events (town, dungeon, scripted battle, tile event)
        resolver.resolve(tile).ifPresent(frame::handleWorldEvent);

        // Random encounter timer (features handle their own transitions)
        // Random encounter timer (features handle their own transitions)
        if (!tile.hasFeature()
                && TourSettings.get().isEncountersEnabled()
                && encounterTimer.step(tile.getType())) {
            frame.eventLog.log("ENCOUNTER", "Random at " + target
                    + "  [" + tile.getType().name() + "]");
            frame.handleWorldEvent(new WorldEvent.RandomEncounter());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private float tilePixelX(WorldPosition p) { return p.x() * TILE; }
    private float tilePixelY(WorldPosition p) { return (worldMap.getHeight() - 1 - p.y()) * TILE; }
}