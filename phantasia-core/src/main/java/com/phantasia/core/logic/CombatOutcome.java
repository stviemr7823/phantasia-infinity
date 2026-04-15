// phantasia-core/src/main/java/com/phantasia/core/logic/CombatOutcome.java
package com.phantasia.core.logic;

/**
 * The state of the conflict at the end of a round.
 */
public enum CombatOutcome {
    ONGOING,   // Both sides still have living combatants
    VICTORY,   // All monsters defeated
    DEFEAT,    // All party members dead — party wipe
    ESCAPED    // Party successfully fled
}