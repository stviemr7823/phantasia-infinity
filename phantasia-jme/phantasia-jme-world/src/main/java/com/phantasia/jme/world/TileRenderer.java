// phantasia-jme/src/main/java/com/phantasia/jme/world/TileRenderer.java
package com.phantasia.jme.world;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.phantasia.core.world.FeatureType;
import com.phantasia.core.world.Tile;
import com.phantasia.core.world.TileType;
import com.phantasia.core.world.WorldMap;
import com.phantasia.core.world.WorldPosition;

/**
 * Builds and owns the JME scene graph for the world map.
 *
 * Each tile is rendered as a flat Quad lying on the XZ plane.
 * Terrain type drives the tile color. Features (towns, dungeons)
 * get a small raised marker quad on top of the terrain tile.
 *
 * Coordinate mapping:
 *   World grid  (x, y)  →  JME world  (x * TILE_SIZE, 0, -y * TILE_SIZE)
 *
 * Y is negated for Z because in JME's right-handed coordinate system,
 * -Z is "forward/north" — matching the WorldPosition.north() convention
 * of increasing Y mapping to moving visually upward/forward on screen.
 *
 * USAGE:
 *   TileRenderer renderer = new TileRenderer(assetManager, TILE_SIZE);
 *   renderer.buildScene(worldMap);
 *   parentNode.attachChild(renderer.getSceneNode());
 *
 *   // Later, to highlight the player's tile:
 *   renderer.setPlayerTileHighlight(playerPosition);
 */
public class TileRenderer {

    // -------------------------------------------------------------------------
    // Tile color palette — flat-shaded, matches the World Map Editor colors
    // -------------------------------------------------------------------------

    private static final ColorRGBA COLOR_OCEAN    = new ColorRGBA(0.10f, 0.25f, 0.55f, 1f);
    private static final ColorRGBA COLOR_ROAD     = new ColorRGBA(0.72f, 0.65f, 0.45f, 1f);
    private static final ColorRGBA COLOR_PLAINS   = new ColorRGBA(0.45f, 0.68f, 0.30f, 1f);
    private static final ColorRGBA COLOR_FOREST   = new ColorRGBA(0.15f, 0.42f, 0.18f, 1f);
    private static final ColorRGBA COLOR_MOUNTAIN = new ColorRGBA(0.55f, 0.52f, 0.48f, 1f);
    private static final ColorRGBA COLOR_SWAMP    = new ColorRGBA(0.30f, 0.38f, 0.22f, 1f);
    private static final ColorRGBA COLOR_TOWN     = new ColorRGBA(0.85f, 0.82f, 0.60f, 1f);
    private static final ColorRGBA COLOR_DUNGEON  = new ColorRGBA(0.22f, 0.18f, 0.28f, 1f);

    // Feature marker colors
    private static final ColorRGBA COLOR_TOWN_MARKER    = new ColorRGBA(1.0f,  0.90f, 0.20f, 1f);
    private static final ColorRGBA COLOR_DUNGEON_MARKER = new ColorRGBA(0.70f, 0.10f, 0.80f, 1f);

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final AssetManager assetManager;
    private final float        tileSize;

    /** Root node for all tile geometry — attach this to your scene. */
    private final Node sceneNode = new Node("WorldTiles");

    // Separate child node so feature markers can be toggled independently
    private final Node featureNode = new Node("WorldFeatures");

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * @param assetManager  the JME asset manager (from SimpleApplication)
     * @param tileSize      world units per tile — use 2.0f for a comfortable
     *                      camera distance, or 1.0f for tight maps
     */
    public TileRenderer(AssetManager assetManager, float tileSize) {
        this.assetManager = assetManager;
        this.tileSize     = tileSize;
        sceneNode.attachChild(featureNode);
    }

    // -------------------------------------------------------------------------
    // Scene construction
    // -------------------------------------------------------------------------

