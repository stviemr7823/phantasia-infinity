package com.phantasia.core.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A schema-validated container for NPC state.
 * The engine never hardcodes specific property names. [cite: 120]
 */
public class PropertyBag {
    private final Map<String, Object> properties = new ConcurrentHashMap<>();

    public void set(String key, Object value) {
        // Validation logic against the loaded schema goes here [cite: 118]
        properties.put(key, value);
    }

    public Object get(String key) {
        return properties.get(key);
    }

    public Map<String, Object> getAll() {
        return Map.copyOf(properties);
    }
}