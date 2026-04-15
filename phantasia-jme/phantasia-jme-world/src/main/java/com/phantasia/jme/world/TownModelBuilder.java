// phantasia-jme/phantasia-jme-world/src/main/java/com/phantasia/jme/world/TownModelBuilder.java
package com.phantasia.jme.world;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.phantasia.core.world.WorldFeature;

/**
 * Builds procedural keep geometry for town features.
 *
 * Each town gets a distinct silhouette seeded from its ID so the same
 * town always looks the same across sessions. All geometry uses
 * Lighting.j3md with material colors so it responds correctly to the
 * DayNightCycle directional and ambient lights.
 *
 * TOWN SILHOUETTES:
 *   id=0  Pendragon  — tall central keep + four corner turrets (capital)
 *   id=1  Scandor    — two equal towers side by side
 *   id=2  Loftwood   — wide low hall with short end towers (forest town)
 *   id=3  Ironhaven  — squat fortified block with thick walls
 *   id=N  (future)   — single generic tower, scales by id
 *
 * USAGE:
 *   Node marker = TownModelBuilder.build(feature, TILE_SIZE, assetManager);
 *   marker.setLocalTranslation(wx, wy, wz);
 *   featureNode.attachChild(marker);
 */
public final class TownModelBuilder {

    private TownModelBuilder() {}

    // -------------------------------------------------------------------------
    // Stone color palette — one per town, warm to cool
    // -------------------------------------------------------------------------

    private static final ColorRGBA[] TOWN_COLORS = {
            new ColorRGBA(0.88f, 0.82f, 0.68f, 1f),  // Pendragon — warm sandstone
            new ColorRGBA(0.75f, 0.72f, 0.65f, 1f),  // Scandor   — grey limestone
            new ColorRGBA(0.70f, 0.78f, 0.60f, 1f),  // Loftwood  — mossy green-grey
            new ColorRGBA(0.60f, 0.58f, 0.55f, 1f),  // Ironhaven — dark iron-grey
    };

    private static final ColorRGBA FALLBACK_COLOR =
            new ColorRGBA(0.80f, 0.78f, 0.70f, 1f);

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    public static Node build(WorldFeature feature, float tileSize,
                             AssetManager assetManager) {
        Node root = new Node("Town_" + feature.getId());
        ColorRGBA color = (feature.getId() >= 0 && feature.getId() < TOWN_COLORS.length)
                ? TOWN_COLORS[feature.getId()] : FALLBACK_COLOR;

        switch (feature.getId()) {
            case 0 -> buildPendragon(root, tileSize, color, assetManager);
            case 1 -> buildScandor(root, tileSize, color, assetManager);
            case 2 -> buildLoftwood(root, tileSize, color, assetManager);
            case 3 -> buildIronhaven(root, tileSize, color, assetManager);
            default -> buildGenericTower(root, tileSize, color, assetManager);
        }

        return root;
    }

    // -------------------------------------------------------------------------
    // id=0  Pendragon — tall central keep + four corner turrets
    // -------------------------------------------------------------------------

    private static void buildPendragon(Node root, float t, ColorRGBA c,
                                        AssetManager am) {
        // Central keep — tallest structure, befitting the capital
        addBox(root, "Keep",     0,       0, 0,  t*0.28f, t*0.55f, t*0.28f, c, am);
        // Battlement top — slightly wider flat cap
        addBox(root, "Top",      0, t*0.58f, 0,  t*0.32f, t*0.06f, t*0.32f, c, am);
        // Four corner turrets
        float o = t * 0.30f;
        float th = t * 0.38f;
        addBox(root, "TurretNE", +o, 0, +o,  t*0.10f, th, t*0.10f, c, am);
        addBox(root, "TurretNW", -o, 0, +o,  t*0.10f, th, t*0.10f, c, am);
        addBox(root, "TurretSE", +o, 0, -o,  t*0.10f, th, t*0.10f, c, am);
        addBox(root, "TurretSW", -o, 0, -o,  t*0.10f, th, t*0.10f, c, am);
    }

    // -------------------------------------------------------------------------
    // id=1  Scandor — two equal towers side by side
    // -------------------------------------------------------------------------

