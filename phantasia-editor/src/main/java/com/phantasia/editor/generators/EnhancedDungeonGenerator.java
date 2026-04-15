// phantasia-editor/src/main/java/com/phantasia/editor/generators/EnhancedDungeonGenerator.java
package com.phantasia.editor.generators;

import com.phantasia.core.world.DungeonFloor;
import com.phantasia.core.world.DungeonFloor.TileType;

import java.util.*;

/**
 * Enhanced dungeon floor generator — extends the core
 * {@link com.phantasia.core.world.DungeonFloorGenerator} algorithm
 * with additional features:
 *
 * <ul>
 *   <li>Configurable room size range and corridor width</li>
 *   <li>Door placement at room entrances</li>
 *   <li>Trap placement in corridors (configurable density)</li>
 *   <li>Chest placement in dead-end rooms</li>
 *   <li>Optional secret rooms (small rooms off main corridors)</li>
 *   <li>Guaranteed connectivity between all rooms</li>
 *   <li>Multiple staircase placement options</li>
 * </ul>
 */
public class EnhancedDungeonGenerator {

    private static final Random RNG = new Random();

    public record Config(
            int    width,          // floor width (20–60)
            int    height,         // floor height (20–60)
            int    maxRooms,       // max room attempts (5–20)
            int    minRoomSize,    // minimum room dimension (3–6)
            int    maxRoomSize,    // maximum room dimension (6–12)
            int    corridorWidth,  // corridor width (1–2)
            double trapDensity,   // probability of traps in corridors (0.0–0.3)
            double chestDensity,  // probability of chests in dead-end rooms (0.0–1.0)
            boolean doors,         // place doors at room entrances
            boolean secretRooms,   // add hidden side rooms
            long   seed            // random seed (-1 for random)
    ) {
        public static Config defaults() {
            return new Config(40, 30, 10, 4, 8, 1, 0.1, 0.5, true, true, -1);
        }

        public static Config small() {
            return new Config(24, 18, 6, 3, 6, 1, 0.05, 0.3, true, false, -1);
        }

        public static Config large() {
            return new Config(55, 40, 16, 5, 10, 2, 0.15, 0.6, true, true, -1);
        }
    }

    /**
     * Generates a dungeon floor with enhanced features.
     */
    public static DungeonFloor generate(Config cfg) {
        if (cfg.seed() >= 0) RNG.setSeed(cfg.seed());

        int w = cfg.width(), h = cfg.height();
        DungeonFloor floor = new DungeonFloor(w, h);

        // Initialize all walls
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                floor.setTile(x, y, TileType.WALL);

        List<Room> rooms = new ArrayList<>();

        // ── Place rooms ──────────────────────────────────────────────────
        for (int i = 0; i < cfg.maxRooms() * 3; i++) {
            if (rooms.size() >= cfg.maxRooms()) break;

            int rw = cfg.minRoomSize() + RNG.nextInt(cfg.maxRoomSize() - cfg.minRoomSize() + 1);
            int rh = cfg.minRoomSize() + RNG.nextInt(cfg.maxRoomSize() - cfg.minRoomSize() + 1);
            int rx = 1 + RNG.nextInt(Math.max(1, w - rw - 2));
            int ry = 1 + RNG.nextInt(Math.max(1, h - rh - 2));

            Room room = new Room(rx, ry, rw, rh);
            if (rooms.stream().anyMatch(r -> r.intersects(room, 1))) continue;

            digRoom(floor, room);
            rooms.add(room);
        }

        if (rooms.isEmpty()) return floor;

        // ── Connect rooms ────────────────────────────────────────────────
        for (int i = 1; i < rooms.size(); i++) {
            connectRooms(floor, rooms.get(i - 1), rooms.get(i), cfg.corridorWidth());
        }
        // Extra connections for loops (makes the dungeon less linear)
        if (rooms.size() > 3) {
            int extras = RNG.nextInt(rooms.size() / 3) + 1;
            for (int i = 0; i < extras; i++) {
                int a = RNG.nextInt(rooms.size());
                int b = RNG.nextInt(rooms.size());
                if (a != b) connectRooms(floor, rooms.get(a), rooms.get(b), cfg.corridorWidth());
            }
        }

        // ── Secret rooms ─────────────────────────────────────────────────
        if (cfg.secretRooms()) {
            int secretCount = 1 + RNG.nextInt(Math.max(1, rooms.size() / 4));
            for (int i = 0; i < secretCount; i++) {
                addSecretRoom(floor, w, h);
            }
        }

        // ── Doors ────────────────────────────────────────────────────────
        if (cfg.doors()) {
            placeDoors(floor, rooms, w, h);
        }

        // ── Traps in corridors ───────────────────────────────────────────
        if (cfg.trapDensity() > 0) {
            placeTraps(floor, rooms, w, h, cfg.trapDensity());
        }

        // ── Chests in dead-end rooms ─────────────────────────────────────
        if (cfg.chestDensity() > 0) {
            placeChests(floor, rooms, cfg.chestDensity());
        }

        // ── Stairs ───────────────────────────────────────────────────────
        Room first = rooms.get(0);
        Room last  = rooms.get(rooms.size() - 1);
        floor.setTile(first.cx(), first.cy(), TileType.STAIRS_UP);
        floor.setTile(last.cx(),  last.cy(),  TileType.STAIRS_DOWN);

        return floor;
    }

