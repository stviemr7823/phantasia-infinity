// phantasia-core/src/main/java/com/phantasia/core/world/TerrainCell.java
package com.phantasia.core.world;

/**
 * The computed terrain attributes for a single world-map tile.
 *
 * <p>Produced by {@link TerrainMetadataService} and consumed by frontends:
 * <ul>
 *   <li>JME — drives {@code TerrainQuad} height map and texture splatting</li>
 *   <li>libGDX — drives parallax depth layer and normal-map sprite choice</li>
 *   <li>j2d — provides a subtle height-based colour tint for map tiles</li>
 * </ul>
 *
 * @param height  normalised altitude, 0.0 (sea level) to 1.0 (mountain peak)
 * @param slope   normalised steepness, 0.0 (perfectly flat) to 1.0 (vertical cliff)
 * @param surface the classified surface material for texture selection
 */
public record TerrainCell(float height, float slope, SurfaceType surface) {

    /** Returns true if this cell is above the notional snow line. */
    public boolean isAboveSnowLine() { return surface == SurfaceType.SNOW; }

    /** Returns true if this cell has cliff-like steepness. */
    public boolean isCliff() {
        return surface == SurfaceType.OBSIDIAN || slope > 0.65f;
    }
}
