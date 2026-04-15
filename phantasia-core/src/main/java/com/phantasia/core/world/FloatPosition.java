// phantasia-core/src/main/java/com/phantasia/core/world/FloatPosition.java
package com.phantasia.core.world;

/**
 * Float-precision position for free-roam movement.
 *
 * The player (and pacing NPCs) move in continuous float space.
 * Tile-logic queries derive a {@link WorldPosition} via {@link #toTile(float)}:
 *
 *   WorldPosition = (floor(px / tileSize), floor(py / tileSize))
 *
 * This record is immutable. Movement produces new instances.
 *
 * @param x  horizontal position in world units (pixels at 1:1 zoom)
 * @param y  vertical position in world units
 */
public record FloatPosition(float x, float y) {

    /**
     * Returns a new position displaced by (dx, dy).
     */
    public FloatPosition translate(float dx, float dy) {
        return new FloatPosition(x + dx, y + dy);
    }

    /**
     * Derives the integer tile coordinate from this float position.
     *
     * @param tileSize  the width/height of one tile in world units
     * @return the tile-grid position this float position falls within
     */
    public WorldPosition toTile(float tileSize) {
        return new WorldPosition(
                (int) Math.floor(x / tileSize),
                (int) Math.floor(y / tileSize)
        );
    }

    /**
     * Euclidean distance to another float position.
     */
    public float distanceTo(FloatPosition other) {
        float dx = this.x - other.x;
        float dy = this.y - other.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Creates a FloatPosition centered on the given tile.
     *
     * @param tile      the tile coordinate
     * @param tileSize  the width/height of one tile in world units
     * @return float position at the center of the tile
     */
    public static FloatPosition fromTileCenter(WorldPosition tile, float tileSize) {
        return new FloatPosition(
                tile.x() * tileSize + tileSize * 0.5f,
                tile.y() * tileSize + tileSize * 0.5f
        );
    }

    @Override
    public String toString() {
        return String.format("(%.2f, %.2f)", x, y);
    }
}