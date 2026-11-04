// phantasia-core/src/main/java/com/phantasia/core/world/SurfaceType.java
package com.phantasia.core.world;

/**
 * The visual surface material of a terrain cell.
 *
 * <p>Determined by {@link TerrainMetadataService} from slope and altitude.
 * Frontends map each value to their own asset:
 * <ul>
 *   <li>j2d — colour tint over the tile</li>
 *   <li>JME — TextureSplatMap layer index for the terrain shader</li>
 *   <li>libGDX — texture atlas region for sprite-based terrain</li>
 * </ul>
 *
 * <h3>Painting rules (from Stravinsky spec)</h3>
 * "Slope-aware shaders automatically paint flat areas with moss/grass
 * and steep inclines (&gt;45°) with obsidian textures.
 * Tri-Planar mapping prevents stretching on vertical surfaces."
 */
public enum SurfaceType {

    /** Coastal flats — between sea level and the first inland rise. */
    SAND,

    /** Low-lying open ground — plains and gentle hills. */
    GRASS,

    /** Forest floor and damp lowlands — near water or dense canopy. */
    MOSS,

    /** Mid-slope terrain — mountain bases and rocky ridges. */
    ROCK,

    /**
     * Steep inclines (&gt;45° equivalent) and fault-line faces.
     * The signature dark-stone texture of the setting.
     */
    OBSIDIAN,

    /** High-altitude caps — peaks above the snow line. */
    SNOW;

    /**
     * Returns a j2d-friendly colour hint for this surface as a packed
     * 0xRRGGBB int. Frontends that can't do full material rendering
     * (j2d tile tinting) use this for a lightweight visual cue.
     */
    public int hintRgb() {
        return switch (this) {
            case SAND     -> 0xC8B87A;
            case GRASS    -> 0x558833;
            case MOSS     -> 0x2E5E2E;
            case ROCK     -> 0x7A7060;
            case OBSIDIAN -> 0x2A2030;
            case SNOW     -> 0xDDDDE8;
        };
    }
}
