// phantasia-core/src/main/java/com/phantasia/core/data/InteriorMapRegistry.java
package com.phantasia.core.data;

import com.phantasia.core.logic.Direction;
import com.phantasia.core.model.PlacedNpc;
import com.phantasia.core.world.*;

import java.util.*;

/**
 * Runtime registry of all authored interior maps (town interiors, dungeon floors).
 *
 * <h3>ID conventions</h3>
 * <pre>
 *   0   — Pendragon town interior  (matches TownDefinition.id=0)
 *   1   — Scandor town interior    (matches TownDefinition.id=1)
 *   2   — Loftwood town interior   (matches TownDefinition.id=2)
 *   3   — Ironhaven town interior  (matches TownDefinition.id=3)
 *   100+ — dungeon floors (future)
 * </pre>
 *
 * <p>While {@code interiors.dat} has not yet been authored, {@link #buildDefault()}
 * provides the same maps programmatically. Replace with a file-based loader
 * once the editor pipeline is complete.</p>
 *
 * <h3>Map layout</h3>
 * Each town uses a 20×16 grid with the {@code town_standard} tile set.
 * Tile indices (from {@link InteriorTileSet#standardTown()}):
 * <pre>
 *   0 = wood_floor (passable)
 *   1 = stone_floor (passable)
 *   2 = wall (impassable)
 *   3 = counter (impassable, BUMP_TRIGGER)
 *  14 = cobblestone (passable)
 *  13 = fountain (impassable)
 *  16 = exit (passable, STEP_TRIGGER)
 * </pre>
 *
 * <h3>Service NPCs</h3>
 * Services are accessed by bumping into PlacedNpc characters on the map.
 * NPC roles (INNKEEPER, MERCHANT, TRAINER, BANKER) drive the service dispatch
 * in TownRoamScreen, matching the overworld NPC dispatch in WorldRoamScreen.
 *
 * <h3>Player spawn</h3>
 * {@link #getSpawnPosition(int)} returns the tile position where the player
 * appears when entering a town from the overworld.
 */
public final class InteriorMapRegistry {

    private final Map<Integer, InteriorMap> byId = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Mutation / lookup
    // -------------------------------------------------------------------------

    public void register(InteriorMap map) {
        byId.put(map.getId(), map);
    }

    /** Returns the interior map for the given ID, or {@code null} if not found. */
    public InteriorMap get(int id) {
        return byId.get(id);
    }

    public Collection<InteriorMap> getAll() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() { return byId.size(); }

    /**
     * Returns the player's spawn position when entering the given town map.
     * Uses this map's explicitly selected default entrance. Legacy imports and
     * built-in towns receive a suggested entry during creation, not at runtime.
     *
     * @param mapId  the interior map ID (matches TownDefinition.id)
     * @return tile position in the map's Y-down coordinate system
     */
    public WorldPosition getSpawnPosition(int mapId) {
        InteriorMap map = byId.get(mapId);
        if (map == null) throw new IllegalArgumentException("Unknown interior map #" + mapId);
        MapEntrance entry = map.getEntrance(map.getDefaultEntranceId());
        if (map.getDefaultEntranceId() == null || map.getDefaultEntranceId().isBlank()
                || entry == null || entry.facing() == null || !MapEntrances.isClear(map, entry.position()))
            throw new IllegalStateException("Map #" + mapId + " has no valid default entrance");
        return entry.position();
    }

    public record Arrival(InteriorMap map, MapEntrance entrance) {}

    /** Resolves an authored link without moving or mutating campaign state. */
    public Arrival resolveLink(ServiceData.MapLinkData link) {
        InteriorMap map = byId.get(link.targetMapId());
        MapEntrance entry = map == null ? null : map.getEntrance(link.entranceId());
        if (link.entranceId() == null || link.entranceId().isBlank()
                || entry == null || entry.facing() == null || !MapEntrances.isClear(map, entry.position()))
            throw new IllegalArgumentException("Unresolved or blocked entrance: map #"
                    + link.targetMapId() + " / " + link.entranceId());
        return new Arrival(map, entry);
    }

