package com.phantasia.core.logic;

import com.phantasia.core.data.GameSession;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.world.*;

import java.util.Optional;

public class NavigationManager {

    private final WorldMap        worldMap;
    private final FeatureRegistry featureRegistry;

    public static class MoveResult {
        public final boolean moved;
        public final WorldPosition newPosition;
        public final ResultTile tile;
        private final WorldEvent event;

        private MoveResult(boolean moved, WorldPosition pos, ResultTile tile, WorldEvent event) {
            this.moved = moved;
            this.newPosition = pos;
            this.tile = tile;
            this.event = event;
        }

        public Optional<WorldEvent> worldEvent() {
            return Optional.ofNullable(event);
        }

        public static class ResultTile {
            private final TileType type;
            public ResultTile(TileType type) { this.type = type; }
            public TileType getType() { return type; }
        }

        public static MoveResult success(WorldPosition pos, TileType type, WorldEvent event) {
            return new MoveResult(true, pos, new ResultTile(type), event);
        }

        public static MoveResult blocked(WorldPosition current) {
            return new MoveResult(false, current, null, null);
        }
    }

    public NavigationManager() {
        this.worldMap        = null;
        this.featureRegistry = null;
    }

    public NavigationManager(WorldMap worldMap)
    {
        this.worldMap        = worldMap;
        this.featureRegistry = null;
    }

    public NavigationManager(WorldMap worldMap, FeatureRegistry featureRegistry) {
        this.worldMap        = worldMap;
        this.featureRegistry = featureRegistry;
    }

    public MoveResult attemptMove(String direction, GameSession session, WorldMap worldMap) {
        DungeonFloor dungeonFloor = session.getDungeonFloor();
        if (dungeonFloor != null) {
            return handleDungeonMovement(direction, session, dungeonFloor);
        } else {
            return handleOverworldMovement(direction, session, worldMap);
        }
    }

    public MoveResult attemptMove(String direction, PlayerCharacter lead) {
        // Using common TileType constant (e.g., GRASS or PLAIN) to avoid non-existent FLOOR
        return MoveResult.success(new WorldPosition(0, 0), TileType.PLAINS, null);
    }

    private MoveResult handleDungeonMovement(String direction, GameSession session, DungeonFloor floor) {
        WorldPosition targetPos = session.getPosition().step(direction);
        DungeonFloor.TileType dType = floor.getTile(targetPos.x(), targetPos.y());

        if (dType == DungeonFloor.TileType.WALL) {
            return MoveResult.blocked(new WorldPosition(targetPos.x(), targetPos.y()));
        }

        // Corrected phrasing: passing a new WorldPosition object
        session.setPosition(new WorldPosition(targetPos.x(), targetPos.y()));
        floor.updateExploration(targetPos.x(), targetPos.y(), 1);

        // Map to a valid overworld TileType constant for JME legacy
        TileType legacyType = (dType == DungeonFloor.TileType.STAIRS_DOWN) ? TileType.MOUNTAIN : TileType.PLAINS;
        return MoveResult.success(targetPos, legacyType, null);
    }

    private MoveResult handleOverworldMovement(String direction, GameSession session, WorldMap worldMap) {
        WorldPosition targetPos = session.getPosition().step(direction);

        if (worldMap == null || !worldMap.isPassable(new WorldPosition(targetPos.x(), targetPos.y()))) {
            return MoveResult.blocked(new WorldPosition(targetPos.x(), targetPos.y()));
        }

        // Corrected phrasing: passing a new WorldPosition object
        session.setPosition(new WorldPosition(targetPos.x(), targetPos.y()));
        return MoveResult.success(targetPos, worldMap.getTile(targetPos.x(), targetPos.y()).getType(), null);
    }

    // =========================================================================
    // Float-precision movement (Phase 1.1 — free-roam model)
    // =========================================================================

    /**
     * Attempts a float-precision move and reports tile boundary crossings.
     *
     * <p>The caller provides the current float position and a delta.
     * NavigationManager computes the new position, derives the tile
     * coordinate, checks passability on boundary crossings, and returns
     * a {@link FloatMoveResult} that tells the frontend everything it
     * needs: the new position, whether a boundary was crossed, and
     * whether movement was blocked.</p>
     *
     * <p><b>Boundary crossing rule:</b> A trigger fires when the derived
     * {@link com.phantasia.core.world.WorldPosition} changes between
     * frames. The frontend is responsible for executing encounter rolls,
     * feature detection, and NPC proximity checks when
     * {@code result.crossedBoundary()} is true.</p>
     *
     * @param current   the player's current float position
     * @param dx        horizontal delta (pixels, already scaled by deltaTime × speed)
     * @param dy        vertical delta
     * @param map       the world map for passability checks
     * @param tileSize  the side length of one tile in world units
     * @return a FloatMoveResult describing the outcome
     */
    public FloatMoveResult move(FloatPosition current, float dx, float dy,
                                WorldMap map, float tileSize) {

        WorldPosition currentTile = current.toTile(tileSize);
        FloatPosition candidate   = current.translate(dx, dy);
        WorldPosition candidateTile = candidate.toTile(tileSize);

        boolean tileChanged = !candidateTile.equals(currentTile);

        if (tileChanged) {
            // Boundary crossing — check passability of the target tile
            if (map != null && !map.isPassable(candidateTile)) {
                // Blocked: clamp position to stay within the current tile
                return FloatMoveResult.blocked(current, currentTile);
            }
            return FloatMoveResult.crossed(candidate, candidateTile, currentTile);
        }

        // No tile change — always allowed
        return FloatMoveResult.sameTile(candidate, candidateTile);
    }

    /**
     * Convenience overload using the default tile size of 32.
     */
    public FloatMoveResult move(FloatPosition current, float dx, float dy,
                                WorldMap map) {
        return move(current, dx, dy, map, 32.0f);
    }
}