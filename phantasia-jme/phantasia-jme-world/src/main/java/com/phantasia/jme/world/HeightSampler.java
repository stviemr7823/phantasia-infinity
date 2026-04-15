// phantasia-jme/phantasia-jme-world/src/main/java/com/phantasia/jme/world/HeightSampler.java
package com.phantasia.jme.world;

import com.jme3.math.FastMath;

/**
 * Thread-safe, O(1) service for resolving terrain elevation at any
 * continuous world-space coordinate.
 *
 * Implemented as a Java 21 record — immutable, no boilerplate, implicitly
 * thread-safe for concurrent reads (multiple entities querying height
 * simultaneously without locks).
 *
 * ALGORITHM:
 *   Bilinear interpolation across the four nearest heightmap corners.
 *   Mirrors the rasterisation behaviour of the GPU exactly, so an entity
 *   placed by getHeightAt() sits flush on the visible surface.
 *
 * COORDINATE CONTRACT:
 *   worldX and worldZ are in the same space as vertex positions emitted
 *   by TerrainMeshBuilder:
 *       x = col * tileSize
 *       z = -row * tileSize
 *   The sampler works in grid-space internally (divides by tileSize),
 *   then multiplies the normalised result by maxHeight to return a world-Y.
 *
 * USAGE:
 *   HeightSampler sampler = new HeightSampler(heightmap, tileSize, maxHeight);
 *
 *   // Place player marker at the correct terrain height:
 *   float y = sampler.getHeightAt(worldX, worldZ);
 *   playerNode.setLocalTranslation(worldX, y, worldZ);
 */
public record HeightSampler(HeightmapData heightmap, float tileSize, float maxHeight) {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the world-space Y elevation (in world units) at the given
     * continuous XZ position.
     *
     * Safe to call with any finite float — out-of-bounds coordinates are
     * clamped to the map edge rather than throwing.
     *
     * @param worldX  X coordinate in the same space as TerrainMeshBuilder vertices
     * @param worldZ  Z coordinate (negative row direction)
     * @return        Y elevation in world units (normalised height × maxHeight)
     */
    public float getHeightAt(float worldX, float worldZ) {

        // Convert world coords to heightmap grid space.
        // Z is negated because the mesh uses z = -row * tileSize.
        float gridX = worldX / tileSize;
        float gridZ = -worldZ / tileSize;

        int cols = heightmap.getCols();
        int rows = heightmap.getRows();

        // Clamp with epsilon so x1/z1 never exceed array bounds.
        // The tiny 0.0001 margin keeps (int)(gridX) + 1 safely inside range.
        float cx = FastMath.clamp(gridX, 0f, cols - 1.0001f);
        float cz = FastMath.clamp(gridZ, 0f, rows - 1.0001f);

        // Integer lower bounds
        int x0 = (int) cx;
        int z0 = (int) cz;
        int x1 = x0 + 1;
        int z1 = z0 + 1;

        // Fractional penetration into the cell
        float fracX = cx - x0;
        float fracZ = cz - z0;

        // Four corner elevations (normalised)
        float h00 = heightmap.get(x0, z0);
        float h10 = heightmap.get(x1, z0);
        float h01 = heightmap.get(x0, z1);
        float h11 = heightmap.get(x1, z1);

        // Phase 1: interpolate along X at both Z bounds
        float interp0 = FastMath.interpolateLinear(fracX, h00, h10);
        float interp1 = FastMath.interpolateLinear(fracX, h01, h11);

        // Phase 2: interpolate along Z
        float normalisedHeight = FastMath.interpolateLinear(fracZ, interp0, interp1);

        return normalisedHeight * maxHeight;
    }
}