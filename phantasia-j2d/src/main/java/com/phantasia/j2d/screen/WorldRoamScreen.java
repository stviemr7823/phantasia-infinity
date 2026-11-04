// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/WorldRoamScreen.java
package com.phantasia.j2d.screen;

import com.phantasia.core.data.*;
import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;
import com.phantasia.core.world.*;
import com.phantasia.j2d.engine.*;
import com.phantasia.j2d.render.ParchmentRenderer;
import com.phantasia.j2d.render.ParchmentRenderer.TextStyle;
import com.phantasia.j2d.render.WeatherParticleSystem;
import com.phantasia.j2d.tour.KenneyTileLoader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.phantasia.j2d.render.ParchmentRenderer.*;

/**
 * Primary world-roam screen — free-float movement across the overworld.
 *
 * <h3>Rendering layers (back to front)</h3>
 * <ol>
 *   <li>Solid VOID background</li>
 *   <li>Tile grid — Kenney image if available, fallback solid color otherwise</li>
 *   <li>Feature markers (towns, dungeons) with name labels</li>
 *   <li>Decorations (trees, rocks, foliage from WFC placement)</li>
 *   <li>Overworld NPC entities</li>
 *   <li>Player sprite</li>
 *   <li>Weather particle layer</li>
 *   <li>HUD overlay</li>
 * </ol>
 */
public class WorldRoamScreen implements Screen {

    // ─────────────────────────────────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────────────────────────────────

    private static final int   TILE_SIZE   = 128;
    private static final float MOVE_SPEED  = 256f;  // pixels/sec
    private static final int   PLAYER_SIZE = 40;

    /** Pre-cached decoration colors indexed by DecorationType.ordinal(). */
    private static final Color[] DECO_COLORS = buildDecoColors();

