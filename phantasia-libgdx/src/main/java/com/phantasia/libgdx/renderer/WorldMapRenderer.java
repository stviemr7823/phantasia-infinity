package com.phantasia.libgdx.renderer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.phantasia.core.world.TileType;
import com.phantasia.core.world.WorldMap;

/**
 * Stateless world-map renderer for the LibGDX frontend.
 *
 * <h3>Coordinate system — no Y-flip needed</h3>
 * {@code WorldPosition} uses Y-up (y=0 is south, y increases northward).
 * LibGDX's {@link OrthographicCamera} also uses Y-up, so tile (tx, ty)
 * maps directly to world pixel {@code (tx * TILE_SIZE, ty * TILE_SIZE)}
 * with no flip required.
 *
 * <h3>Feature markers</h3>
 * Town and dungeon tiles carry a small coloured circle in their top-right
 * corner, matching the marker in {@code phantasia-j2d/tour/MapPanel}.
 * Gold for towns, purple for dungeons, with a translucent shadow underneath.
 * The dot is drawn as a dedicated pass after tiles so it is never obscured.
 *
 * <h3>Frustum culling with zoom</h3>
 * Visible half-extent = (viewportDimension * zoom) / 2.  Without the zoom
 * factor the frustum is computed incorrectly for non-1.0 zoom values.
 */
public class WorldMapRenderer {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    public static final float TILE_SIZE = 32f;

    // -------------------------------------------------------------------------
    // Feature dot geometry  (mirrors j2d MapPanel)
    //
    // J2D MapPanel: TILE = 64 px, dot radius = 7 px, corner gap = 5 px.
    // LibGDX TILE_SIZE = 32 world units.  At zoom 0.5 each world unit = 2 px:
    //   DOT_RADIUS  3.5 wu × 2 px/wu = 7 px   ✓ matches j2d
    //   DOT_GAP     2.5 wu × 2 px/wu = 5 px   ✓ matches j2d
    // -------------------------------------------------------------------------

    private static final float DOT_RADIUS = 3.5f;
    private static final float DOT_GAP    = 2.5f;
    private static final int   DOT_SEGS   = 20;

    private static final Color C_TOWN    = new Color(1.000f, 0.941f, 0.314f, 0.824f);
    private static final Color C_DUNGEON = new Color(0.784f, 0.314f, 1.000f, 0.824f);
    private static final Color C_SHADOW  = new Color(0.000f, 0.000f, 0.000f, 0.392f);

    // -------------------------------------------------------------------------
    // Fallback tile colours
    // -------------------------------------------------------------------------

    private static final Color[] FALLBACK_COLORS = buildFallbackTable();

