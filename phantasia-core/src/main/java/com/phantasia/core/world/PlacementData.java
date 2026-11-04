// phantasia-core/src/main/java/com/phantasia/core/world/PlacementData.java
package com.phantasia.core.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The complete set of overworld decoration placements produced by
 * {@link WfcPlacementService}.
 *
 * <h3>Viewport culling</h3>
 * {@link #getEntriesInRegion(float, float, float, float)} returns only
 * entries within a rectangular tile-coordinate region. Frontends call this
 * each frame with the visible tile range to avoid iterating all entries.
 * The full list ({@link #getAll()}) is available for cases where culling is
 * handled elsewhere (e.g. JME's spatial scene graph).
 *
 * <h3>Memory</h3>
 * A typical 32×32 overworld with default densities produces ~400–800 entries.
 * PlacementData is computed once at startup ({@code GameMain}) and held in
 * memory for the session.
 */
public class PlacementData {

    private final List<PlacementEntry> entries;

    // -------------------------------------------------------------------------
    // Construction (package-private — built only by WfcPlacementService)
    // -------------------------------------------------------------------------

    PlacementData(List<PlacementEntry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    // -------------------------------------------------------------------------
    // Access
    // -------------------------------------------------------------------------

    /**
     * Returns all placement entries (unmodifiable).
     */
    public List<PlacementEntry> getAll() {
        return entries;
    }

    /**
     * Returns entries whose float-tile position falls within the given
     * axis-aligned rectangle.
     *
     * <p>Use the visible tile range from the camera to cull efficiently:
     * <pre>
     *   float startTileX = camX / TILE_SIZE - 1;
     *   float endTileX   = (camX + screenW) / TILE_SIZE + 1;
     *   // ... same for Y
     *   List&lt;PlacementEntry&gt; visible = placementData.getEntriesInRegion(
     *           startTileX, startTileY, endTileX, endTileY);
     * </pre>
     *
     * @param x1  left   (tile coords, inclusive)
     * @param y1  bottom (tile coords, inclusive, Y-up)
     * @param x2  right  (tile coords, inclusive)
     * @param y2  top    (tile coords, inclusive)
     */
    public List<PlacementEntry> getEntriesInRegion(float x1, float y1,
                                                   float x2, float y2) {
        List<PlacementEntry> result = new ArrayList<>();
        for (PlacementEntry e : entries) {
            if (e.x() >= x1 && e.x() <= x2
                    && e.y() >= y1 && e.y() <= y2) {
                result.add(e);
            }
        }
        return result;
    }

    public int size() { return entries.size(); }

    @Override
    public String toString() {
        return "PlacementData[" + entries.size() + " entries]";
    }
}