    private static Color[] buildDecoColors() {
        DecorationType[] types = DecorationType.values();
        Color[] colors = new Color[types.length];
        for (int i = 0; i < types.length; i++) {
            int rgb = types[i].fallbackRgb();
            colors[i] = new Color(
                    (rgb >> 16) & 0xFF,
                    (rgb >>  8) & 0xFF,
                    rgb        & 0xFF,
                    180);
        }
        return colors;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Dependencies (injected at construction)
    // ─────────────────────────────────────────────────────────────────────

    private final ScreenManager       screenManager;
    private final WorldMap            worldMap;
    private final GameSession         session;
    private final NavigationManager   navManager;
    private final FeatureRegistry     featureRegistry;
    private final KenneyTileLoader    tileLoader;
    private final SpellFactory        spellFactory;
    private final NpcRegistry         npcRegistry;
    private final ShopRegistry        shopRegistry;
    private final WorldClock          clock;
    private final WeatherManager      weatherManager;
    private final TerrainMetadata     terrainMeta;
    private final InteriorMapRegistry interiorMaps;
    private final PlacementData       placementData;

    // ─────────────────────────────────────────────────────────────────────
    // Runtime state
    // ─────────────────────────────────────────────────────────────────────

    private FloatPosition    playerPos;
    private WorldPosition    currentTile;
    private final Set<Integer> heldKeys     = new HashSet<>();
    private final EncounterTimer encounterTimer = new EncounterTimer();
    private final ActiveEntityManager entityManager = new ActiveEntityManager();
    private final WeatherParticleSystem weatherFx   = new WeatherParticleSystem();

    private BufferedImage playerSprite;

    private String locationLabel  = "";
    private String interactPrompt = "";
    private float  promptAlpha    = 0f;

    // ─────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────

    public WorldRoamScreen(ScreenManager screenManager,
                           WorldMap worldMap,
                           GameSession session,
                           NavigationManager navManager,
                           FeatureRegistry featureRegistry,
                           KenneyTileLoader tileLoader,
                           SpellFactory spellFactory,
                           NpcRegistry npcRegistry,
                           ShopRegistry shopRegistry,
                           WorldClock clock,
                           WeatherManager weatherManager,
                           TerrainMetadata terrainMeta,
                           InteriorMapRegistry interiorMaps,
                           PlacementData placementData) {
        this.screenManager  = screenManager;
        this.worldMap       = worldMap;
        this.session        = session;
        this.navManager     = navManager;
        this.featureRegistry = featureRegistry;
        this.tileLoader     = tileLoader;
        this.spellFactory   = spellFactory;
        this.npcRegistry    = npcRegistry;
        this.shopRegistry   = shopRegistry;
        this.clock          = clock;
        this.weatherManager = weatherManager;
        this.terrainMeta    = terrainMeta;
        this.interiorMaps   = interiorMaps;
        this.placementData  = placementData;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Screen lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onEnter(Object transitionData) {
        WorldPosition wp = session.getPosition();
        playerPos   = FloatPosition.fromTileCenter(wp, TILE_SIZE);
        currentTile = wp;
        heldKeys.clear();

        if (playerSprite == null) {
            try {
                playerSprite = ImageIO.read(new File("assets/PLAYER.png"));
            } catch (IOException e) {
                System.err.println("[WorldRoamScreen] assets/PLAYER.png not found — using fallback.");
            }
        }

        updateLocationLabel();

        // Spawn overworld NPCs
        entityManager.clear();
        if (npcRegistry != null && featureRegistry != null) {
            for (FeatureRecord rec : featureRegistry.getAll(FeatureType.NPC)) {
                NpcDefinition def = npcRegistry.get(rec.getId());
                if (def == null) continue;
                FloatPosition homePos = FloatPosition.fromTileCenter(
                        new WorldPosition(rec.getX(), rec.getY()), TILE_SIZE);
                entityManager.spawn(def, homePos);
            }
            System.out.println("[WorldRoam] Spawned " + entityManager.size() + " overworld NPC(s).");
        }
    }

    @Override
    public void onExit() {
        heldKeys.clear();
        promptAlpha = 0f;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Update
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void update(float dt) {
        // These run every tick regardless of movement
        entityManager.update(dt);
        clock.update(dt);
        weatherManager.update(dt, clock);
        weatherFx.update(dt, weatherManager.getCurrentWeather());

        // HUD prompt fade-in
        if (promptAlpha > 0f) promptAlpha = Math.min(1f, promptAlpha + dt * 3f);

        // Movement delta
        float dx = 0f, dy = 0f;
        if (heldKeys.contains(KeyEvent.VK_W) || heldKeys.contains(KeyEvent.VK_UP))    dy -= MOVE_SPEED * dt;
        if (heldKeys.contains(KeyEvent.VK_S) || heldKeys.contains(KeyEvent.VK_DOWN))  dy += MOVE_SPEED * dt;
        if (heldKeys.contains(KeyEvent.VK_A) || heldKeys.contains(KeyEvent.VK_LEFT))  dx -= MOVE_SPEED * dt;
        if (heldKeys.contains(KeyEvent.VK_D) || heldKeys.contains(KeyEvent.VK_RIGHT)) dx += MOVE_SPEED * dt;
        if (dx == 0f && dy == 0f) return;

        FloatMoveResult result = navManager.move(playerPos, dx, dy, worldMap, TILE_SIZE);
        if (!result.blocked()) {
            playerPos = result.newPosition();
        }

        if (result.crossedBoundary()) {
            currentTile = result.newTile();
            session.setPosition(currentTile);
            onTileCrossing(currentTile);
        }

        updateInteractPrompt();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tile crossing — encounters, features
    // ─────────────────────────────────────────────────────────────────────

    private void onTileCrossing(WorldPosition tile) {
        updateLocationLabel();

        Tile mapTile = worldMap.getTile(tile);
        WorldFeature feature = mapTile.getFeature();

        if (feature != null && feature.getType() != FeatureType.NONE) {
            onFeatureDetected(feature);
            return;  // no random encounters on feature tiles
        }

        if (encounterTimer.step(mapTile.getType())) {
            triggerEncounter();
        }
    }

    private void onFeatureDetected(WorldFeature feature) {
        if (featureRegistry == null) return;
        FeatureRecord record = featureRegistry.get(feature.getType(), feature.getId());
        if (record == null) return;

        String verb = switch (feature.getType()) {
            case NPC  -> "Press E to speak with ";
            default   -> "Press E to enter ";
        };
        interactPrompt = verb + record.getName();
        promptAlpha    = 0.01f;
    }

    private void triggerEncounter() {
        List<Monster> enemies   = EncounterFactory.generateEncounter(session.getParty());
        EncounterCondition cond = FormulaEngine.rollEncounterCondition();
        screenManager.fadeTo(GameState.ENCOUNTER_SPLASH,
                new EncounterSplashScreen.EncounterData(enemies, cond, session));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void render(Graphics2D g) {
        final int W = GameCanvas.DESIGN_WIDTH;
        final int H = GameCanvas.DESIGN_HEIGHT;

        float camX = playerPos.x() - W / 2f;
        float camY = playerPos.y() - H / 2f;

        // 1. Void background
        g.setColor(VOID);
        g.fillRect(0, 0, W, H);

        // 2. Tile grid
        int startTileX = Math.max(0, (int)(camX / TILE_SIZE) - 1);
        int startTileY = Math.max(0, (int)(camY / TILE_SIZE) - 1);
        int endTileX   = Math.min(worldMap.getWidth(),  startTileX + W / TILE_SIZE + 3);
        int endTileY   = Math.min(worldMap.getHeight(), startTileY + H / TILE_SIZE + 3);

        for (int ty = startTileY; ty < endTileY; ty++) {
            for (int tx = startTileX; tx < endTileX; tx++) {
                int screenX = (int)(tx * TILE_SIZE - camX);
                int screenY = (int)(ty * TILE_SIZE - camY);

                Tile     tile  = worldMap.getTile(tx, ty);
                TileType type  = tile.getType();

                BufferedImage img = (tileLoader != null) ? tileLoader.getTile(type) : null;
                if (img != null) {
                    g.drawImage(img, screenX, screenY, TILE_SIZE, TILE_SIZE, null);
                } else {
                    g.setColor(fallbackColor(type));
                    g.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
                }

                // Feature marker drawn on top of tile, never obscured
                WorldFeature feat = tile.getFeature();
                if (feat != null && feat.getType() != FeatureType.NONE) {
                    renderFeatureMarker(g, feat, screenX, screenY);
                }
            }
        }   // both loops close here

        // 3. Decorations
        renderDecorations(g, camX, camY, W, H);

        // 4. Overworld NPCs
        renderEntities(g, camX, camY);

        // 5. Player
        int playerScreenX = W / 2 - PLAYER_SIZE / 2;
        int playerScreenY = H / 2 - PLAYER_SIZE / 2;
        if (playerSprite != null) {
            g.drawImage(playerSprite, playerScreenX, playerScreenY,
                    PLAYER_SIZE, PLAYER_SIZE, null);
        } else {
            g.setColor(GOLD_BRIGHT);
            int[] xp = { playerScreenX + PLAYER_SIZE/2, playerScreenX + PLAYER_SIZE,
                    playerScreenX + PLAYER_SIZE/2, playerScreenX };
            int[] yp = { playerScreenY, playerScreenY + PLAYER_SIZE/2,
                    playerScreenY + PLAYER_SIZE, playerScreenY + PLAYER_SIZE/2 };
            g.fillPolygon(xp, yp, 4);
            g.setColor(GOLD);
            g.drawPolygon(xp, yp, 4);
        }

        // 6. Weather particles (above player, below HUD)
        weatherFx.render(g);

        // 7. HUD (always topmost)
        renderHUD(g, W, H);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Feature markers
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

        if (featureRegistry != null) {
            FeatureRecord rec = featureRegistry.get(feat.getType(), feat.getId());
            if (rec != null) {
                drawTextCentered(g, rec.getName(), sx - 20, sy - 8,
                        TILE_SIZE + 40, TextStyle.DATA, color);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Decorations
    // ─────────────────────────────────────────────────────────────────────

    private void renderDecorations(Graphics2D g, float camX, float camY, int W, int H) {
        if (placementData == null) return;

        float tileF = (float) TILE_SIZE;
        float x1 = camX / tileF - 1f;
        float y1 = camY / tileF - 1f;
        float x2 = (camX + W) / tileF + 1f;
        float y2 = (camY + H) / tileF + 1f;

        for (PlacementEntry entry : placementData.getEntriesInRegion(x1, y1, x2, y2)) {
            int sx = (int)(entry.x() * TILE_SIZE - camX);
            int sy = (int)(entry.y() * TILE_SIZE - camY);
            int size = Math.max(3, (int)(entry.type().heightScale
                    * entry.scale() * TILE_SIZE * 0.28f));

            g.setColor(DECO_COLORS[entry.type().ordinal()]);

            switch (entry.type()) {
                case TREE_OAK, TREE_PINE, TREE_DEAD, DEAD_TREE -> {
                    int[] xp = { sx, sx - size/2, sx + size/2 };
                    int[] yp = { sy - size, sy, sy };
                    g.fillPolygon(xp, yp, 3);
                }
                case ROCK_SMALL, ROCK_LARGE, BOULDER, MOSS_PATCH ->
                        g.fillOval(sx - size/2, sy - size/3, size, size * 2 / 3);
                default ->
                        g.fillOval(sx - size/2, sy - size/2, size, size);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // NPC entities
    // ─────────────────────────────────────────────────────────────────────

    private void renderEntities(Graphics2D g, float camX, float camY) {
        for (ActiveEntity entity : entityManager.getAll()) {
            FloatPosition pos = entity.getPosition();
            int sx = (int)(pos.x() - camX) - PLAYER_SIZE / 2;
            int sy = (int)(pos.y() - camY) - PLAYER_SIZE / 2;

            if (sx < -PLAYER_SIZE * 2 || sx > GameCanvas.DESIGN_WIDTH  + PLAYER_SIZE * 2) continue;
            if (sy < -PLAYER_SIZE * 2 || sy > GameCanvas.DESIGN_HEIGHT + PLAYER_SIZE * 2) continue;

            // Glow
            g.setColor(new Color(74, 127, 165, 60));
            g.fillOval(sx - 3, sy - 3, PLAYER_SIZE + 6, PLAYER_SIZE + 6);

            // Body
            g.setColor(entity.isMoving() ? new Color(100, 160, 220) : MAGIC);
            g.fillOval(sx, sy, PLAYER_SIZE, PLAYER_SIZE);

            // Rim
            g.setColor(new Color(180, 210, 240, 140));
            g.setStroke(new BasicStroke(1f));
            g.drawOval(sx, sy, PLAYER_SIZE, PLAYER_SIZE);

            drawText(g, entity.getDefinition().getName(),
                    sx - 10, sy + PLAYER_SIZE + 14,
                    TextStyle.DATA, new Color(180, 210, 240, 200));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // HUD
    // ─────────────────────────────────────────────────────────────────────

    private void renderHUD(Graphics2D g, int W, int H) {

        // Location label (top-left)
        if (!locationLabel.isEmpty()) {
            int lw = 280, lh = 50;
            g.setColor(new Color(42, 33, 24, 216));
            g.fillRoundRect(20, 16, lw, lh, 6, 6);
            g.setColor(BORDER_STR);
            g.drawRoundRect(20, 16, lw, lh, 6, 6);
            drawText(g, locationLabel, 36, 48, TextStyle.LABEL, GOLD);
        }

        drawText(g, clock.getDisplayTime(), 36, 82, TextStyle.DATA, BORDER_STR);

        // Compass (top-right)
        int compassX = W - 100, compassY = 20, compassR = 40;
        g.setColor(new Color(42, 33, 24, 216));
        g.fillOval(compassX, compassY, compassR * 2, compassR * 2);
        g.setColor(GOLD);
        g.drawOval(compassX, compassY, compassR * 2, compassR * 2);
        drawTextCentered(g, "N", compassX, compassY + 22,
                compassR * 2, TextStyle.HEADER, GOLD_BRIGHT);
        String coords = currentTile.x() + ", " + currentTile.y();
        drawTextCentered(g, coords, compassX, compassY + 54,
                compassR * 2, TextStyle.DATA, new Color(0x8A7F6E));

        // Party health strip (bottom-left)
        int stripX = 20, stripY = H - 56;
        for (int i = 0; i < session.getParty().size(); i++) {
            PlayerCharacter pc = session.getParty().get(i);
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

        // Interact prompt (bottom-center)
        if (promptAlpha > 0.01f && !interactPrompt.isEmpty()) {
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, Math.min(1f, promptAlpha)));
            int pw = 440, ph = 36;
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
        switch (e.getKeyCode()) {
            case KeyEvent.VK_E      -> handleInteract();
            case KeyEvent.VK_ESCAPE -> screenManager.push(GameState.PAUSE, null);
        }
    }

    @Override
    public void onKeyReleased(KeyEvent e) {
        heldKeys.remove(e.getKeyCode());
    }

    @Override
    public void onMouseClicked(MouseEvent e) {}

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

            case TOWN -> {
                InteriorMap townMap = (interiorMaps != null)
                        ? interiorMaps.get(feat.getId()) : null;
                if (townMap != null) {
                    WorldPosition spawn = interiorMaps.getSpawnPosition(feat.getId());
                    screenManager.fadeTo(GameState.TOWN,
                            new TownRoamScreen.TownData(
                                    record.getName(), townMap,
                                    spawn, GameState.WORLD_ROAM));
                } else {
                    // Fallback: open a shop if no interior map authored yet
                    ShopInventory shop = buildDemoShop(record.getName());
                    screenManager.push(GameState.SHOP,
                            new ShopOverlay.ShopData(shop, session));
                }
            }

            case DUNGEON -> {
                DungeonFloor floor = DungeonFloorGenerator.generate(40, 30, 12);
                screenManager.fadeTo(GameState.DUNGEON,
                        new DungeonRoamScreen.DungeonData(
                                record.getName() + " \u2014 Floor 1",
                                floor, session, GameState.WORLD_ROAM));
            }

            case NPC -> {
                NpcDefinition npcDef = (npcRegistry != null)
                        ? npcRegistry.get(feat.getId()) : null;
                if (npcDef == null) return;
                dispatchNpc(npcDef);
            }

            default -> {}
        }
    }

    private void dispatchNpc(NpcDefinition npcDef) {
        switch (npcDef.getRole()) {

            case MERCHANT -> {
                ShopInventory shop = (shopRegistry != null)
                        ? shopRegistry.get(npcDef.getShopId()) : null;
                if (shop == null) shop = buildDemoShop(npcDef.getName());
                screenManager.push(GameState.SHOP,
                        new ShopOverlay.ShopData(shop, session));
            }

            case INNKEEPER -> screenManager.push(GameState.INN,
                    new InnOverlay.InnData(npcDef.getName() + "'s Inn", session));

            case TRAINER -> screenManager.push(GameState.GUILD,
                    new GuildOverlay.GuildData(npcDef.getName() + "'s Guild", session));

            case BANKER -> screenManager.push(GameState.BANK,
                    new BankOverlay.BankData(npcDef.getName() + "'s Bank", session));

            default -> {
                DialogueContext ctx = DialogueContext.freeze(npcDef.getName(), session);
                screenManager.push(GameState.DIALOGUE,
                        new DialogueScreen.DialogueData(
                                npcDef, session, ctx, List.of()));
            }
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
            if (rec != null) { locationLabel = rec.getName(); return; }
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
            promptAlpha = Math.max(0f, promptAlpha - 0.05f);
        }
    }

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

    /** Demo shop for towns that have no authored ShopRegistry entry yet. */
    private static ShopInventory buildDemoShop(String townName) {
        ShopInventory shop = new ShopInventory(0, townName + " General Store");
        shop.addEntry(new ShopEntry(1));    // Glove
        shop.addEntry(new ShopEntry(2));    // Wooden Shield
        shop.addEntry(new ShopEntry(21));   // Clothing
        shop.addEntry(new ShopEntry(23));   // Leather
        shop.addEntry(new ShopEntry(61));   // Knife
        shop.addEntry(new ShopEntry(62));   // Dagger
        shop.addEntry(new ShopEntry(101));  // Healing Potion 1
        shop.addEntry(new ShopEntry(103));  // Healing Potion 3
        shop.addEntry(new ShopEntry(111));  // Magic Potion 1
        return shop;
    }
}
