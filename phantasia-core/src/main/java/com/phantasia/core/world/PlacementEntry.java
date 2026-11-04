// phantasia-core/src/main/java/com/phantasia/core/world/PlacementEntry.java
package com.phantasia.core.world;

/**
 * A single decoration object placed in the world.
 *
 * <p>Positions are in float-tile coordinates, matching {@link FloatPosition}.
 * A value of {@code (5.3f, 7.8f)} means the object sits 30% across tile 5
 * horizontally and 80% down tile 7 vertically (Y-up, overworld convention).
 * Frontends multiply by their tile-size constant to get pixel/world coordinates.</p>
 *
 * @param x          float-tile X position (includes sub-tile jitter)
 * @param y          float-tile Y position
 * @param type       the decoration to render
 * @param scale      size multiplier applied to {@link DecorationType#heightScale}
 *                   (1.0 = nominal, range typically 0.7–1.3)
 * @param rotDegrees Y-axis rotation in degrees — used by JME/libGDX for variety;
 *                   j2d can ignore this
 */
public record PlacementEntry(
        float          x,
        float          y,
        DecorationType type,
        float          scale,
        float          rotDegrees
) {}
