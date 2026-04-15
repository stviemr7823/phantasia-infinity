// phantasia-core/src/main/java/com/phantasia/core/world/EncounterTimer.java
package com.phantasia.core.world;

import java.util.Random;

/**
 * Overland encounter pacing system.
 *
 * After each encounter, the timer resets to a random value in a
 * defined range. Each pace decrements the timer by the terrain's
 * drain rate. When the timer reaches zero, an encounter triggers
 * and the timer resets.
 *
 * This guarantees breathing room between fights while still making
 * dangerous terrain feel threatening — swamp drains faster than road.
 */
public class EncounterTimer {

    private static final Random rand = new Random();

    // Steps between encounters — range gives natural variance
    private static final int TIMER_MIN = 8;
    private static final int TIMER_MAX = 20;

    private int stepsRemaining;

    public EncounterTimer() {
        reset();
    }

    /**
     * Called once per pace. Returns true if an encounter triggers.
     * @param terrain the tile the party just stepped onto
     */
    public boolean step(TileType terrain) {
        // Towns and dungeons never trigger overland random encounters
        if (terrain == TileType.TOWN || terrain == TileType.DUNGEON) {
            return false;
        }

        stepsRemaining -= terrain.timerDrain;

        if (stepsRemaining <= 0) {
            reset();
            return true;
        }
        return false;
    }

    /** Resets after an encounter — or can be called on rest, town visit, etc. */
    public void reset() {
        stepsRemaining = TIMER_MIN + rand.nextInt(TIMER_MAX - TIMER_MIN + 1);
    }

    public int getStepsRemaining() { return stepsRemaining; }
}