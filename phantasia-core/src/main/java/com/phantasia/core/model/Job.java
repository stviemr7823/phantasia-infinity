package com.phantasia.core.model;

public enum Job {
    WIZARD(1),
    PRIEST(2),
    MONK(4),
    RANGER(8),
    FIGHTER(16),
    THIEF(32);

    private final int bitValue;
    Job(int bitValue) { this.bitValue = bitValue; }
    public int getBitValue() { return bitValue; }

    // Helper to find Job by value (useful for getCharClass)
    public static Job fromValue(int value) {
        for (Job j : Job.values()) {
            if (j.bitValue == value) return j;
        }
        return FIGHTER; // Default
    }
}