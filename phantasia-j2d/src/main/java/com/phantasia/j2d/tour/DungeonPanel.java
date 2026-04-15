// phantasia-j2d/src/main/java/com/phantasia/j2d/tour/DungeonPanel.java
package com.phantasia.j2d.tour;

import com.phantasia.core.data.GameSession;
import com.phantasia.core.world.DungeonFloor;
import com.phantasia.core.world.DungeonFloor.TileType;
import com.phantasia.core.world.WorldPosition;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.RadialGradientPaint;

/**
 * Fully playable dungeon exploration panel.
 *
 * RENDERING (multi-layer compositing):
 *   1. Black background (unexplored = void)
 *   2. "Memory" layer — explored tiles drawn at 30% opacity
 *   3. "Active" layer — tiles within torch radius drawn at 100%
 *   4. Torchlight mask — RadialGradientPaint from transparent to black
 *   5. Player avatar — always centered, always visible
 *   6. HUD overlay — dungeon name, position, step count
 *
 * COORDINATE SYSTEM:
 *   Dungeons use Y-down (same as screen space). No Y-flip needed.
 *   Camera centers on the player position.
 *
 * INPUT:
 *   WASD / Arrows — move one tile per press
 *   ESC — exit dungeon (returns to overworld)
 *
 * ENCOUNTERS:
 *   A step counter ticks on each successful move. When it reaches
 *   the threshold, a random encounter fires via TourFrame.
 *   The threshold resets after each encounter.
 */
public class DungeonPanel extends JPanel {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final int TILE_SIZE    = 64;
    private static final int TORCH_RADIUS = 3;       // tiles of full visibility
    private static final float TORCH_PIXEL_RADIUS = TILE_SIZE * (TORCH_RADIUS + 1.2f);

    // Tile colors (fallback when no spritesheet is loaded)
    private static final Color C_VOID       = new Color(  0,   0,   0);
    private static final Color C_FLOOR      = new Color( 42,  38,  50);
    private static final Color C_WALL       = new Color( 68,  62,  78);
    private static final Color C_DOOR       = new Color(120,  90,  55);
    private static final Color C_STAIRS_UP  = new Color( 80, 180, 100);
    private static final Color C_STAIRS_DN  = new Color(180,  80,  80);
    private static final Color C_CHEST      = new Color(200, 175,  60);
    private static final Color C_TRAP       = new Color(160,  50,  50);

    // HUD
    private static final Font HUD_FONT     = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    private static final Color HUD_BG      = new Color(0, 0, 0, 180);
    private static final Color HUD_FG      = new Color(215, 205, 185);

    // Encounter pacing
    private static final int BASE_ENCOUNTER_INTERVAL = 18;  // steps between encounters

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private final GameSession session;
    private final TourFrame   frame;

    // -------------------------------------------------------------------------
    // Dungeon state
    // -------------------------------------------------------------------------

    private DungeonFloor  currentFloor;
    private int           dungeonId;
    private String        dungeonName;
    private WorldPosition savedOverworldPos;  // where to return on exit

