// phantasia-core/src/main/java/com/phantasia/core/world/InteriorSettings.java
package com.phantasia.core.world;

/**
 * Behavioral configuration for an interior map.
 *
 * These settings determine how the map feels and plays. A town and a
 * dungeon can share the same grid structure but behave completely
 * differently based on these flags.
 *
 * Default values are provided by {@link #forTown()} and {@link #forDungeon()}.
 */
public class InteriorSettings {

    private boolean fogOfWar;
    private boolean encountersEnabled;
    private int     encounterInterval;    // steps between random encounters
    private LightingMode ambientLight;
    private int     torchRadius;          // tiles of visibility (fog mode only)
    private String  musicTrackId;         // background music asset reference

    // -------------------------------------------------------------------------
    // Lighting modes
    // -------------------------------------------------------------------------

    public enum LightingMode {
        /** Full visibility — all tiles visible at all times. Towns. */
        FULL,
        /** Torch-radius visibility with fog of war. Standard dungeons. */
        TORCH,
        /** No ambient light — torch radius reduced, memory tiles darker. */
        DARK
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /** Default settings for a town: lit, peaceful, no encounters. */
    public static InteriorSettings forTown() {
        InteriorSettings s = new InteriorSettings();
        s.fogOfWar          = false;
        s.encountersEnabled = false;
        s.encounterInterval = 0;
        s.ambientLight      = LightingMode.FULL;
        s.torchRadius       = 0;
        s.musicTrackId      = null;
        return s;
    }

    /** Default settings for a dungeon: fog, encounters, torch lighting. */
    public static InteriorSettings forDungeon() {
        InteriorSettings s = new InteriorSettings();
        s.fogOfWar          = true;
        s.encountersEnabled = true;
        s.encounterInterval = 18;
        s.ambientLight      = LightingMode.TORCH;
        s.torchRadius       = 3;
        s.musicTrackId      = null;
        return s;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public boolean      isFogOfWar()          { return fogOfWar; }
    public boolean      isEncountersEnabled() { return encountersEnabled; }
    public int          getEncounterInterval(){ return encounterInterval; }
    public LightingMode getAmbientLight()     { return ambientLight; }
    public int          getTorchRadius()      { return torchRadius; }
    public String       getMusicTrackId()     { return musicTrackId; }

    public void setFogOfWar(boolean v)              { this.fogOfWar = v; }
    public void setEncountersEnabled(boolean v)     { this.encountersEnabled = v; }
    public void setEncounterInterval(int v)         { this.encounterInterval = v; }
    public void setAmbientLight(LightingMode v)     { this.ambientLight = v; }
    public void setTorchRadius(int v)               { this.torchRadius = v; }
    public void setMusicTrackId(String v)           { this.musicTrackId = v; }
}