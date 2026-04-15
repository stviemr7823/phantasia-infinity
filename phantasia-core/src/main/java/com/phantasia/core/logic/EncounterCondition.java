// phantasia-core/src/main/java/com/phantasia/core/logic/EncounterCondition.java
package com.phantasia.core.logic;

/**
 * The condition under which a battle begins.
 *
 * Drives the opening announcement text and any first-round penalties.
 * Mirrors the three states the original Phantasie III could announce:
 *
 *   "An Encounter..."
 *   "An Encounter, the monsters surprise you!"
 *   "An Encounter, the Party is Asleep."
 */
public enum EncounterCondition {

    /** Normal engagement — both sides aware. */
    NORMAL,

    /**
     * Monsters acted before the party could react.
     * Penalty: party members cannot act in round 1.
     */
    MONSTERS_SURPRISE,

    /**
     * The party was asleep when the encounter began.
     * Penalty: all party members start with asleep=true.
     */
    PARTY_ASLEEP;

    // ------------------------------------------------------------------
    // Opening announcement — faithful to the original text
    // ------------------------------------------------------------------

    /**
     * Returns the announcement string shown before the tactical view opens.
     * Matches the original Phantasie III wording exactly.
     */
    public String announcement() {
        return switch (this) {
            case NORMAL           -> "An Encounter...";
            case MONSTERS_SURPRISE -> "An Encounter, the monsters surprise you!";
            case PARTY_ASLEEP     -> "An Encounter, the Party is Asleep.";
        };
    }
}