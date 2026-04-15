// phantasia-core/src/main/java/com/phantasia/core/world/WorldFeature.java
package com.phantasia.core.world;

/**
 * A tile-level feature reference — records which feature sits on a tile.
 *
 * WorldFeature is intentionally thin: just type + id. All rich metadata
 * (name, description, services) lives in FeatureRegistry, loaded from
 * features.dat. This keeps world.dat compact and tile data clean.
 *
 * ENCAPSULATION:
 *   Fields are private final. Access via {@link #getType()} and
 *   {@link #getId()}. This preserves the contract if the internal
 *   representation ever changes (e.g., packed int).
 */
public final class WorldFeature {

    public static final WorldFeature NONE =
            new WorldFeature(FeatureType.NONE, -1);

    private final FeatureType type;
    private final int         id;

    public WorldFeature(FeatureType type, int id) {
        this.type = type;
        this.id   = id;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public FeatureType getType() { return type; }
    public int         getId()   { return id;   }

    // -------------------------------------------------------------------------
    // Convenience factories
    // -------------------------------------------------------------------------

    public static WorldFeature town(int id)    {
        return new WorldFeature(FeatureType.TOWN, id);
    }

    public static WorldFeature dungeon(int id) {
        return new WorldFeature(FeatureType.DUNGEON, id);
    }

    public static WorldFeature npc(int id) {
        return new WorldFeature(FeatureType.NPC, id);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public boolean isTown()    { return type == FeatureType.TOWN;    }
    public boolean isDungeon() { return type == FeatureType.DUNGEON; }
    public boolean isNpc()     { return type == FeatureType.NPC;     }
    public boolean isNone()    { return type == FeatureType.NONE;    }

    @Override
    public String toString() {
        return type == FeatureType.NONE ? "NONE" : type + ":" + id;
    }
}