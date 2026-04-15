// phantasia-core/src/main/java/com/phantasia/core/world/Tile.java
package com.phantasia.core.world;

public class Tile {

    // 1. Fields are now private and final
    private final TileType          type;
    private final WorldFeature      feature;
    private final ScriptedEncounter scriptedEncounter;
    private final TileEvent         tileEvent;

    private Tile(Builder b) {
        this.type              = b.type;
        this.feature           = b.feature;
        this.scriptedEncounter = b.scriptedEncounter;
        this.tileEvent         = b.tileEvent;
    }

    public static Tile of(TileType type) {
        return new Builder(type).build();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public TileType getType() {
        return type;
    }

    public WorldFeature getFeature() {
        return feature;
    }

    public ScriptedEncounter getScriptedEncounter() {
        return scriptedEncounter;
    }

    public TileEvent getTileEvent() {
        return tileEvent;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    // Note: Assuming TileType also has encapsulated getters now (e.g., type.isPassable())
    // If TileType variables are still public, type.passable works fine.
    public boolean isPassable()    { return type.passable; }
    public int     getTimerDrain() { return type.timerDrain; }

    public boolean hasFeature() {
        // Assuming WorldFeature.type is accessible, or use feature.getType() if encapsulated
        return feature != null &&
                feature.getType() != FeatureType.NONE;
    }

    public boolean hasScriptedEncounter() {
        return scriptedEncounter != null &&
                !scriptedEncounter.isExhausted();
    }

    public boolean hasTileEvent() {
        return tileEvent != null && !tileEvent.isResolved();
    }

    // -------------------------------------------------------------------------
    // Builder (Acts as your "Setters" for object creation)
    // -------------------------------------------------------------------------

    public static class Builder {

        private final TileType type;
        private WorldFeature      feature           = WorldFeature.NONE;
        private ScriptedEncounter scriptedEncounter = null;
        private TileEvent         tileEvent         = null;

        public Builder(TileType type) {
            this.type = type;
        }

        public Builder feature(WorldFeature feature) {
            this.feature = feature;
            return this;
        }

        public Builder scripted(ScriptedEncounter encounter) {
            if (this.tileEvent != null)
                throw new IllegalStateException(
                        "A tile cannot have both a ScriptedEncounter and a TileEvent.");
            this.scriptedEncounter = encounter;
            return this;
        }

        public Builder event(TileEvent event) {
            if (this.scriptedEncounter != null)
                throw new IllegalStateException(
                        "A tile cannot have both a ScriptedEncounter and a TileEvent.");
            this.tileEvent = event;
            return this;
        }

        public Tile build() {
            return new Tile(this);
        }
    }
}