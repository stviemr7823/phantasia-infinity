// phantasia-core/src/main/java/com/phantasia/core/world/InteractionType.java
package com.phantasia.core.world;

/**
 * How an interior tile responds to player contact.
 *
 * NONE         — no interaction. Decorative or structural.
 * BUMP_TRIGGER — player walks into the tile (blocked) and the interaction
 *                fires. Used for counters, altars, NPCs, locked doors.
 * STEP_TRIGGER — player walks onto the tile (passable) and the interaction
 *                fires. Used for doors, stairs, chests, traps, exits.
 */
public enum InteractionType {
    NONE,
    BUMP_TRIGGER,
    STEP_TRIGGER
}