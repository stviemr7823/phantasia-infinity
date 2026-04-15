// phantasia-core/src/main/java/com/phantasia/core/logic/PacingController.java
package com.phantasia.core.logic;

import com.phantasia.core.model.AnimationProfile;
import com.phantasia.core.world.FloatPosition;

import java.util.Random;

/**
 * Stateless utility that computes the next pacing action for a wandering entity.
 *
 * <p>Each entity with a non-zero {@code wanderRadius} in its
 * {@link AnimationProfile} runs a simple three-state machine (see
 * {@link PacingState}). The frontend owns the mutable per-entity state;
 * this class provides the pure decision function.</p>
 *
 * <h3>Usage from a frontend game loop:</h3>
 * <pre>{@code
 *   PacingController.Result r = PacingController.update(
 *       entity.pacingState, entity.position, entity.homePosition,
 *       entity.targetPosition, entity.idleTimer,
 *       entity.animationProfile, deltaSeconds, rng
 *   );
 *   entity.pacingState    = r.newState();
 *   entity.position       = r.newPosition();
 *   entity.targetPosition = r.newTarget();
 *   entity.idleTimer      = r.newIdleTimer();
 *   entity.currentAnim    = r.animationKey();
 * }</pre>
 *
 * <p>This class is thread-safe — it has no mutable state.</p>
 */
public final class PacingController {

    private PacingController() {} // utility class

    // -------------------------------------------------------------------------
    // Idle duration bounds (seconds)
    // -------------------------------------------------------------------------

    private static final float IDLE_MIN = 2.0f;
    private static final float IDLE_MAX = 5.0f;

    /** Distance threshold for "arrived at target". */
    private static final float ARRIVAL_THRESHOLD = 0.15f;

    // -------------------------------------------------------------------------
    // Result record
    // -------------------------------------------------------------------------

    /**
     * The complete output of a pacing update. The frontend applies every
     * field back to its mutable entity state.
     *
     * @param newState      the state after this tick
     * @param newPosition   the entity's updated float position
     * @param newTarget     the current wander/return target (may be unchanged)
     * @param newIdleTimer  remaining idle time (only meaningful in IDLE state)
     * @param animationKey  the animation key the renderer should play this frame
     */
    public record Result(
            PacingState   newState,
            FloatPosition newPosition,
            FloatPosition newTarget,
            float         newIdleTimer,
            String        animationKey
    ) {}

    // -------------------------------------------------------------------------
    // The decision function
    // -------------------------------------------------------------------------

    /**
     * Computes one tick of the pacing AI.
     *
     * @param state       current pacing state
     * @param position    current float position of the entity
     * @param home        the entity's home position (center of wander area)
     * @param target      current movement target (nullable if IDLE)
     * @param idleTimer   seconds remaining in IDLE countdown
     * @param profile     the entity's animation profile
     * @param dt          delta time this frame, in seconds
     * @param rng         random source for wander target selection and idle duration
     * @return a Result with all updated fields
     */
    public static Result update(PacingState state, FloatPosition position,
                                FloatPosition home, FloatPosition target,
                                float idleTimer, AnimationProfile profile,
                                float dt, Random rng) {

        if (profile == null || profile.isStationary()) {
            // Fully static — always idle at current position
            return new Result(PacingState.IDLE, position, null, 0f,
                    profile != null ? profile.idleAnimation() : "idle_south");
        }

        return switch (state) {
            case IDLE      -> tickIdle(position, home, target, idleTimer, profile, dt, rng);
            case WANDERING -> tickWander(position, home, target, profile, dt, rng);
            case RETURNING -> tickReturn(position, home, target, profile, dt, rng);
        };
    }

    // -------------------------------------------------------------------------
    // Per-state tick logic
    // -------------------------------------------------------------------------

    private static Result tickIdle(FloatPosition pos, FloatPosition home,
                                   FloatPosition target, float idleTimer,
                                   AnimationProfile profile, float dt, Random rng) {
        float remaining = idleTimer - dt;
        if (remaining > 0) {
            // Still idling
            return new Result(PacingState.IDLE, pos, target, remaining,
                    profile.idleAnimation());
        }
        // Idle timer expired → pick a wander target and start walking
        FloatPosition wanderTarget = randomPointInRadius(home, profile.wanderRadius(), rng);
        return new Result(PacingState.WANDERING, pos, wanderTarget, 0f,
                profile.walkAnimation());
    }

    private static Result tickWander(FloatPosition pos, FloatPosition home,
                                     FloatPosition target, AnimationProfile profile,
                                     float dt, Random rng) {
        // Check if we've strayed beyond the wander radius
        if (pos.distanceTo(home) > profile.wanderRadius() * 1.1f) {
            // Over-extended — switch to returning
            return new Result(PacingState.RETURNING, pos, home, 0f,
                    profile.walkAnimation());
        }

        // Move toward target
        FloatPosition newPos = moveToward(pos, target, profile.paceSpeed(), dt);

        if (newPos.distanceTo(target) < ARRIVAL_THRESHOLD) {
            // Arrived — go idle for a random duration
            float idleDuration = IDLE_MIN + rng.nextFloat() * (IDLE_MAX - IDLE_MIN);
            return new Result(PacingState.IDLE, newPos, null, idleDuration,
                    profile.idleAnimation());
        }

        return new Result(PacingState.WANDERING, newPos, target, 0f,
                profile.walkAnimation());
    }

    private static Result tickReturn(FloatPosition pos, FloatPosition home,
                                     FloatPosition target, AnimationProfile profile,
                                     float dt, Random rng) {
        FloatPosition newPos = moveToward(pos, home, profile.paceSpeed(), dt);

        if (newPos.distanceTo(home) < ARRIVAL_THRESHOLD) {
            // Back home — go idle
            float idleDuration = IDLE_MIN + rng.nextFloat() * (IDLE_MAX - IDLE_MIN);
            return new Result(PacingState.IDLE, newPos, null, idleDuration,
                    profile.idleAnimation());
        }

        return new Result(PacingState.RETURNING, newPos, home, 0f,
                profile.walkAnimation());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Moves {@code from} toward {@code to} by up to {@code speed * dt} tiles.
     */
    private static FloatPosition moveToward(FloatPosition from, FloatPosition to,
                                            float speed, float dt) {
        float dx = to.x() - from.x();
        float dy = to.y() - from.y();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist < ARRIVAL_THRESHOLD) return to;

        float step = speed * dt;
        if (step >= dist) return to;

        float ratio = step / dist;
        return new FloatPosition(
                from.x() + dx * ratio,
                from.y() + dy * ratio
        );
    }

    /**
     * Picks a random point within {@code radius} tiles of {@code center}.
     */
    private static FloatPosition randomPointInRadius(FloatPosition center,
                                                     float radius, Random rng) {
        // Uniform distribution inside a circle
        float angle = rng.nextFloat() * 2f * (float) Math.PI;
        float r     = radius * (float) Math.sqrt(rng.nextFloat());
        return new FloatPosition(
                center.x() + r * (float) Math.cos(angle),
                center.y() + r * (float) Math.sin(angle)
        );
    }
}