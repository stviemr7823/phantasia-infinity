package com.phantasia.core.world;

public class ScriptedEncounter {
    private final String monsterName;
    private final int count;
    private final boolean repeatable;
    private boolean triggered;

    public ScriptedEncounter(String monsterName, int count, boolean repeatable) {
        this.monsterName = monsterName;
        this.count       = count;
        this.repeatable  = repeatable;
        this.triggered   = false;
    }
    /**
     * Attempts to trigger this encounter.
     * Returns true if the encounter should fire (first time, or repeatable).
     * Returns false if it has already been triggered and is non-repeatable.
     * Marks the encounter as triggered on a successful fire.
     */
    public boolean trigger() {
        if (triggered && !repeatable) return false;
        triggered = true;
        return true;
    }

    public boolean isExhausted() {
        return triggered && !repeatable;
    }

    // --- Getters ---
    public String getMonsterName() { return monsterName; }
    public int getCount() { return count; }
    public boolean isRepeatable() { return repeatable; }
    public boolean isTriggered() { return triggered; }

    // ... original trigger() logic ...
}