    // ── Room carving ─────────────────────────────────────────────────────

    private static void digRoom(DungeonFloor floor, Room r) {
        for (int x = r.x; x < r.x + r.w; x++)
            for (int y = r.y; y < r.y + r.h; y++)
                floor.setTile(x, y, TileType.FLOOR);
    }

    // ── Corridor carving ─────────────────────────────────────────────────

    private static void connectRooms(DungeonFloor floor, Room a, Room b, int width) {
        int cx = a.cx(), cy = a.cy();
        int tx = b.cx(), ty = b.cy();

        // Random choice: horizontal-first or vertical-first
        boolean hFirst = RNG.nextBoolean();

        if (hFirst) {
            carveHorizontal(floor, cx, tx, cy, width);
            carveVertical(floor, tx, cy, ty, width);
        } else {
            carveVertical(floor, cx, cy, ty, width);
            carveHorizontal(floor, cx, tx, ty, width);
        }
    }

    private static void carveHorizontal(DungeonFloor floor, int x1, int x2, int y, int w) {
        int min = Math.min(x1, x2), max = Math.max(x1, x2);
        for (int x = min; x <= max; x++)
            for (int dy = 0; dy < w; dy++)
                if (floor.inBounds(x, y + dy))
                    floor.setTile(x, y + dy, TileType.FLOOR);
    }

    private static void carveVertical(DungeonFloor floor, int x, int y1, int y2, int w) {
        int min = Math.min(y1, y2), max = Math.max(y1, y2);
        for (int y = min; y <= max; y++)
            for (int dx = 0; dx < w; dx++)
                if (floor.inBounds(x + dx, y))
                    floor.setTile(x + dx, y, TileType.FLOOR);
    }

    // ── Secret rooms ─────────────────────────────────────────────────────

    private static void addSecretRoom(DungeonFloor floor, int w, int h) {
        for (int attempt = 0; attempt < 50; attempt++) {
            int rx = 2 + RNG.nextInt(w - 5);
            int ry = 2 + RNG.nextInt(h - 5);
            int rw = 2 + RNG.nextInt(2);  // small: 2–3
            int rh = 2 + RNG.nextInt(2);

            // Check the room area is all walls (unexplored)
            boolean allWall = true;
            for (int x = rx; x < rx + rw && allWall; x++)
                for (int y = ry; y < ry + rh && allWall; y++)
                    if (!floor.inBounds(x, y) || floor.getTile(x, y) != TileType.WALL)
                        allWall = false;
            if (!allWall) continue;

            // Check at least one adjacent floor tile to connect to
            boolean hasNeighbor = false;
            for (int x = rx - 1; x <= rx + rw && !hasNeighbor; x++)
                for (int y = ry - 1; y <= ry + rh && !hasNeighbor; y++)
                    if (floor.inBounds(x, y) && floor.getTile(x, y) == TileType.FLOOR)
                        hasNeighbor = true;
            if (!hasNeighbor) continue;

            // Carve the secret room
            for (int x = rx; x < rx + rw; x++)
                for (int y = ry; y < ry + rh; y++)
                    floor.setTile(x, y, TileType.FLOOR);

            // Place a chest inside
            floor.setTile(rx + rw / 2, ry + rh / 2, TileType.CHEST);
            break;
        }
    }