    // Movement / encounter state
    private int stepCount      = 0;
    private int encounterTimer = BASE_ENCOUNTER_INTERVAL;
    private int blockedTimer   = 0;   // frames of red flash
    private static final int BLOCKED_FRAMES = 16;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public DungeonPanel(GameSession session, TourFrame frame) {
        this.session = session;
        this.frame   = frame;

        setBackground(Color.BLACK);
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKey(e.getKeyCode());
            }
        });

        // Grab focus when shown
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0
                    && isShowing()) {
                requestFocusInWindow();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Entry point — called by TourFrame when EnterDungeon fires
    // -------------------------------------------------------------------------

    /**
     * Initializes the dungeon panel with a generated floor.
     *
     * @param id    dungeon feature ID (for logging / future save keying)
     * @param name  display name
     * @param floor the generated DungeonFloor
     */
    public void enter(int id, String name, DungeonFloor floor) {
        this.dungeonId   = id;
        this.dungeonName = name;
        this.currentFloor = floor;

        // Save overworld position so we can restore it on exit
        this.savedOverworldPos = session.getWorldPosition();

        // Place player at STAIRS_UP (the entry point)
        WorldPosition start = floor.findStairsUp();
        if (start == null) {
            // Fallback: center of the floor
            start = new WorldPosition(floor.getWidth() / 2, floor.getHeight() / 2);
        }
        session.setPosition(start);

        // Reveal tiles around start position
        floor.updateExploration(start.x(), start.y(),
                TourSettings.get().getTorchRadius());

        // Reset encounter state
        stepCount      = 0;
        encounterTimer = BASE_ENCOUNTER_INTERVAL;
        blockedTimer   = 0;

        // Store floor on session so NavigationManager can see it
        session.setCurrentDungeonFloor(floor);

        frame.eventLog.log("DUNGEON", "Entered " + name + "  id=" + id
                + "  start=" + start + "  floor=" + floor.getWidth() + "x" + floor.getHeight());

        repaint();
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    private void handleKey(int keyCode) {
        if (currentFloor == null) return;

        // ESC — exit dungeon
        if (keyCode == KeyEvent.VK_ESCAPE) {
            exitDungeon("Escaped from " + dungeonName);
            return;
        }

        String dir = switch (keyCode) {
            case KeyEvent.VK_UP,    KeyEvent.VK_W -> "north";
            case KeyEvent.VK_DOWN,  KeyEvent.VK_S -> "south";
            case KeyEvent.VK_LEFT,  KeyEvent.VK_A -> "west";
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> "east";
            default -> null;
        };
        if (dir == null) return;

        // Calculate target position
        // Note: dungeons use Y-down, but WorldPosition.step() uses Y-up
        // (north = y+1). We need to adapt: in dungeon screen space,
        // "north" (up on screen) means y-1.
        WorldPosition current = session.getWorldPosition();
        WorldPosition target = switch (dir) {
            case "north" -> new WorldPosition(current.x(), current.y() - 1);
            case "south" -> new WorldPosition(current.x(), current.y() + 1);
            case "west"  -> new WorldPosition(current.x() - 1, current.y());
            case "east"  -> new WorldPosition(current.x() + 1, current.y());
            default -> current;
        };

        TileType tileType = currentFloor.getTile(target.x(), target.y());

        // Collision check
        if (!tileType.isPassable()) {
            blockedTimer = BLOCKED_FRAMES;
            frame.eventLog.log("BLOCKED", "Wall at " + target);
            repaint();
            return;
        }

        // Move succeeded
        session.setPosition(target);
        currentFloor.updateExploration(target.x(), target.y(),
                TourSettings.get().getTorchRadius());
        stepCount++;

        frame.eventLog.log("MOVE", target + "  [" + tileType.name() + "]"
                + "  steps=" + stepCount);

        // Check for special tiles
        switch (tileType) {
            case STAIRS_UP -> {
                // Only exit if we entered from somewhere else (not our spawn)
                if (stepCount > 1) {
                    exitDungeon("Ascended stairs in " + dungeonName);
                    return;
                }
            }
            case STAIRS_DOWN -> {
                frame.eventLog.log("DUNGEON", "Reached STAIRS_DOWN — deeper floors not yet implemented.");
                // Future: generate next floor, transition
            }
            case CHEST -> {
                frame.eventLog.log("DUNGEON", "Found a chest! (Loot not yet implemented)");
            }
            case TRAP -> {
                frame.eventLog.log("DUNGEON", "Triggered a trap! (Damage not yet implemented)");
            }
            default -> {}
        }

        // Encounter timer
        // Encounter timer
        if (TourSettings.get().isEncountersEnabled()) {
            encounterTimer--;
            if (encounterTimer <= 0) {
                encounterTimer = BASE_ENCOUNTER_INTERVAL / 2
                        + (int)(Math.random() * BASE_ENCOUNTER_INTERVAL);
                frame.eventLog.log("ENCOUNTER", "Dungeon encounter at " + target);
                frame.triggerDungeonEncounter();
                return;
            }
        }

        frame.refreshStatus();
        repaint();
    }

    // -------------------------------------------------------------------------
    // Exit
    // -------------------------------------------------------------------------

    private void exitDungeon(String reason) {
        // Clear dungeon state from session
        session.setCurrentDungeonFloor(null);

        // Restore overworld position
        if (savedOverworldPos != null) {
            session.setPosition(savedOverworldPos);
        }

        currentFloor = null;
        frame.returnToMap(reason);
    }

    // -------------------------------------------------------------------------
    // Timer tick — called by TourFrame's game timer when dungeon is active
    // -------------------------------------------------------------------------

    public void tick() {
        if (blockedTimer > 0) blockedTimer--;
        repaint();
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics gx) {
        super.paintComponent(gx);
        if (currentFloor == null) {
            // Nothing to render — show a placeholder
            gx.setColor(Color.BLACK);
            gx.fillRect(0, 0, getWidth(), getHeight());
            gx.setColor(HUD_FG);
            gx.setFont(HUD_FONT);
            gx.drawString("No dungeon loaded.", 20, 30);
            return;
        }

        Graphics2D g = (Graphics2D) gx;

        int panelW = getWidth();
        int panelH = getHeight();

        // Clear to black (unexplored areas are void)
        g.setColor(C_VOID);
        g.fillRect(0, 0, panelW, panelH);

        WorldPosition playerPos = session.getWorldPosition();

        // Camera: center player on screen
        int camX = panelW / 2 - playerPos.x() * TILE_SIZE - TILE_SIZE / 2;
        int camY = panelH / 2 - playerPos.y() * TILE_SIZE - TILE_SIZE / 2;

        // ── Layer 1+2: Tiles (memory + active) ──
        g.translate(camX, camY);
        renderTiles(g, playerPos, panelW, panelH, camX, camY);
        g.translate(-camX, -camY);

        // ── Layer 3: Torchlight mask ──
        applyTorchlightMask(g, panelW, panelH);

        // ── Layer 4: Player token ──
        drawPlayer(g, panelW, panelH);

        // ── Layer 5: HUD ──
        drawHud(g, playerPos, panelW, panelH);
    }

    private void renderTiles(Graphics2D g, WorldPosition player,
                             int panelW, int panelH, int camX, int camY) {
        int floorW = currentFloor.getWidth();
        int floorH = currentFloor.getHeight();

        // Viewport culling: only draw tiles visible on screen
        int startX = Math.max(0, -camX / TILE_SIZE - 1);
        int startY = Math.max(0, -camY / TILE_SIZE - 1);
        int endX   = Math.min(floorW, startX + panelW / TILE_SIZE + 3);
        int endY   = Math.min(floorH, startY + panelH / TILE_SIZE + 3);

        for (int tx = startX; tx < endX; tx++) {
            for (int ty = startY; ty < endY; ty++) {
                boolean fogEnabled = TourSettings.get().isFogOfWarEnabled();
                if (fogEnabled && !currentFloor.isExplored(tx, ty)) continue;

                TileType type = currentFloor.getTile(tx, ty);
                if (type == TileType.VOID) continue;

                // Distance to player (for memory vs active state)
                int dx = tx - player.x();
                int dy = ty - player.y();
                double dist = Math.sqrt(dx * dx + dy * dy);

                if (dist > TORCH_RADIUS + 0.5) {
                    // Memory state: dimmed
                    g.setComposite(AlphaComposite.getInstance(
                            AlphaComposite.SRC_OVER, 0.3f));
                } else {
                    // Active state: full brightness
                    g.setComposite(AlphaComposite.SrcOver);
                }

                int screenX = tx * TILE_SIZE;
                int screenY = ty * TILE_SIZE;

                // Draw tile fill
                g.setColor(tileColor(type));
                g.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE);

                // Subtle grid line (only on active tiles)
                if (dist <= TORCH_RADIUS + 0.5) {
                    g.setColor(new Color(255, 255, 255, 15));
                    g.drawRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
                }

                // Feature markers
                if (type == TileType.STAIRS_UP || type == TileType.STAIRS_DOWN) {
                    g.setComposite(AlphaComposite.SrcOver);
                    g.setColor(type == TileType.STAIRS_UP ? C_STAIRS_UP : C_STAIRS_DN);
                    String sym = type == TileType.STAIRS_UP ? "\u25B2" : "\u25BC";
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
                    FontMetrics fm = g.getFontMetrics();
                    int strW = fm.stringWidth(sym);
                    g.drawString(sym,
                            screenX + (TILE_SIZE - strW) / 2,
                            screenY + TILE_SIZE / 2 + fm.getAscent() / 2 - 2);
                }
                if (type == TileType.CHEST) {
                    g.setComposite(AlphaComposite.SrcOver);
                    g.setColor(C_CHEST);
                    int cs = 14;
                    g.fillRect(screenX + (TILE_SIZE - cs) / 2,
                            screenY + (TILE_SIZE - cs) / 2, cs, cs);
                }
            }
        }
        // Reset composite
        g.setComposite(AlphaComposite.SrcOver);
    }

    private void applyTorchlightMask(Graphics2D g, int panelW, int panelH) {
        int centerX = panelW / 2;
        int centerY = panelH / 2;

        float[] fractions = { 0.0f, 0.55f, 0.85f, 1.0f };
        Color[] colors = {
                new Color(0, 0, 0, 0),
                new Color(0, 0, 0, 40),
                new Color(0, 0, 0, 200),
                new Color(0, 0, 0, 255),
        };

        RadialGradientPaint paint = new RadialGradientPaint(
                new Point2D.Float(centerX, centerY),
                TORCH_PIXEL_RADIUS,
                fractions, colors);

        g.setPaint(paint);
        g.fillRect(0, 0, panelW, panelH);

        // Black out corners beyond the gradient's bounding circle
        // (RadialGradientPaint only covers the circle; outside is unpainted)
        g.setColor(Color.BLACK);
        // Top band
        g.fillRect(0, 0, panelW,
                Math.max(0, centerY - (int) TORCH_PIXEL_RADIUS));
        // Bottom band
        int bottomY = centerY + (int) TORCH_PIXEL_RADIUS;
        if (bottomY < panelH)
            g.fillRect(0, bottomY, panelW, panelH - bottomY);
        // Left band
        g.fillRect(0, 0,
                Math.max(0, centerX - (int) TORCH_PIXEL_RADIUS), panelH);
        // Right band
        int rightX = centerX + (int) TORCH_PIXEL_RADIUS;
        if (rightX < panelW)
            g.fillRect(rightX, 0, panelW - rightX, panelH);
    }

    private void drawPlayer(Graphics2D g, int panelW, int panelH) {
        int cx = panelW / 2;
        int cy = panelH / 2;
        int size = (int)(TILE_SIZE * 0.5f);

        // Drop shadow
        g.setColor(new Color(0, 0, 0, 100));
        g.fillOval(cx - size / 2 + 2, cy - size / 2 + 3, size, size);

        // Token outline
        g.setColor(new Color(200, 160, 40));
        g.fillOval(cx - size / 2 - 1, cy - size / 2 - 1, size + 2, size + 2);

        // Token fill
        g.setColor(new Color(255, 230, 60));
        g.fillOval(cx - size / 2, cy - size / 2, size, size);
    }

    private void drawHud(Graphics2D g, WorldPosition pos, int panelW, int panelH) {
        g.setFont(HUD_FONT);
        FontMetrics fm = g.getFontMetrics();
        int lineH = fm.getHeight() + 3;
        int px = 10, py = 10;

        TileType standing = currentFloor.getTile(pos.x(), pos.y());

        String[] lines = {
                dungeonName.toUpperCase(),
                pos + "  [" + standing.name() + "]",
                "Steps: " + stepCount + "   Next enc: ~" + encounterTimer,
                blockedTimer > 0 ? "[ BLOCKED ]"
                        : "WASD: move    ESC: exit dungeon"
        };

        int boxW = 360;
        int boxH = lines.length * lineH + 20;

        // Background
        g.setColor(HUD_BG);
        g.fillRoundRect(px, py, boxW, boxH, 10, 10);

        // Text
        int ty = py + 13 + fm.getAscent();
        for (String line : lines) {
            if (line.startsWith("[")) {
                g.setColor(TourFrame.C_RED);
            } else if (line.equals(lines[0])) {
                g.setColor(TourFrame.C_PURPLE);
            } else {
                g.setColor(HUD_FG);
            }
            g.drawString(line, px + 10, ty);
            ty += lineH;
        }
    }

    // -------------------------------------------------------------------------
    // Tile color lookup
    // -------------------------------------------------------------------------

    private static Color tileColor(TileType type) {
        return switch (type) {
            case VOID       -> C_VOID;
            case FLOOR      -> C_FLOOR;
            case WALL       -> C_WALL;
            case DOOR       -> C_DOOR;
            case STAIRS_UP  -> C_STAIRS_UP;
            case STAIRS_DOWN-> C_STAIRS_DN;
            case CHEST      -> C_CHEST;
            case TRAP       -> C_TRAP;
        };
    }
}