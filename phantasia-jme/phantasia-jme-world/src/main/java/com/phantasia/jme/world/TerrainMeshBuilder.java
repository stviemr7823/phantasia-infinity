// phantasia-jme/phantasia-jme-world/src/main/java/com/phantasia/jme/world/TerrainMeshBuilder.java
package com.phantasia.jme.world;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;
import com.phantasia.core.world.TileType;
import com.phantasia.core.world.WorldMap;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Builds a single JME {@link Mesh} representing the entire world map as a
 * continuous 3D heightmap terrain.
 *
 * Replaces {@link TileRenderer} entirely.  WorldState switches its
 * construction call and all other logic is unchanged.
 *
 * DESIGN:
 *   - (W+1) x (H+1) vertices, one per tile corner.
 *   - Vertex positions:  x = col * TILE_SIZE,
 *                        y = heightmap[col][row] * MAX_HEIGHT,
 *                        z = -row * TILE_SIZE   (JME right-hand coords)
 *   - Quads split into two CCW-wound triangles.
 *   - Per-vertex normals via Central Difference (O(n), zero heap alloc).
 *   - Per-vertex colors from the nearest tile's TileType, using the same
 *     palette as TileRenderer for visual continuity.
 *   - Direct native (off-heap) buffers via BufferUtils — no GC pressure.
 *   - updateBound() called after assembly — mandatory for frustum culling.
 *
 * USAGE:
 *   Mesh terrainMesh = TerrainMeshBuilder.build(heightmap, worldMap,
 *                                               TILE_SIZE, MAX_HEIGHT);
 *   Geometry geom = new Geometry("Terrain", terrainMesh);
 *   geom.setMaterial(terrainMaterial);
 *   rootNode.attachChild(geom);
 */
public final class TerrainMeshBuilder {

    private TerrainMeshBuilder() {} // utility class

    // -------------------------------------------------------------------------
    // Biome color palette — matches TileRenderer for visual continuity
    // -------------------------------------------------------------------------

    private static final float[] C_OCEAN    = { 0.10f, 0.25f, 0.55f, 1f };
    private static final float[] C_ROAD     = { 0.72f, 0.65f, 0.45f, 1f };
    private static final float[] C_PLAINS   = { 0.45f, 0.68f, 0.30f, 1f };
    private static final float[] C_FOREST   = { 0.15f, 0.42f, 0.18f, 1f };
    private static final float[] C_MOUNTAIN = { 0.55f, 0.52f, 0.48f, 1f };
    private static final float[] C_SWAMP    = { 0.30f, 0.38f, 0.22f, 1f };
    private static final float[] C_TOWN     = { 0.85f, 0.82f, 0.60f, 1f };
    private static final float[] C_DUNGEON  = { 0.22f, 0.18f, 0.28f, 1f };

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Builds and returns the terrain mesh.
     *
     * @param heightmap  source elevation data from HeightmapData
     * @param worldMap   provides tile types for vertex coloring
     * @param tileSize   world units per tile (match WorldState.TILE_SIZE)
     * @param maxHeight  world units for normalised height 1.0
     * @return           a fully assembled, bound-updated JME Mesh
     */
    public static Mesh build(HeightmapData heightmap,
                             WorldMap      worldMap,
                             float         tileSize,
                             float         maxHeight) {

        int cols = heightmap.getCols();   // mapWidth  + 1
        int rows = heightmap.getRows();   // mapHeight + 1

        int vertexCount   = cols * rows;
        int quadCount     = (cols - 1) * (rows - 1);
        int triangleCount = quadCount * 2;

        // --- Allocate direct off-heap buffers ---
        FloatBuffer posBuf  = BufferUtils.createFloatBuffer(vertexCount * 3);
        IntBuffer   idxBuf  = BufferUtils.createIntBuffer(triangleCount * 3);
        FloatBuffer colBuf  = BufferUtils.createFloatBuffer(vertexCount * 4);
        FloatBuffer normBuf = BufferUtils.createFloatBuffer(vertexCount * 3);
        // UV coords — tiled at UV_SCALE so texture repeats across the map
        FloatBuffer uvBuf   = BufferUtils.createFloatBuffer(vertexCount * 2);
        final float UV_SCALE = 0.5f;  // one texture repeat per 2 tiles

        float[][] hm = heightmap.getData();

        // =====================================================================
        // Phase 1 — Vertex positions and colors
        // =====================================================================

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {

                float x = col * tileSize;
                float y = hm[col][row] * maxHeight;
                float z = -row * tileSize;

                posBuf.put(x).put(y).put(z);

                // UV: tiled across the grid so textures repeat naturally
                uvBuf.put(col * UV_SCALE).put(row * UV_SCALE);

                // Nearest tile for this corner: clamp to valid tile range
                int tileCol = Math.min(col, worldMap.getWidth()  - 1);
                int tileRow = Math.min(row, worldMap.getHeight() - 1);
                float[] c = tileColor(worldMap.getTile(tileCol, tileRow).getType());
                colBuf.put(c[0]).put(c[1]).put(c[2]).put(c[3]);
            }
        }

