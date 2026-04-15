// phantasia-core/src/main/java/com/phantasia/core/logic/PacingState.java
package com.phantasia.core.logic;

/**
 * The three states of the NPC pacing AI state machine.
 *
 * <ul>
 *   <li>{@link #IDLE} — stand still for a randomised duration (2–5 s), play idle animation</li>
 *   <li>{@link #WANDERING} — walk toward a random point within wander radius</li>
 *   <li>{@link #RETURNING} — walk back toward home position (strayed beyond radius)</li>
 * </ul>
 *
 * Frontends own the mutable state per entity. Core's {@link PacingController}
 * provides the stateless decision function.
 */
public enum PacingState {
    IDLE,
    WANDERING,
    RETURNING
}