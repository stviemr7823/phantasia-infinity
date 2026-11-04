// phantasia-core/src/main/java/com/phantasia/core/world/TerrainMetadata.java
package com.phantasia.core.world;

/**
 * The complete terrain metadata grid produced by {@link TerrainMetadataService}.
 *
 * <p>One {@link TerrainCell} per world-map tile. Coordinates match
 * {@link WorldMap} exactly: (0,0) is the south-west corner, Y increases
 * northward, X increases eastward.</p>
 *
 * <h3>JME usage</h3>
 * <pre>
 *   TerrainMetadata meta = service.compute(worldMap);
 *
 *   // Build a JME height map from the normalised heights
 *   float[] heightMap = new float[meta.getWidth() * meta.getHeight()];
 *   for (int y = 0; y &lt; meta.getHeight(); y++)
 *       for (int x = 0; x &lt; meta.getWidth(); x++)
 *           heightMap[y * meta.getWidth() + x] =
 *                   meta.getHeight(x, y) * MAX_TERRAIN_HEIGHT;
 *
 *   // Build a texture splat map from surface types
 *   for (int y ...) for (int x ...)
 *       splatLayer[y][x] = meta.getSurface(x, y).ordinal();
 * </pre>
 *
 * <h3>libGDX usage</h3>
 * Read {@link #getSlope(int, int)} to select which sprite variant (flat/steep)
 * to render for each tile in the parallax depth layer.
 */
public class TerrainMetadata {

    private final TerrainCell[][] cells;
    private final int             width;
    private final int             height;

    // Pre-computed summary statistics (useful for shader uniforms)
    private final float maxHeight;
    private final float avgHeight;

    // -------------------------------------------------------------------------
    // Construction (package-private — built only by TerrainMetadataService)
    // -------------------------------------------------------------------------

    TerrainMetadata(TerrainCell[][] cells, int width, int height) {
        this.cells  = cells;
        this.width  = width;
        this.height = height;

        float max = 0f, sum = 0f;
        int   count = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                float h = cells[x][y].height();
                if (h > max) max = h;
                sum += h;
                count++;
            }
        }
        this.maxHeight = max;
        this.avgHeight = count > 0 ? sum / count : 0f;
    }

    // -------------------------------------------------------------------------
    // Tile access
    // -------------------------------------------------------------------------

    /**
     * Returns the full {@link TerrainCell} for position (x, y).
     * Returns a sea-level SAND cell if out of bounds.
     */
    public TerrainCell getCell(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height)
            return new TerrainCell(0f, 0f, SurfaceType.SAND);
        return cells[x][y];
    }

    /** Shortcut for {@code getCell(x,y).height()}. */
    public float getHeight(int x, int y) {
        return getCell(x, y).height();
    }

    /** Shortcut for {@code getCell(x,y).slope()}. */
    public float getSlope(int x, int y) {
        return getCell(x, y).slope();
    }

    /** Shortcut for {@code getCell(x,y).surface()}. */
    public SurfaceType getSurface(int x, int y) {
        return getCell(x, y).surface();
    }

    // -------------------------------------------------------------------------
    // Bulk access — for JME TerrainQuad construction
    // -------------------------------------------------------------------------

    /**
     * Returns a flat float[] height map in row-major order (x-fast, y-slow),
     * scaled by {@code verticalScale}.
     *
     * <p>JME's {@code TerrainQuad} constructor expects a height map of size
     * {@code (width+1) * (height+1)} when using power-of-two tile counts.
     * This method returns {@code width * height} values — callers may need
     * to pad or resample.</p>
     *
     * @param verticalScale world-units per height unit (e.g. 200f for a
     *                      200-unit tall mountain at height=1.0)
     */
    public float[] toHeightArray(float verticalScale) {
        float[] arr = new float[width * height];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                arr[y * width + x] = cells[x][y].height() * verticalScale;
        return arr;
    }

    /**
     * Returns a byte[] splat-map where each byte is the {@link SurfaceType}
     * ordinal for that tile. Used by libGDX and j2d tinting.
     */
    public byte[] toSplatMap() {
        byte[] arr = new byte[width * height];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                arr[y * width + x] = (byte) cells[x][y].surface().ordinal();
        return arr;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int   getWidth()     { return width;     }
    public int   getHeight()    { return height;    }
    public float getMaxHeight() { return maxHeight; }
    public float getAvgHeight() { return avgHeight; }
}
