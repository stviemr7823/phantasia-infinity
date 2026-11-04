// phantasia-core/src/main/java/com/phantasia/core/logic/Direction.java
package com.phantasia.core.logic;

/**
 * Cardinal movement directions.
 *
 * Used by {@link GameCommand.Move} so that movement intent is expressed
 * as a typed value rather than a lowercase string.  WorldPosition.step()
 * should be updated to accept a Direction, but a legacy string bridge is
 * provided in the interim so existing code keeps compiling.
 *
 * COORDINATE CONVENTION (matches WorldPosition):
 *   NORTH → y + 1
 *   SOUTH → y - 1
 *   EAST  → x + 1
 *   WEST  → x - 1
 */
public enum Direction {
    NORTH, SOUTH, EAST, WEST;

    /**
     * Parses a legacy lowercase direction string ("north", "south", etc.).
     * Case-insensitive. Throws {@link IllegalArgumentException} for unknown strings.
     */
    public static Direction fromString(String s) {
        return switch (s.toUpperCase()) {
            case "NORTH" -> NORTH;
            case "SOUTH" -> SOUTH;
            case "EAST"  -> EAST;
            case "WEST"  -> WEST;
            default -> throw new IllegalArgumentException("Unknown direction: " + s);
        };
    }

    public int dx() {
        return switch (this) { case EAST -> 1; case WEST -> -1; default -> 0; };
    }
    public int dy() {
        return switch (this) { case SOUTH -> 1; case NORTH -> -1; default -> 0; };
    }

    /** Returns the lowercase string WorldPosition.step() currently expects. */
    public String toLegacyString() {
        return name().toLowerCase();
    }
}