    /**
     * Reads the full WorldMap and builds a quad for every tile.
     * Safe to call multiple times (clears the previous scene first).
     *
     * This is intentionally simple — no chunking, no LOD, no culling.
     * For the maps sizes used in Phantasie III (typically ≤64×64) this
     * is well within JME's batch budget with static geometry.
     */
    public void buildScene(WorldMap worldMap) {
        sceneNode.detachAllChildren();
        featureNode.detachAllChildren();
        sceneNode.attachChild(featureNode);  // re-attach after clear

        for (int x = 0; x < worldMap.getWidth(); x++) {
            for (int y = 0; y < worldMap.getHeight(); y++) {
                Tile tile = worldMap.getTile(x, y);
                buildTerrainQuad(tile.getType(), x, y);

                if (tile.hasFeature()) {
                    buildFeatureMarker(tile.getFeature().getType(), x, y);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Tile quad builder
    // -------------------------------------------------------------------------

    private void buildTerrainQuad(TileType type, int x, int y) {
        // A Quad is a flat XY plane. We rotate it to lie flat on XZ.
        Quad mesh = new Quad(tileSize, tileSize);
        Geometry geom = new Geometry("Tile_" + x + "_" + y, mesh);

        Material mat = flatColorMaterial(tileColor(type));
        geom.setMaterial(mat);

        // Rotate the quad to lie flat (XZ plane)
        geom.rotate(-com.jme3.math.FastMath.HALF_PI, 0, 0);

        // Position: center of this tile. Quads are placed from their bottom-left
        // corner, so we offset by half a tile to center on grid coordinates.
        geom.setLocalTranslation(
                x * tileSize,
                0f,
                -y * tileSize
        );

        sceneNode.attachChild(geom);
    }

    // -------------------------------------------------------------------------
    // Feature marker builder
    // -------------------------------------------------------------------------

    /**
     * Spawns a small raised box above a feature tile.
     * Towns get a gold cube; dungeons get a purple cube.
     * The marker is smaller than the tile so the terrain color
     * is still visible around it.
     */
    private void buildFeatureMarker(FeatureType featureType, int x, int y) {
        float markerSize = tileSize * 0.35f;
        Box mesh = new Box(markerSize, markerSize, markerSize);
        Geometry geom = new Geometry("Feature_" + x + "_" + y, mesh);

        ColorRGBA color = (featureType == FeatureType.TOWN)
                ? COLOR_TOWN_MARKER
                : COLOR_DUNGEON_MARKER;

        geom.setMaterial(flatColorMaterial(color));

        // Center the marker on the tile, raised above it
        geom.setLocalTranslation(
                x * tileSize + tileSize * 0.5f,
                markerSize,
                -y * tileSize - tileSize * 0.5f
        );

        featureNode.attachChild(geom);
    }

    // -------------------------------------------------------------------------
    // Player highlight
    // -------------------------------------------------------------------------

    /**
     * Places (or moves) a bright highlight quad directly above the tile
     * at the given position. Call this every time the player moves.
     *
     * The highlight is a semi-transparent white quad, slightly above ground
     * to avoid z-fighting with the terrain quad beneath it.
     */
    public void setPlayerTileHighlight(WorldPosition pos) {
        // Remove old highlight if present
        com.jme3.scene.Spatial old = sceneNode.getChild("PlayerHighlight");
        if (old != null) old.removeFromParent();

        Quad mesh = new Quad(tileSize * 0.85f, tileSize * 0.85f);
        Geometry geom = new Geometry("PlayerHighlight", mesh);

        Material mat = flatColorMaterial(new ColorRGBA(1f, 1f, 1f, 0.35f));
        // Enable alpha blend for the semi-transparent highlight
        mat.getAdditionalRenderState()
                .setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        geom.setMaterial(mat);
        geom.rotate(-com.jme3.math.FastMath.HALF_PI, 0, 0);

        float offset = tileSize * 0.075f;  // centering offset
        geom.setLocalTranslation(
                pos.x() * tileSize + offset,
                0.02f,   // just above ground to prevent z-fight
                -pos.y() * tileSize - offset
        );

        sceneNode.attachChild(geom);
    }

    // -------------------------------------------------------------------------
    // Camera framing helper
    // -------------------------------------------------------------------------

    /**
     * Returns the world-space center of a tile, elevated by the given height.
     * Use this to position the camera above the player:
     *
     *   Vector3f camTarget = renderer.tileCenterWorld(playerPos, 12f);
     *   camera.setLocation(camTarget);
     *   camera.lookAt(renderer.tileCenterWorld(playerPos, 0f), Vector3f.UNIT_Y);
     */
    public Vector3f tileCenterWorld(WorldPosition pos, float elevationY) {
        return new Vector3f(
                pos.x() * tileSize + tileSize * 0.5f,
                elevationY,
                -pos.y() * tileSize - tileSize * 0.5f
        );
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** The root scene node — attach this to your application's rootNode. */
    public Node getSceneNode() { return sceneNode; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Material flatColorMaterial(ColorRGBA color) {
        Material mat = new Material(assetManager,
                "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        return mat;
    }

    private static ColorRGBA tileColor(TileType type) {
        return switch (type) {
            case OCEAN    -> COLOR_OCEAN;
            case ROAD     -> COLOR_ROAD;
            case PLAINS   -> COLOR_PLAINS;
            case FOREST   -> COLOR_FOREST;
            case MOUNTAIN -> COLOR_MOUNTAIN;
            case SWAMP    -> COLOR_SWAMP;
            case TOWN     -> COLOR_TOWN;
            case DUNGEON  -> COLOR_DUNGEON;
        };
    }
}