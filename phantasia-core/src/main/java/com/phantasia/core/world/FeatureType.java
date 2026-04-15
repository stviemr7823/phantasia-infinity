package com.phantasia.core.world;

public enum FeatureType {
    NONE, TOWN, DUNGEON, SIGNPOST, CHEST, EVENT_TRIGGER, NPC; // Added NPC [cite: 4323]

    public static FeatureType fromId(int id) {
        if (id < 0 || id >= values().length) return NONE;
        return values()[id];
    }
}