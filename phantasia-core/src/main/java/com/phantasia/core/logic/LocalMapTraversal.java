package com.phantasia.core.logic;

import com.phantasia.core.data.*;
import com.phantasia.core.model.PlacedNpc;
import com.phantasia.core.model.item.Inventory;
import com.phantasia.core.world.*;
import com.phantasia.core.world.ServiceData.*;
import java.util.*;

/** Shared authored-map movement. Local grids are Y-down; world coordinates stay
 * in GameSession while position, facing and exploration belong to this visit.
 * Map definitions are never modified by traversal.
 */
public final class LocalMapTraversal {
    public enum Kind { MOVED, BLOCKED, NPC, FEATURE, CHANGED_MAP, EXIT }
    public record Result(Kind kind, PlacedNpc npc, PlacedFeature feature, String message) {}
    private final InteriorMapRegistry registry;
    private final GameSession session;
    private final WorldPosition worldReturn;
    private final java.util.function.Predicate<WorldPosition> worldReturnAllowed;
    private final Map<Integer, BitSet> explored = new HashMap<>();
    private InteriorMap map;
    private InteriorTileSet tiles;
    private WorldPosition position;
    private Direction facing;
    private boolean exited;

    public LocalMapTraversal(InteriorMapRegistry registry, GameSession session, int mapId, String entranceId) {
        this(registry, session, mapId, entranceId, p -> p.x() >= 0 && p.y() >= 0);
    }
    public LocalMapTraversal(InteriorMapRegistry registry, GameSession session, int mapId, String entranceId,
                             java.util.function.Predicate<WorldPosition> worldReturnAllowed) {
        this.worldReturnAllowed = Objects.requireNonNull(worldReturnAllowed);
        this.registry = Objects.requireNonNull(registry); this.session = Objects.requireNonNull(session);
        worldReturn = Objects.requireNonNull(session.getWorldPosition());
        InteriorMap selected = registry.get(mapId);
        if (selected == null) throw new IllegalArgumentException("Unknown interior map #" + mapId);
        String key = entranceId == null ? selected.getDefaultEntranceId() : entranceId;
        arrive(registry.resolveLink(new MapLinkData(mapId, key)));
    }
    public InteriorMap map() { return map; }
    public WorldPosition position() { return position; }
    public Direction facing() { return facing; }
    public boolean hasExited() { return exited; }
    public boolean featureAvailable(PlacedFeature f) {
        return f != null && f.isActive(session::hasFlag)
                && (f.consumedFlag() == null || !session.hasFlag(f.consumedFlag()));
    }
    public PlacedNpc visibleNpcAt(int x, int y) {
        // Search every placement: a hidden NPC must not mask another visible one.
        return map.getPlacedNpcs().stream().filter(n -> n.x() == x && n.y() == y && n.isVisible(session::hasFlag)).findFirst().orElse(null);
    }
    private Result result(Kind k, PlacedNpc n, PlacedFeature f, String text) { return new Result(k, n, f, text); }
    private Result blocked(String message) { return result(Kind.BLOCKED, null, null, message); }
    private static int dx(Direction d) { return d == Direction.EAST ? 1 : d == Direction.WEST ? -1 : 0; }
    private static int dy(Direction d) { return d == Direction.SOUTH ? 1 : d == Direction.NORTH ? -1 : 0; }