    // -------------------------------------------------------------------------
    // Default programmatic registry
    // -------------------------------------------------------------------------

    public static InteriorMapRegistry buildDefault() {
        InteriorMapRegistry reg = new InteriorMapRegistry();
        reg.register(buildPendragon());
        reg.register(buildScandor());
        reg.register(buildLoftwood());
        reg.register(buildIronhaven());
        return reg;
    }

    // =========================================================================
    // Pendragon — map ID 0
    // =========================================================================

    private static InteriorMap buildPendragon() {
        InteriorMap map = new InteriorMap(
                0, "Pendragon", InteriorMapType.TOWN, 20, 16, "town_standard");
        fillTownLayout(map, 20, 16);

        // ── Service NPCs ─────────────────────────────────────────────────────
        // 110 = Marta (innkeeper), facing south (y+ in Y-down), at top-left building
        map.addNpc(new PlacedNpc(110, 3, 4, Direction.SOUTH));
        // 111 = Aldric (merchant), at top-right building
        map.addNpc(new PlacedNpc(111, 15, 4, Direction.SOUTH));
        // 112 = Gregor (guild master), at bottom-left building
        map.addNpc(new PlacedNpc(112, 3, 11, Direction.NORTH));
        // 113 = Serafine (banker), at bottom-right building
        map.addNpc(new PlacedNpc(113, 15, 11, Direction.NORTH));

        // ── Placed feature: exit ──────────────────────────────────────────────
        map.addFeature(new PlacedFeature(9, 14, PlacedFeatureType.EXIT));

        MapEntrances.addSuggestedDefault(map);
        return map;
    }

    // =========================================================================
    // Scandor — map ID 1
    // =========================================================================

    private static InteriorMap buildScandor() {
        InteriorMap map = new InteriorMap(
                1, "Scandor", InteriorMapType.TOWN, 20, 16, "town_standard");
        fillTownLayout(map, 20, 16);

        // 210 = Kira (merchant), 211 = Bram (innkeeper)
        // Scandor is a smaller trading post — only inn and shop
        map.addNpc(new PlacedNpc(210, 15, 4, Direction.SOUTH));  // shop, top-right
        map.addNpc(new PlacedNpc(211,  3, 4, Direction.SOUTH));  // inn, top-left

        map.addFeature(new PlacedFeature(9, 14, PlacedFeatureType.EXIT));
        MapEntrances.addSuggestedDefault(map);
        return map;
    }

    // =========================================================================
    // Loftwood — map ID 2
    // =========================================================================

    private static InteriorMap buildLoftwood() {
        InteriorMap map = new InteriorMap(
                2, "Loftwood", InteriorMapType.TOWN, 20, 16, "town_standard");
        fillTownLayout(map, 20, 16);

        // 310 = Elara (merchant/healer)
        map.addNpc(new PlacedNpc(310, 15, 4, Direction.SOUTH));  // shop, top-right

        map.addFeature(new PlacedFeature(9, 14, PlacedFeatureType.EXIT));
        MapEntrances.addSuggestedDefault(map);
        return map;
    }

    // =========================================================================
    // Ironhaven — map ID 3
    // =========================================================================

    private static InteriorMap buildIronhaven() {
        InteriorMap map = new InteriorMap(
                3, "Ironhaven", InteriorMapType.TOWN, 20, 16, "town_standard");
        fillTownLayout(map, 20, 16);

        // 410 = Durrin (merchant/smith)
        map.addNpc(new PlacedNpc(410, 15, 4, Direction.SOUTH));  // shop, top-right

        map.addFeature(new PlacedFeature(9, 14, PlacedFeatureType.EXIT));
        MapEntrances.addSuggestedDefault(map);
        return map;
    }

    // =========================================================================
    // Shared tile layout for all 20×16 towns
    // =========================================================================

