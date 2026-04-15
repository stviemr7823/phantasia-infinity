package com.phantasia.jme.states;

import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import java.util.HashMap;
import java.util.Map;

public class CombatSceneRegistry {
    private final Map<String, Spatial> entityMap = new HashMap<>();

    public void register(String name, Spatial spatial) {
        entityMap.put(name, spatial);
    }

    // This is the missing link from your screenshot!
    public Spatial getSpatial(String name) {
        return entityMap.get(name);
    }

    public Vector3f getPosition(String name) {
        Spatial s = entityMap.get(name);
        return (s != null) ? s.getWorldTranslation().clone() : Vector3f.ZERO;
    }

    public void clear() {
        entityMap.clear();
    }
}