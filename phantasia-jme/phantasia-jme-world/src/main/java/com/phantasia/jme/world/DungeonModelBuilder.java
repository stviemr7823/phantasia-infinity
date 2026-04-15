// phantasia-jme/phantasia-jme-world/src/main/java/com/phantasia/jme/world/DungeonModelBuilder.java
package com.phantasia.jme.world;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.phantasia.core.world.WorldFeature;

/**
 * Builds procedural stone archway geometry for dungeon features.
 *
 * All dungeons share the same archway silhouette — two pillars, a lintel,
 * a base plinth, and a keystone — so players learn to recognise dungeon
 * entrances at a glance. Colors shift slightly between dungeon IDs to
 * distinguish Frostpeak Cavern (cold blue-grey) from Sunken Vault (dark
 * mossy stone).
 *
 * All geometry uses Lighting.j3md so it responds correctly to the
 * DayNightCycle directional and ambient lights.
 *
 * DUNGEON COLORS:
 *   id=1  Frostpeak Cavern — cold blue-grey stone
 *   id=2  Sunken Vault     — dark mossy stone
 *   id=N  (future)         — falls back to dark grey
 *
 * USAGE:
 *   Node marker = DungeonModelBuilder.build(feature, TILE_SIZE, assetManager);
 *   marker.setLocalTranslation(wx, wy, wz);
 *   featureNode.attachChild(marker);
 */
public final class DungeonModelBuilder {

    private DungeonModelBuilder() {}

    // -------------------------------------------------------------------------
    // Stone color palette
    // -------------------------------------------------------------------------

    private static final ColorRGBA[] DUNGEON_COLORS = {
            new ColorRGBA(0.30f, 0.32f, 0.38f, 1f),  // id=0 — placeholder, unused
            new ColorRGBA(0.28f, 0.30f, 0.40f, 1f),  // id=1 Frostpeak — cold blue-grey
            new ColorRGBA(0.22f, 0.26f, 0.22f, 1f),  // id=2 Sunken Vault — dark mossy
    };

    private static final ColorRGBA FALLBACK_COLOR =
            new ColorRGBA(0.25f, 0.25f, 0.28f, 1f);

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    public static Node build(WorldFeature feature, float tileSize,
                              AssetManager assetManager) {
        Node root = new Node("Dungeon_" + feature.getId());
        float t = tileSize;

        ColorRGBA color = (feature.getId() >= 0 && feature.getId() < DUNGEON_COLORS.length)
                ? DUNGEON_COLORS[feature.getId()] : FALLBACK_COLOR;

        // Darker accent for details — lintel, plinth, keystone
        ColorRGBA dark = color.mult(0.65f);
        dark.a = 1f;

        // Left pillar
        addBox(root, "PillarL", -t*0.22f, 0f, 0f,
                t*0.09f, t*0.50f, t*0.09f, color, assetManager);

        // Right pillar
        addBox(root, "PillarR", +t*0.22f, 0f, 0f,
                t*0.09f, t*0.50f, t*0.09f, color, assetManager);

        // Lintel — spans the top of the two pillars
        addBox(root, "Lintel", 0f, t*0.52f, 0f,
                t*0.31f, t*0.07f, t*0.09f, dark, assetManager);

        // Base plinth — wide low slab the pillars stand on
        addBox(root, "Plinth", 0f, 0f, 0f,
                t*0.35f, t*0.04f, t*0.14f, dark, assetManager);

        // Keystone — dark block at the arch apex above the lintel
        addBox(root, "Keystone", 0f, t*0.60f, 0f,
                t*0.07f, t*0.07f, t*0.07f, dark, assetManager);

        return root;
    }

    // -------------------------------------------------------------------------
    // Box helper
    // -------------------------------------------------------------------------

    /**
     * Adds a Box geometry to the node.
     * cx/cz are centre X and Z offsets in local space.
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
