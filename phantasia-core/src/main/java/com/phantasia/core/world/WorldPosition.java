package com.phantasia.core.world;

/**
 * Immutable (x, y) position on the world grid.
 * One unit = one tile = one pace of travel. [cite: 1038-1039]
 */
public record WorldPosition(int x, int y) {

    // Helper methods to return new instances for movement [cite: 1040-1043]
    public WorldPosition north() { return new WorldPosition(x,     y + 1); }
    public WorldPosition south() { return new WorldPosition(x,     y - 1); }
    public WorldPosition east()  { return new WorldPosition(x + 1, y); }
    public WorldPosition west()  { return new WorldPosition(x - 1, y); }

    /**
     * Translates a string direction into a new WorldPosition [cite: 1044-1045]
     */
    public WorldPosition step(String direction) {
        return switch (direction.toLowerCase()) {
            case "north" -> north();
            case "south" -> south();
            case "east"  -> east();
            case "west"  -> west();
            default      -> this;
        };
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}