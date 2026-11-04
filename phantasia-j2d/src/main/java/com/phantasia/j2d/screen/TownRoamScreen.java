// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/TownRoamScreen.java
package com.phantasia.j2d.screen;

import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.NpcRegistry;
import com.phantasia.core.data.ShopInventory;
import com.phantasia.core.data.ShopRegistry;
import com.phantasia.core.model.DialogueContext;
import com.phantasia.core.model.NpcDefinition;
import com.phantasia.core.model.PlacedNpc;
import com.phantasia.core.world.*;
import com.phantasia.j2d.engine.*;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.phantasia.j2d.render.ParchmentRenderer.*;

/**
 * Town roam screen — tile-snapped movement inside a town interior map.
 *
 * <h3>Design contract</h3>
 * <ul>
 *   <li>Towns use full lighting — no fog of war.</li>
 *   <li>No random encounters inside towns.</li>
 *   <li>Services (shop, inn, guild, bank) open as overlays when the
 *       player bumps into an NPC with the appropriate {@link com.phantasia.core.model.NpcRole}.</li>
 *   <li>The EXIT {@link PlacedFeature} returns the player to the overworld
 *       via {@code fadeTo(returnState)}.</li>
 * </ul>
 *
 * <h3>Interaction model</h3>
 * <ul>
 *   <li>Player moves tile-by-tile with WASD / arrows.</li>
 *   <li><b>Bump</b>: player tries to walk into an impassable tile.
 *       If a {@link PlacedNpc} occupies that tile, dispatch on NPC role.
 *       If a {@link PlacedFeature} is there, dispatch on feature type.</li>
 *   <li><b>Step</b>: player steps onto a passable tile.
 *       If a {@link PlacedFeature} with STEP_TRIGGER is there (e.g. EXIT),
 *       dispatch on feature type.</li>
 * </ul>
 *
 * <h3>Transition data</h3>
 * {@link TownData} carries the map to display, the town name for the HUD,
 * and the return state for the EXIT feature.
 */
public class TownRoamScreen implements Screen {

    // ─────────────────────────────────────────────────────────────────────
    // Transition data
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @param townName    shown in the HUD location label
     * @param map         the interior map to render and walk
     * @param spawnPos    tile position where the player appears
     * @param returnState the GameState to fadeTo when the player exits
     */
    public record TownData(
            String       townName,
            InteriorMap  map,
            WorldPosition spawnPos,
            GameState     returnState
    ) {}

    // ─────────────────────────────────────────────────────────────────────
    // Rendering constants (design resolution 1920×1080)
    // ─────────────────────────────────────────────────────────────────────

    private static final int   TILE_SIZE     = 48;   // px per interior tile
    private static final float MOVE_COOLDOWN = 0.13f;

    private static final int   COUNTER_SEARCH_DEPTH = 3;