    private static Color[] buildFallbackTable() {
        Color[] t = new Color[TileType.values().length];
        t[TileType.OCEAN   .ordinal()] = new Color(0.098f, 0.200f, 0.600f, 1f);
        t[TileType.ROAD    .ordinal()] = new Color(0.749f, 0.651f, 0.451f, 1f);
        t[TileType.PLAINS  .ordinal()] = new Color(0.349f, 0.651f, 0.247f, 1f);
        t[TileType.FOREST  .ordinal()] = new Color(0.098f, 0.400f, 0.098f, 1f);
        t[TileType.MOUNTAIN.ordinal()] = new Color(0.549f, 0.502f, 0.451f, 1f);
        t[TileType.SWAMP   .ordinal()] = new Color(0.298f, 0.400f, 0.200f, 1f);
        t[TileType.TOWN    .ordinal()] = new Color(0.851f, 0.749f, 0.298f, 1f);
        t[TileType.DUNGEON .ordinal()] = new Color(0.502f, 0.200f, 0.549f, 1f);
        return t;
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final WorldMap map;
    private final int      mapWidth;
    private final int      mapHeight;
    private KenneyTileSet  tileSet = null;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public WorldMapRenderer(WorldMap map) {
        this.map       = map;
        this.mapWidth  = map.getWidth();
        this.mapHeight = map.getHeight();
    }

    // -------------------------------------------------------------------------
    // Tile set wiring
    // -------------------------------------------------------------------------

    public void setTileSet(KenneyTileSet tileSet) {
        this.tileSet = tileSet;
    }

    // -------------------------------------------------------------------------
    // Frustum helper
    // -------------------------------------------------------------------------

    private static float[] frustum(OrthographicCamera camera) {
        float halfW = camera.viewportWidth  * camera.zoom / 2f;
        float halfH = camera.viewportHeight * camera.zoom / 2f;
        return new float[]{
                camera.position.x - halfW,
                camera.position.x + halfW,
                camera.position.y - halfH,
                camera.position.y + halfH
        };
    }

    // -------------------------------------------------------------------------
    // Render — solid-color path
    // -------------------------------------------------------------------------

    /**
     * Draws all visible tiles as solid-color rectangles, then feature dots.
     * The ShapeRenderer must be in {@link ShapeType#Filled} mode with the
     * camera's combined matrix set.  The caller owns begin/end.
     */
    public void render(ShapeRenderer sr, OrthographicCamera camera) {
        float[] f = frustum(camera);
        float camLeft = f[0], camRight = f[1], camBottom = f[2], camTop = f[3];

        for (int ty = 0; ty < mapHeight; ty++) {
            float screenY = ty * TILE_SIZE;
            if (screenY + TILE_SIZE < camBottom || screenY > camTop) continue;

            for (int tx = 0; tx < mapWidth; tx++) {
                float screenX = tx * TILE_SIZE;
                if (screenX + TILE_SIZE < camLeft || screenX > camRight) continue;

                TileType type = map.getTile(tx, ty).getType();
                Color fallback = (tileSet != null)
                        ? tileSet.getFallbackColor(type)
                        : FALLBACK_COLORS[type.ordinal()];

                sr.setColor(fallback);
                sr.rect(screenX, screenY, TILE_SIZE, TILE_SIZE);

                if (map.getTile(tx, ty).hasFeature()) {
                    drawDot(sr, screenX, screenY, type);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Render — textured path
    // -------------------------------------------------------------------------

    /**
     * Three passes: textured tiles, solid-color fallback tiles, feature dots.
     * The caller must NOT have either renderer open when this is called.
     */
    public void renderTextured(SpriteBatch batch, ShapeRenderer sr,
                               OrthographicCamera camera) {
        float[] f = frustum(camera);
        float camLeft = f[0], camRight = f[1], camBottom = f[2], camTop = f[3];

        // --- Pass 1: textured tiles ---
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (int ty = 0; ty < mapHeight; ty++) {
            float screenY = ty * TILE_SIZE;
            if (screenY + TILE_SIZE < camBottom || screenY > camTop) continue;

            for (int tx = 0; tx < mapWidth; tx++) {
                float screenX = tx * TILE_SIZE;
                if (screenX + TILE_SIZE < camLeft || screenX > camRight) continue;

                TileType type    = map.getTile(tx, ty).getType();
                Texture  texture = tileSet.getTexture(type);
                if (texture != null) {
                    batch.setColor(Color.WHITE);
                    batch.draw(texture, screenX, screenY, TILE_SIZE, TILE_SIZE);
                }
            }
        }
        batch.end();

        // --- Pass 2: solid-color fallback tiles ---
        sr.setProjectionMatrix(camera.combined);
        sr.begin(ShapeType.Filled);
        for (int ty = 0; ty < mapHeight; ty++) {
            float screenY = ty * TILE_SIZE;
            if (screenY + TILE_SIZE < camBottom || screenY > camTop) continue;

            for (int tx = 0; tx < mapWidth; tx++) {
                float screenX = tx * TILE_SIZE;
                if (screenX + TILE_SIZE < camLeft || screenX > camRight) continue;

                TileType type = map.getTile(tx, ty).getType();
                if (tileSet.getTexture(type) == null) {
                    sr.setColor(tileSet.getFallbackColor(type));
                    sr.rect(screenX, screenY, TILE_SIZE, TILE_SIZE);
                }
            }
        }
        sr.end();

        // --- Pass 3: feature dots (drawn last so never obscured by tiles) ---
        sr.begin(ShapeType.Filled);
        for (int ty = 0; ty < mapHeight; ty++) {
            float screenY = ty * TILE_SIZE;
            if (screenY + TILE_SIZE < camBottom || screenY > camTop) continue;

            for (int tx = 0; tx < mapWidth; tx++) {
                float screenX = tx * TILE_SIZE;
                if (screenX + TILE_SIZE < camLeft || screenX > camRight) continue;

                if (map.getTile(tx, ty).hasFeature()) {
                    drawDot(sr, screenX, screenY, map.getTile(tx, ty).getType());
                }
            }
        }
        sr.end();
    }

    // -------------------------------------------------------------------------
    // Feature dot
    // -------------------------------------------------------------------------

    /**
     * Draws a shadow circle then a coloured fill circle in the top-right
     * corner of the tile, mirroring {@code MapPanel}'s feature dot exactly.
     *
     * <p>In LibGDX Y-up coords, the top of a tile whose bottom-left corner
     * is at {@code (screenX, screenY)} is at {@code screenY + TILE_SIZE}.
     * The dot centre is inset from that corner by {@code DOT_RADIUS + DOT_GAP}.
     *
     * @param sr      ShapeRenderer in Filled mode
     * @param screenX world-pixel X of tile's left edge
     * @param screenY world-pixel Y of tile's bottom edge (Y-up)
     * @param type    tile type — DUNGEON gets purple, everything else gets gold
     */
    private static void drawDot(ShapeRenderer sr,
                                float screenX, float screenY,
                                TileType type) {
        float cx = screenX + TILE_SIZE - DOT_RADIUS - DOT_GAP;
        float cy = screenY + TILE_SIZE - DOT_RADIUS - DOT_GAP;

        sr.setColor(C_SHADOW);
        sr.circle(cx + 0.5f, cy - 0.5f, DOT_RADIUS + 0.5f, DOT_SEGS);

        sr.setColor(type == TileType.DUNGEON ? C_DUNGEON : C_TOWN);
        sr.circle(cx, cy, DOT_RADIUS, DOT_SEGS);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int     getMapWidth()  { return mapWidth;  }
    public int     getMapHeight() { return mapHeight; }
    public boolean hasTileSet()   { return tileSet != null && tileSet.hasTextures(); }

    public void dispose() {}
}