    private static void buildScandor(Node root, float t, ColorRGBA c,
                                      AssetManager am) {
        float offset = t * 0.22f;
        float h      = t * 0.45f;
        addBox(root, "TowerA", -offset, 0, 0,  t*0.18f, h, t*0.18f, c, am);
        addBox(root, "TowerB", +offset, 0, 0,  t*0.18f, h, t*0.18f, c, am);
        // Connecting wall between towers
        addBox(root, "Wall",   0, 0, 0,  offset, t*0.20f, t*0.10f, c, am);
        // Darker caps on each tower
        ColorRGBA dark = c.mult(0.75f); dark.a = 1f;
        addBox(root, "CapA", -offset, h, 0,  t*0.20f, t*0.05f, t*0.20f, dark, am);
        addBox(root, "CapB", +offset, h, 0,  t*0.20f, t*0.05f, t*0.20f, dark, am);
    }

    // -------------------------------------------------------------------------
    // id=2  Loftwood — wide low hall with short end towers
    // -------------------------------------------------------------------------

    private static void buildLoftwood(Node root, float t, ColorRGBA c,
                                       AssetManager am) {
        // Long low great hall
        addBox(root, "Hall",   0,       0, 0,  t*0.38f, t*0.22f, t*0.20f, c, am);
        // Slightly darker pitched roof suggestion on top
        ColorRGBA roof = c.mult(0.80f); roof.a = 1f;
        addBox(root, "Roof",   0, t*0.24f, 0,  t*0.36f, t*0.12f, t*0.18f, roof, am);
        // Short end towers
        float ex = t * 0.40f;
        addBox(root, "TowerE", +ex, 0, 0,  t*0.14f, t*0.32f, t*0.14f, c, am);
        addBox(root, "TowerW", -ex, 0, 0,  t*0.14f, t*0.32f, t*0.14f, c, am);
    }

    // -------------------------------------------------------------------------
    // id=3  Ironhaven — squat heavily fortified block
    // -------------------------------------------------------------------------

    private static void buildIronhaven(Node root, float t, ColorRGBA c,
                                        AssetManager am) {
        // Thick outer walls
        addBox(root, "Walls",  0,       0, 0,  t*0.38f, t*0.18f, t*0.38f, c, am);
        // Inner keep — taller and narrower, rising above the walls
        ColorRGBA inner = c.mult(0.85f); inner.a = 1f;
        addBox(root, "Keep",   0, t*0.20f, 0,  t*0.22f, t*0.30f, t*0.22f, inner, am);
        // Crenellation strip at the top
        ColorRGBA dark = c.mult(0.70f); dark.a = 1f;
        addBox(root, "Crens",  0, t*0.52f, 0,  t*0.24f, t*0.06f, t*0.24f, dark, am);
    }

    // -------------------------------------------------------------------------
    // Generic fallback — single tower
    // -------------------------------------------------------------------------

    private static void buildGenericTower(Node root, float t, ColorRGBA c,
                                           AssetManager am) {
        addBox(root, "Tower", 0, 0, 0,  t*0.20f, t*0.35f, t*0.20f, c, am);
    }

    // -------------------------------------------------------------------------
    // Box helper
    // -------------------------------------------------------------------------

    /**
     * Adds a Box geometry to the node.
     * cx/cz are the centre X and Z offsets in local space.
     * cy is the BASE Y — the box bottom sits at cy, top at cy + halfH*2.
     * halfX, halfH, halfZ are the half-extents passed to JME Box.
     */
    private static void addBox(Node parent, String name,
                                float cx, float cy, float cz,
                                float halfX, float halfH, float halfZ,
                                ColorRGBA color, AssetManager am) {
        Box mesh = new Box(halfX, halfH, halfZ);
        Geometry geom = new Geometry(name, mesh);

        Material mat = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Diffuse",  color);
        mat.setColor("Ambient",  color.mult(0.4f));
        mat.setColor("Specular", ColorRGBA.Black);
        mat.setFloat("Shininess", 0f);
        geom.setMaterial(mat);

        // Position: cy is base, so centre Y = cy + halfH
        geom.setLocalTranslation(cx, cy + halfH, cz);
        parent.attachChild(geom);
    }
}