    // Fallback colours keyed by tile assetId
    private static final java.util.Map<String, Color> ASSET_COLORS;
    static {
        java.util.Map<String, Color> m = new java.util.HashMap<>();
        m.put("town_wood_floor",   new Color(0x8B, 0x69, 0x14));
        m.put("town_stone_floor",  new Color(0x7A, 0x70, 0x68));
        m.put("town_wall",         new Color(0x4A, 0x3A, 0x2A));
        m.put("town_counter",      new Color(0x6B, 0x4C, 0x1E));
        m.put("town_door",         new Color(0x8B, 0x5E, 0x3C));
        m.put("town_window",       new Color(0x4A, 0x6A, 0x8A));
        m.put("town_stairs",       new Color(0x9A, 0x90, 0x88));
        m.put("town_hearth",       new Color(0x8B, 0x4A, 0x14));
        m.put("town_table",        new Color(0x7A, 0x5A, 0x2A));
        m.put("town_shelf",        new Color(0x6B, 0x4A, 0x1E));
        m.put("town_bed",          new Color(0x4A, 0x5A, 0x7A));
        m.put("town_rug",          new Color(0x7A, 0x2A, 0x2A));
        m.put("town_market_stall", new Color(0x8B, 0x7A, 0x2A));
        m.put("town_fountain",     new Color(0x3A, 0x5A, 0x8B));
        m.put("town_cobblestone",  new Color(0x6A, 0x60, 0x58));
        m.put("town_garden",       new Color(0x2A, 0x6A, 0x2A));
        m.put("town_exit",         new Color(0xBF, 0xA3, 0x4C)); // gold = clear cue
        ASSET_COLORS = java.util.Collections.unmodifiableMap(m);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Dependencies (injected at construction)
    // ─────────────────────────────────────────────────────────────────────

    private final ScreenManager screenManager;
    private final GameSession   session;
    private final NpcRegistry   npcRegistry;
    private final ShopRegistry  shopRegistry;

    // ─────────────────────────────────────────────────────────────────────
    // Per-enter state
    // ─────────────────────────────────────────────────────────────────────

    private TownData      data;
    private InteriorTileSet tileSet;
    private WorldPosition  playerPos;
    private Set<Integer>   heldKeys;
    private float          moveCooldown;

    // ─────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────

    public TownRoamScreen(ScreenManager screenManager,
                          GameSession   session,
                          NpcRegistry   npcRegistry,
                          ShopRegistry  shopRegistry) {
        this.screenManager = screenManager;
        this.session       = session;
        this.npcRegistry   = npcRegistry;
        this.shopRegistry  = shopRegistry;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Screen lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onEnter(Object transitionData) {
        this.data     = (TownData) transitionData;
        this.tileSet  = InteriorTileSet.standardTown();
        this.playerPos = data.spawnPos();
        this.heldKeys  = new HashSet<>();
        this.moveCooldown = 0f;
    }

    @Override
    public void onExit() {
        // Clear held input so a direction held at push-time doesn't
        // re-trigger a bump-dispatch the moment an overlay pops. The
        // player must release and re-press to move again — standard
        // anti-repeat behavior for modal dialogs.
        if (heldKeys != null) heldKeys.clear();
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

        if (dx != 0 && dy != 0) dy = 0;  // cardinal only
        if (dx == 0 && dy == 0) return;

        attemptMove(dx, dy);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onKeyPressed(KeyEvent e) {
        heldKeys.add(e.getKeyCode());
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
            screenManager.push(GameState.PAUSE, null);
    }

    @Override
    public void onKeyReleased(KeyEvent e) {
        heldKeys.remove(e.getKeyCode());
    }

    @Override public void onMouseClicked(MouseEvent e) {}

    // ─────────────────────────────────────────────────────────────────────
    // Movement and interaction
    // ─────────────────────────────────────────────────────────────────────

    private void attemptMove(int dx, int dy) {
        int tx = playerPos.x() + dx;
        int ty = playerPos.y() + dy;
        InteriorMap map = data.map();

        if (!map.inBounds(tx, ty)) return;

        int tileIdx = map.getTile(tx, ty);
        TileSetEntry entry = tileSet.getEntry(tileIdx);
        if (entry == null) return;

        if (!entry.passable()) {
            // Bumped an impassable tile. First resolve the classic
            // direct-bump case: an NPC or feature *on* the bumped tile
            // (signs, posts, an NPC explicitly placed on a wall).
            // If nothing is there, treat the tile as a shop counter and
            // search past it in the bump direction for an NPC "behind
            // the counter" — the Questron/Phantasie interaction model.
            if (!tryDirectBump(tx, ty)) {
                tryBumpThrough(tx, ty, dx, dy);
            }
            // Any bump — dispatch or not — consumes a move cooldown.
            // Without this, a held direction fires 60 dispatches/second
            // into the counter and re-pushes the overlay the instant
            // the player pops it.
            moveCooldown = MOVE_COOLDOWN;
            return;
        }

        // Move succeeded
        playerPos = new WorldPosition(tx, ty);
        moveCooldown = MOVE_COOLDOWN;

        // Step trigger — check for feature at new position
        if (entry.interactionType() == InteractionType.STEP_TRIGGER) {
            onStep(tx, ty);
        }
    }

    /**
     * Classic bump interaction: NPC or feature on the bumped tile itself.
     * Returns {@code true} if something was dispatched so the caller knows
     * whether to escalate to a counter search.
     *
     * <p>Replaces the former {@code onBump()} method; semantics are
     * identical, but the boolean return lets {@link #attemptMove} chain
     * a bump-through search when the direct bump finds nothing.</p>
     */
    private boolean tryDirectBump(int tx, int ty) {
        InteriorMap map = data.map();

        // ── NPC check ──────────────────────────────────────────────────
        PlacedNpc npcPlacement = map.getNpcAt(tx, ty);
        if (npcPlacement != null
                && npcPlacement.isVisible(session::hasFlag)) {
            NpcDefinition npcDef = npcRegistry != null
                    ? npcRegistry.get(npcPlacement.npcId()) : null;
            if (npcDef != null) {
                dispatchNpc(npcDef);
                return true;
            }
        }

        // ── Feature check ──────────────────────────────────────────────
        PlacedFeature feat = map.getFeatureAt(tx, ty);
        if (feat != null && feat.isActive(session::hasFlag)) {
            dispatchFeature(feat);
            return true;
        }
        return false;
    }

    /**
     * Bump-through counter search. Treats the bumped tile as a shop
     * counter and walks forward from it in the bump direction looking
     * for an NPC "behind" the counter. Stops at any impassable tile
     * (so the search never leaks past walls into adjacent buildings)
     * or when it has advanced {@value #COUNTER_SEARCH_DEPTH} tiles,
     * whichever comes first.
     *
     * <p>This implements the Questron/Phantasie-style shop interaction:
     * a player on the town square walks up to a shop's outer wall and
     * bumps it; the search finds the shopkeeper standing on the passable
     * interior floor behind the counter and opens the shop window.</p>
     *
     * @return {@code true} if an NPC was dispatched
     */
    private boolean tryBumpThrough(int tx, int ty, int dx, int dy) {
        InteriorMap map = data.map();

        int sx = tx + dx;
        int sy = ty + dy;
        for (int step = 0; step < COUNTER_SEARCH_DEPTH; step++) {
            if (!map.inBounds(sx, sy)) return false;

            int tileIdx = map.getTile(sx, sy);
            TileSetEntry entry = tileSet.getEntry(tileIdx);
            if (entry == null) return false;
            // Stop at walls — prevents the search from leaking into
            // adjacent buildings or through map obstacles.
            if (!entry.passable()) return false;

            PlacedNpc npcPlacement = map.getNpcAt(sx, sy);
            if (npcPlacement != null
                    && npcPlacement.isVisible(session::hasFlag)) {
                NpcDefinition npcDef = npcRegistry != null
                        ? npcRegistry.get(npcPlacement.npcId()) : null;
                if (npcDef != null) {
                    dispatchNpc(npcDef);
                    return true;
                }
            }

            sx += dx;
            sy += dy;
        }
        return false;
    }

    /**
     * Called when the player steps onto a tile with STEP_TRIGGER.
     */
    private void onStep(int tx, int ty) {
        PlacedFeature feat = data.map().getFeatureAt(tx, ty);
        if (feat != null && feat.isActive(session::hasFlag)) {
            dispatchFeature(feat);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Dispatch helpers
    // ─────────────────────────────────────────────────────────────────────

    private void dispatchNpc(NpcDefinition npcDef) {
        switch (npcDef.getRole()) {

            case MERCHANT -> {
                ShopInventory shop = (shopRegistry != null)
                        ? shopRegistry.get(npcDef.getShopId()) : null;
                if (shop == null) return;
                screenManager.push(GameState.SHOP,
                        new ShopOverlay.ShopData(shop, session));
            }

            case INNKEEPER -> screenManager.push(GameState.INN,
                    new InnOverlay.InnData(
                            npcDef.getName() + "'s Inn", session));

            case TRAINER -> screenManager.push(GameState.GUILD,
                    new GuildOverlay.GuildData(
                            npcDef.getName() + "'s Guild", session));

            case BANKER -> screenManager.push(GameState.BANK,
                    new BankOverlay.BankData(
                            npcDef.getName() + "'s Bank", session));

            default -> {
                // QUEST_GIVER, INFORMANT, GUARD → dialogue
                DialogueContext ctx =
                        DialogueContext.freeze(npcDef.getName(), session);
                screenManager.push(GameState.DIALOGUE,
                        new DialogueScreen.DialogueData(
                                npcDef, session, ctx, List.of()));
            }
        }
    }

    private void dispatchFeature(PlacedFeature feat) {
        switch (feat.featureType()) {
            case EXIT -> screenManager.fadeTo(data.returnState(), null);
            case SIGN, ALTAR -> {
                // TODO: show sign text via DialogueScreen with a dummy NPC
                System.out.println("[TownRoam] Sign: " + feat.serviceData());
            }
            default -> {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void render(Graphics2D g) {
        final int W = GameCanvas.DESIGN_WIDTH;
        final int H = GameCanvas.DESIGN_HEIGHT;

        // Warm town background (not the void)
        g.setColor(new Color(0x3A, 0x2A, 0x18));
        g.fillRect(0, 0, W, H);

        InteriorMap map = data.map();

        // Camera: player tile centered
        int camX = playerPos.x() * TILE_SIZE - W / 2 + TILE_SIZE / 2;
        int camY = playerPos.y() * TILE_SIZE - H / 2 + TILE_SIZE / 2;

        renderTiles(g, map, camX, camY);
        renderNpcs(g, map, camX, camY);
        renderPlayer(g, camX, camY);
        renderHUD(g);
    }

    private void renderTiles(Graphics2D g, InteriorMap map, int camX, int camY) {
        int startX = Math.max(0, camX / TILE_SIZE - 1);
        int startY = Math.max(0, camY / TILE_SIZE - 1);
        int endX   = Math.min(map.getWidth(),
                (camX + GameCanvas.DESIGN_WIDTH)  / TILE_SIZE + 2);
        int endY   = Math.min(map.getHeight(),
                (camY + GameCanvas.DESIGN_HEIGHT) / TILE_SIZE + 2);

        for (int tx = startX; tx < endX; tx++) {
            for (int ty = startY; ty < endY; ty++) {
                int sx = tx * TILE_SIZE - camX;
                int sy = ty * TILE_SIZE - camY;

                int          idx   = map.getTile(tx, ty);
                TileSetEntry entry = tileSet.getEntry(idx);

                Color fill = ASSET_COLORS.getOrDefault(
                        entry != null ? entry.assetId() : "",
                        new Color(0x6A, 0x60, 0x58));

                g.setColor(fill);
                g.fillRect(sx, sy, TILE_SIZE, TILE_SIZE);

                // Subtle grid line
                g.setColor(new Color(0, 0, 0, 40));
                g.setStroke(new BasicStroke(0.5f));
                g.drawRect(sx, sy, TILE_SIZE, TILE_SIZE);

                // Exit tile glow
                if (entry != null && "town_exit".equals(entry.assetId())) {
                    g.setColor(new Color(0xBF, 0xA3, 0x4C, 60));
                    g.fillRect(sx, sy, TILE_SIZE, TILE_SIZE);
                    drawTextCentered(g, "EXIT", sx, sy + TILE_SIZE / 2 + 6,
                            TILE_SIZE, TextStyle.DATA, GOLD_BRIGHT);
                }
            }
        }
    }

    private void renderNpcs(Graphics2D g, InteriorMap map, int camX, int camY) {
        List<PlacedNpc> npcs = map.getVisibleNpcs(session::hasFlag);
        for (PlacedNpc n : npcs) {
            int sx = n.x() * TILE_SIZE - camX + 4;
            int sy = n.y() * TILE_SIZE - camY + 4;
            int sz = TILE_SIZE - 8;

            // NPC body (magic blue circle)
            g.setColor(new Color(74, 127, 165, 60));
            g.fillOval(sx - 3, sy - 3, sz + 6, sz + 6);
            g.setColor(MAGIC);
            g.fillOval(sx, sy, sz, sz);
            g.setColor(new Color(180, 210, 240, 140));
            g.setStroke(new BasicStroke(1.2f));
            g.drawOval(sx, sy, sz, sz);

            // Name label
            NpcDefinition def = npcRegistry != null
                    ? npcRegistry.get(n.npcId()) : null;
            if (def != null) {
                drawTextCentered(g, def.getName(),
                        n.x() * TILE_SIZE - camX - 10,
                        n.y() * TILE_SIZE - camY - 6,
                        TILE_SIZE + 20, TextStyle.DATA,
                        new Color(180, 210, 240, 200));
            }
        }
    }

    private void renderPlayer(Graphics2D g, int camX, int camY) {
        int sz = TILE_SIZE - 10;
        int sx = playerPos.x() * TILE_SIZE - camX + 5;
        int sy = playerPos.y() * TILE_SIZE - camY + 5;

        g.setColor(new Color(0xE8, 0xC9, 0x6A, 55));
        g.fillOval(sx - 4, sy - 4, sz + 8, sz + 8);
        g.setColor(GOLD_BRIGHT);
        g.fillOval(sx, sy, sz, sz);
        g.setColor(new Color(255, 255, 255, 100));
        g.setStroke(new BasicStroke(1.2f));
        g.drawOval(sx, sy, sz, sz);
    }

    private void renderHUD(Graphics2D g) {
        // Location label
        int lw = 380, lh = 50;
        g.setColor(new Color(42, 33, 24, 216));
        g.fillRoundRect(20, 16, lw, lh, 6, 6);
        g.setColor(BORDER_STR);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(20, 16, lw, lh, 6, 6);
        drawText(g, data.townName(), 36, 48, TextStyle.LABEL, GOLD);

        // Tile coords (debug)
        drawText(g,
                playerPos.x() + "," + playerPos.y(),
                GameCanvas.DESIGN_WIDTH - 110, 44,
                TextStyle.DATA, BORDER_STR);

        // Party health strip
        int stripX = 20, stripY = GameCanvas.DESIGN_HEIGHT - 80;
        for (var pc : session.getParty()) {
            g.setColor(new Color(42, 33, 24, 200));
            g.fillRoundRect(stripX, stripY, 160, 58, 6, 6);
            g.setColor(BORDER_STR);
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(stripX, stripY, 160, 58, 6, 6);
            Color nc = pc.isAlive() ? TEXT : DANGER;
            drawText(g, pc.getName(), stripX + 8, stripY + 18, TextStyle.DATA, nc);
            if (pc.isAlive()) {
                int hp = pc.getStat(com.phantasia.core.model.Stat.HP);
                int mh = pc.getStat(com.phantasia.core.model.Stat.MAX_HP);
                drawHealthBar(g, hp, mh, stripX + 8, stripY + 26, 144, 8);
            }
            stripX += 168;
        }

        // Interaction hint
        renderInteractionHint(g);
    }

    private void renderInteractionHint(Graphics2D g) {
        InteriorMap map = data.map();
        // Check cardinal neighbours for interactable NPCs or features
        int[][] dirs = {{0,-1},{0,1},{-1,0},{1,0}};
        for (int[] d : dirs) {
            int tx = playerPos.x() + d[0];
            int ty = playerPos.y() + d[1];
            if (!map.inBounds(tx, ty)) continue;

            PlacedNpc npc = map.getNpcAt(tx, ty);
            if (npc != null && npc.isVisible(session::hasFlag)) {
                NpcDefinition def = npcRegistry != null
                        ? npcRegistry.get(npc.npcId()) : null;
                if (def != null) {
                    String hint = "Speak with " + def.getName();
                    renderHintBox(g, hint);
                    return;
                }
            }
        }

        // Check EXIT at current position
        PlacedFeature feat = map.getFeatureAt(playerPos.x(), playerPos.y());
        if (feat != null && feat.featureType() == PlacedFeatureType.EXIT) {
            renderHintBox(g, "Press WASD to leave " + data.townName());
        }
    }

    private void renderHintBox(Graphics2D g, String hint) {
        int hw = 460, hh = 38;
        int hx = (GameCanvas.DESIGN_WIDTH - hw) / 2;
        int hy = GameCanvas.DESIGN_HEIGHT - 60;
        g.setColor(new Color(42, 33, 24, 200));
        g.fillRoundRect(hx, hy, hw, hh, 6, 6);
        g.setColor(BORDER_STR);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(hx, hy, hw, hh, 6, 6);
        drawTextCentered(g, hint, hx, hy + 25, hw, TextStyle.DATA, GOLD);
    }
}