    public Result move(Direction direction) {
        if (exited) throw new IllegalStateException("This local-map visit has ended");
        facing = Objects.requireNonNull(direction);
        int x = position.x() + dx(direction), y = position.y() + dy(direction);
        if (!map.inBounds(x, y)) return blocked("The edge of this area.");
        var tile = tiles.getEntry(map.getTile(x, y));
        if (tile == null) return blocked("Unknown tile at (" + x + "," + y + ").");
        PlacedNpc npc = visibleNpcAt(x, y);
        if (npc != null) return result(Kind.NPC, npc, null, "");
        PlacedFeature feature = map.getFeatureAt(x, y);
        boolean door = feature != null && feature.featureType() == PlacedFeatureType.LOCKED_DOOR;
        boolean openDoor = door && feature.consumedFlag() != null && session.hasFlag(feature.consumedFlag());
        boolean unlock = door && !openDoor && feature.isActive(session::hasFlag) && canUnlock(feature);
        if (door && !openDoor && !unlock)
            return result(Kind.FEATURE, null, feature, feature.serviceData() instanceof LockData lock && lock.lockedMessage() != null ? lock.lockedMessage() : "The door is locked.");
        if (feature != null && (feature.featureType() == PlacedFeatureType.STAIRS_LINK || feature.featureType() == PlacedFeatureType.EXIT)
                && !featureAvailable(feature)) return blocked("This passage is not available.");
        if (!tile.passable() && !openDoor && !unlock) {
            if (featureAvailable(feature)) return result(Kind.FEATURE, null, feature, "");
            // Service NPCs behind authored counter tiles, never through ordinary walls.
            if (tile.interactionType() == InteractionType.BUMP_TRIGGER) {
                for (int step = 1; step <= 3; step++) {
                    int sx = x + dx(direction) * step, sy = y + dy(direction) * step;
                    if (!map.inBounds(sx, sy)) break;
                    var next = tiles.getEntry(map.getTile(sx, sy));
                    if (next == null || !next.passable()) break;
                    npc = visibleNpcAt(sx, sy);
                    if (npc != null) return result(Kind.NPC, npc, null, "");
                }
            }
            return blocked("The way is blocked.");
        }
        if (featureAvailable(feature) && feature.featureType() == PlacedFeatureType.STAIRS_LINK) {
            try {
                if (feature.serviceData() instanceof MapLinkData link) arrive(registry.resolveLink(link));
                else if (feature.serviceData() instanceof StairsData stairs) arriveCoordinate(stairs);
                else return blocked("This passage has no valid destination.");
                consume(feature); // only after destination resolution succeeds
                return result(Kind.CHANGED_MAP, null, feature, map.getName());
            } catch (IllegalArgumentException | IllegalStateException ex) {
                return blocked("Cannot use passage: " + ex.getMessage());
            }
        }
        if (featureAvailable(feature) && feature.featureType() == PlacedFeatureType.EXIT) {
            if (feature.serviceData() != null && !(feature.serviceData() instanceof ExitData)) return blocked("This exit has invalid return data.");
            WorldPosition target = feature.serviceData() instanceof ExitData e && e.returnPosition() != null ? e.returnPosition() : worldReturn;
            if (!worldReturnAllowed.test(target)) return blocked("This exit has invalid return coordinates.");
            session.setPosition(target); exited = true; consume(feature);
            return result(Kind.EXIT, null, feature, "Leaving " + map.getName());
        }
        if (unlock) consume(feature);
        position = new WorldPosition(x, y); reveal();
        if (!door && featureAvailable(feature)) return result(Kind.FEATURE, null, feature, "");
        return result(Kind.MOVED, null, null, "");
    }
    private boolean canUnlock(PlacedFeature feature) {
        if (!(feature.serviceData() instanceof LockData lock)) return false;
        return lock.requiredFlag() != null && session.hasFlag(lock.requiredFlag())
                || lock.requiredItemId() != 0 && session.getParty().stream().anyMatch(p -> new Inventory(p).contains(lock.requiredItemId()));
    }
    private void consume(PlacedFeature f) { if (f != null && f.consumedFlag() != null) session.setFlag(f.consumedFlag()); }
    /** Call only after completing the interaction's actual effect (e.g. displaying a sign).
     * Reward/service implementations remain responsible for their own successful completion.
     */
    public void completeInteraction(PlacedFeature f) {
        if (exited || f == null || map.getFeatureAt(f.x(), f.y()) != f || !featureAvailable(f))
            throw new IllegalArgumentException("Interaction is no longer available");
        consume(f);
    }
    private void arrive(InteriorMapRegistry.Arrival arrival) {
        var set = AuthoredContentValidator.tileSet(arrival.map().getTileSetId());
        if (set == null) throw new IllegalArgumentException("Unknown tile set: " + arrival.map().getTileSetId());
        map = arrival.map(); tiles = set; position = arrival.entrance().position(); facing = arrival.entrance().facing(); reveal();
    }
    private void arriveCoordinate(StairsData stairs) {
        if (stairs.targetFloor() != 0) throw new IllegalArgumentException("Use separate authored maps for dungeon floors");
        InteriorMap target = stairs.targetMapId() == 0 ? map : registry.get(stairs.targetMapId());
        if (target == null || !MapEntrances.isClear(target, stairs.targetPosition())) throw new IllegalArgumentException("Destination tile is missing or blocked");
        arrive(new InteriorMapRegistry.Arrival(target, new MapEntrance("coordinate", stairs.targetPosition(), facing)));
    }
    public int visibleRadius() {
        var s = map.getSettings(); int radius = Math.max(0, s.getTorchRadius());
        return s.getAmbientLight() == InteriorSettings.LightingMode.DARK ? Math.max(0, radius - 1) : radius;
    }
    public boolean isVisible(int x, int y) {
        if (!map.inBounds(x, y)) return false;
        if (!map.getSettings().isFogOfWar()) return true;
        long xdiff = (long) x - position.x(), ydiff = (long) y - position.y(), radius = visibleRadius();
        return xdiff * xdiff + ydiff * ydiff <= radius * radius;
    }
    public boolean isExplored(int x, int y) {
        var bits = explored.get(map.getId());
        return map.inBounds(x, y) && bits != null && bits.get(y * map.getWidth() + x);
    }
    private void reveal() {
        var bits = explored.computeIfAbsent(map.getId(), ignored -> new BitSet());
        int r = map.getSettings().isFogOfWar() ? visibleRadius() : Math.max(map.getWidth(), map.getHeight());
        int minX = Math.max(0, position.x() - r), maxX = (int) Math.min((long) map.getWidth() - 1, (long) position.x() + r);
        int minY = Math.max(0, position.y() - r), maxY = (int) Math.min((long) map.getHeight() - 1, (long) position.y() + r);
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) if (isVisible(x, y)) bits.set(y * map.getWidth() + x);
    }
}