        // =====================================================================
        // Phase 2 — Index buffer (CCW winding, two triangles per quad)
        // =====================================================================

        for (int row = 0; row < rows - 1; row++) {
            for (int col = 0; col < cols - 1; col++) {

                int topLeft     = (row * cols) + col;
                int topRight    = topLeft + 1;
                int bottomLeft  = ((row + 1) * cols) + col;
                int bottomRight = bottomLeft + 1;

                // Triangle 1 — correct winding for camera above terrain
                idxBuf.put(topLeft).put(topRight).put(bottomLeft);
                // Triangle 2 — correct winding for camera above terrain
                idxBuf.put(topRight).put(bottomRight).put(bottomLeft);
            }
        }

        // =====================================================================
        // Phase 3 — Per-vertex normals via Central Difference
        //
        // For a uniform grid, the unnormalised normal at (col, row) is:
        //   nx = h[col-1][row] - h[col+1][row]
        //   ny = 2.0  (constant — spans 2 grid units in both axes)
        //   nz = h[col][row-1] - h[col][row+1]
        // Then normalise manually (no Vector3f allocation in inner loop).
        // Edge samples clamp to the same vertex.
        // =====================================================================

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {

                float hLeft  = (col > 0)        ? hm[col - 1][row] : hm[col][row];
                float hRight = (col < cols - 1) ? hm[col + 1][row] : hm[col][row];
                float hDown  = (row > 0)        ? hm[col][row - 1] : hm[col][row];
                float hUp    = (row < rows - 1) ? hm[col][row + 1] : hm[col][row];

                // Central Difference normals for upward-facing terrain
                // nx: left-to-right height gradient (positive = slopes right)
                // nz: back-to-front height gradient (positive = slopes toward camera)
                float nx = (hRight - hLeft) * maxHeight;
                float ny = 2.0f * tileSize;
                float nz = (hUp - hDown) * maxHeight;

                // Manual normalisation — avoids Vector3f allocation
                float inv = 1.0f / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                normBuf.put(nx * inv).put(ny * inv).put(nz * inv);
            }
        }

        // =====================================================================
        // Phase 4 — Assemble mesh and compute bounding volume
        // =====================================================================

        Mesh mesh = new Mesh();
        mesh.setBuffer(Type.Position,  3, posBuf);
        mesh.setBuffer(Type.Index,     3, idxBuf);
        mesh.setBuffer(Type.Color,     4, colBuf);
        mesh.setBuffer(Type.Normal,    3, normBuf);
        mesh.setBuffer(Type.TexCoord,  2, uvBuf);

        // MANDATORY: without this, JME frustum culling may discard the mesh
        // as invisible even when it is squarely in front of the camera.
        mesh.updateBound();

        return mesh;
    }

    // -------------------------------------------------------------------------
    // Helper — color lookup
    // -------------------------------------------------------------------------

    private static float[] tileColor(TileType type) {
        return switch (type) {
            case OCEAN    -> C_OCEAN;
            case ROAD     -> C_ROAD;
            case PLAINS   -> C_PLAINS;
            case FOREST   -> C_FOREST;
            case MOUNTAIN -> C_MOUNTAIN;
            case SWAMP    -> C_SWAMP;
            case TOWN     -> C_TOWN;
            case DUNGEON  -> C_DUNGEON;
        };
    }
}