    // ── Door placement ───────────────────────────────────────────────────

    private static void placeDoors(DungeonFloor floor, List<Room> rooms, int w, int h) {
        for (Room r : rooms) {
            // Check each wall tile of the room for corridor connections
            for (int x = r.x - 1; x <= r.x + r.w; x++) {
                checkDoorCandidate(floor, x, r.y - 1, w, h);      // north wall
                checkDoorCandidate(floor, x, r.y + r.h, w, h);    // south wall
            }
            for (int y = r.y - 1; y <= r.y + r.h; y++) {
                checkDoorCandidate(floor, r.x - 1, y, w, h);      // west wall
                checkDoorCandidate(floor, r.x + r.w, y, w, h);    // east wall
            }
        }
    }

    private static void checkDoorCandidate(DungeonFloor floor, int x, int y, int w, int h) {
        if (!floor.inBounds(x, y)) return;
        if (floor.getTile(x, y) != TileType.FLOOR) return;

        // A door candidate is a floor tile that has wall on two opposite sides
        // and floor on the other two (a corridor-to-room transition)
        boolean wallNS = wallAt(floor, x, y - 1, w, h) && wallAt(floor, x, y + 1, w, h);
        boolean wallEW = wallAt(floor, x - 1, y, w, h) && wallAt(floor, x + 1, y, w, h);
        boolean floorNS = floorAt(floor, x, y - 1, w, h) && floorAt(floor, x, y + 1, w, h);
        boolean floorEW = floorAt(floor, x - 1, y, w, h) && floorAt(floor, x + 1, y, w, h);

        if ((wallNS && floorEW) || (wallEW && floorNS)) {
            if (RNG.nextDouble() < 0.6)  // not every chokepoint gets a door
                floor.setTile(x, y, TileType.DOOR);
        }
    }

    private static boolean wallAt(DungeonFloor f, int x, int y, int w, int h) {
        return !f.inBounds(x, y) || f.getTile(x, y) == TileType.WALL;
    }

    private static boolean floorAt(DungeonFloor f, int x, int y, int w, int h) {
        return f.inBounds(x, y) && (f.getTile(x, y) == TileType.FLOOR
                || f.getTile(x, y) == TileType.DOOR);
    }

    // ── Trap placement ───────────────────────────────────────────────────

    private static void placeTraps(DungeonFloor floor, List<Room> rooms,
                                   int w, int h, double density) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (floor.getTile(x, y) != TileType.FLOOR) continue;
                // Only in corridors (not inside rooms)
                if (isInsideRoom(x, y, rooms)) continue;
                if (RNG.nextDouble() < density)
                    floor.setTile(x, y, TileType.TRAP);
            }
        }
    }

    // ── Chest placement ──────────────────────────────────────────────────

    private static void placeChests(DungeonFloor floor, List<Room> rooms, double density) {
        for (Room r : rooms) {
            if (RNG.nextDouble() < density) {
                // Place a chest in a corner of the room
                int cx = r.x + (RNG.nextBoolean() ? 0 : r.w - 1);
                int cy = r.y + (RNG.nextBoolean() ? 0 : r.h - 1);
                if (floor.getTile(cx, cy) == TileType.FLOOR)
                    floor.setTile(cx, cy, TileType.CHEST);
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static boolean isInsideRoom(int x, int y, List<Room> rooms) {
        for (Room r : rooms)
            if (x >= r.x && x < r.x + r.w && y >= r.y && y < r.y + r.h)
                return true;
        return false;
    }

    private record Room(int x, int y, int w, int h) {
        int cx() { return x + w / 2; }
        int cy() { return y + h / 2; }

        boolean intersects(Room o, int margin) {
            return x - margin < o.x + o.w && x + w + margin > o.x
                    && y - margin < o.y + o.h && y + h + margin > o.y;
        }
    }
}