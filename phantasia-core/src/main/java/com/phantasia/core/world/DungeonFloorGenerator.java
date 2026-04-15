package com.phantasia.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DungeonFloorGenerator {
    private static final Random RNG = new Random();

    public static DungeonFloor generate(int width, int height, int maxRooms) {
        DungeonFloor floor = new DungeonFloor(width, height);
        // 1. Initialize with Walls
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                floor.setTile(x, y, DungeonFloor.TileType.WALL);
            }
        }

        List<Room> rooms = new ArrayList<>();

        for (int i = 0; i < maxRooms; i++) {
            int w = 4 + RNG.nextInt(6); // Rooms 4-10 wide
            int h = 4 + RNG.nextInt(6);
            int x = RNG.nextInt(width - w - 1) + 1;
            int y = RNG.nextInt(height - h - 1) + 1;

            Room newRoom = new Room(x, y, w, h);

            // Check for overlap
            boolean overlaps = false;
            for (Room r : rooms) {
                if (newRoom.intersects(r)) {
                    overlaps = true;
                    break;
                }
            }

            if (!overlaps) {
                digRoom(floor, newRoom);
                if (!rooms.isEmpty()) {
                    // Connect to previous room
                    connectRooms(floor, rooms.get(rooms.size() - 1), newRoom);
                }
                rooms.add(newRoom);
            }
        }

        // 2. Place Stairs (Start in first room, End in last)
        if (!rooms.isEmpty()) {
            Room start = rooms.get(0);
            Room end = rooms.get(rooms.size() - 1);
            floor.setTile(start.centerX, start.centerY, DungeonFloor.TileType.STAIRS_UP);
            floor.setTile(end.centerX, end.centerY, DungeonFloor.TileType.STAIRS_DOWN);
        }

        return floor;
    }

    private static void digRoom(DungeonFloor floor, Room r) {
        for (int x = r.x1; x < r.x2; x++) {
            for (int y = r.y1; y < r.y2; y++) {
                floor.setTile(x, y, DungeonFloor.TileType.FLOOR);
            }
        }
    }

    private static void connectRooms(DungeonFloor floor, Room r1, Room r2) {
        // Horizontal then Vertical "L" corridor
        int currX = r1.centerX;
        int currY = r1.centerY;

        while (currX != r2.centerX) {
            floor.setTile(currX, currY, DungeonFloor.TileType.FLOOR);
            currX += (r2.centerX > currX) ? 1 : -1;
        }
        while (currY != r2.centerY) {
            floor.setTile(currX, currY, DungeonFloor.TileType.FLOOR);
            currY += (r2.centerY > currY) ? 1 : -1;
        }
    }

    private static class Room {
        int x1, y1, x2, y2, centerX, centerY;
        Room(int x, int y, int w, int h) {
            x1 = x; y1 = y; x2 = x + w; y2 = y + h;
            centerX = x + w / 2;
            centerY = y + h / 2;
        }
        boolean intersects(Room other) {
            return (x1 <= other.x2 && x2 >= other.x1 && y1 <= other.y2 && y2 >= other.y1);
        }
    }
}