    /**
     * Fills a 20×16 town grid with the standard town interior layout.
     *
     * <pre>
     * Tile legend:
     *   2  = wall (impassable)
     *   0  = wood_floor (passable)
     *   14 = cobblestone (passable, central square)
     *   13 = fountain (impassable, decorative centre)
     *
     * Layout (y-down, 20 wide × 16 tall):
     *
     *   y= 0: WWWWWWWWWWWWWWWWWWWW  (perimeter)
     *   y= 1: W..................W
     *   y= 2: W.WWWWW.....WWWWW.W  top-left and top-right building shells
     *   y= 3: W.W000W.....W000W.W
     *   y= 4: W.W000W.....W000W.W  ← NPCs placed here (bump from y=5)
     *   y= 5: W.W000W.....W000W.W  ← player enters building from here
     *   y= 6: W.WWWWW.....WWWWW.W  building south wall
     *   y= 7: W...................W  central cobblestone
     *   y= 8: W.........F........W  F = fountain at (9,8)
     *   y= 9: W.WWWWW.....WWWWW.W  bottom-left and bottom-right building shells
     *   y=10: W.W000W.....W000W.W
     *   y=11: W.W000W.....W000W.W  ← NPCs placed here (bump from y=10)
     *   y=12: W.W000W.....W000W.W
     *   y=13: W.WWWWW.....WWWWW.W  building north wall (y-down = visually below)
     *   y=14: W.........E........W  E = exit tile at (9,14)
     *   y=15: WWWWWWWWWWWWWWWWWWWW
     *
     * Buildings occupy x=2-6 (left) and x=13-17 (right).
     * Central open square runs x=7-12, y=1-14.
     * </pre>
     */
    private static void fillTownLayout(InteriorMap map, int W, int H) {
        // Default fill: cobblestone (14) everywhere first
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++)
                map.setTile(x, y, 14);

        // Perimeter walls
        for (int x = 0; x < W; x++) {
            map.setTile(x, 0,   2);  // top wall
            map.setTile(x, H-1, 2);  // bottom wall
        }
        for (int y = 0; y < H; y++) {
            map.setTile(0,   y, 2);  // left wall
            map.setTile(W-1, y, 2);  // right wall
        }

        // ── Top-left building (x=2-6, y=2-6) ─────────────────────────────
        buildingShell(map, 2, 2, 5, 5);
        fillFloor(map, 3, 3, 4, 4, 0);  // wood floor interior

        // ── Top-right building (x=13-17, y=2-6) ──────────────────────────
        buildingShell(map, 13, 2, 17, 6);
        fillFloor(map, 14, 3, 16, 5, 0);

        // ── Bottom-left building (x=2-6, y=9-13) ─────────────────────────
        buildingShell(map, 2, 9, 6, 13);
        fillFloor(map, 3, 10, 5, 12, 0);

        // ── Bottom-right building (x=13-17, y=9-13) ──────────────────────
        buildingShell(map, 13, 9, 17, 13);
        fillFloor(map, 14, 10, 16, 12, 0);

        // ── Fountain at (9, 8) ────────────────────────────────────────────
        map.setTile(9, 8, 13);

        // ── Exit tile at (9, 14) — tile stays cobblestone; EXIT is a
        //    PlacedFeature, not a special tile index ─────────────────────────
        // (nothing to set — exit handled via PlacedFeature by callers)
    }

    /** Draws a 5-tile rectangular building shell with wall borders. */
    private static void buildingShell(InteriorMap map,
                                      int x1, int y1, int x2, int y2) {
        for (int x = x1; x <= x2; x++) {
            map.setTile(x, y1, 2);  // top wall
            map.setTile(x, y2, 2);  // bottom wall
        }
        for (int y = y1; y <= y2; y++) {
            map.setTile(x1, y, 2);  // left wall
            map.setTile(x2, y, 2);  // right wall
        }
    }

    /** Fills a rectangular interior region with the given tile index. */
    private static void fillFloor(InteriorMap map,
                                  int x1, int y1, int x2, int y2, int tileIdx) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                map.setTile(x, y, tileIdx);
    }
}

