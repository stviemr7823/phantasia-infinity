// phantasia-core/src/main/java/com/phantasia/core/world/FloatMoveResult.java
package com.phantasia.core.world;

/**
 * Result of a float-precision movement attempt via
 * {@link com.phantasia.core.logic.NavigationManager#move(FloatPosition, float, float, WorldMap, float)}.
 *
 * Carries the new float position, the derived tile, and whether a tile
 * boundary was crossed (which triggers encounter rolls, feature detection,
 * and NPC proximity checks in the frontend).
 *
 * @param newPosition    the float position after movement (clamped if blocked)
 * @param newTile        the tile derived from newPosition
 * @param crossedBoundary true if newTile differs from the previous tile
 * @param previousTile   the tile before movement (non-null only if crossedBoundary)
 * @param blocked        true if the target tile was impassable and movement was denied
 */
public record FloatMoveResult(
        FloatPosition newPosition,
        WorldPosition newTile,
        boolean       crossedBoundary,
        WorldPosition previousTile,
        boolean       blocked
) {

    /**
     * Successful move that stayed within the same tile.
     */
    public static FloatMoveResult sameTile(FloatPosition pos, WorldPosition tile) {
        return new FloatMoveResult(pos, tile, false, null, false);
    }

    /**
     * Successful move that crossed into a new tile.
     */
    public static FloatMoveResult crossed(FloatPosition pos, WorldPosition newTile,
                                          WorldPosition prevTile) {
        return new FloatMoveResult(pos, newTile, true, prevTile, false);
    }

    /**
     * Movement was blocked — position unchanged.
     */
    public static FloatMoveResult blocked(FloatPosition pos, WorldPosition tile) {
        return new FloatMoveResult(pos, tile, false, null, true);
    }
}