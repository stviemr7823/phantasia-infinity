// phantasia-core/src/main/java/com/phantasia/core/logic/ActiveEntity.java
package com.phantasia.core.logic;

import com.phantasia.core.model.AnimationProfile;
import com.phantasia.core.model.NpcDefinition;
import com.phantasia.core.world.FloatPosition;

import java.util.Random;

/**
 * The mutable runtime state of a live NPC on a map.
 *
 * <p>{@link NpcDefinition} is immutable authored data. {@code ActiveEntity}
 * is the in-game instance of that NPC — it owns everything that changes
 * at runtime: float position, pacing AI state, current animation key.</p>
 *
 * <h3>Design contract</h3>
 * <ul>
 *   <li>One {@code ActiveEntity} per live NPC placement.</li>
 *   <li>The entity does not own its {@link NpcDefinition} — it holds a
 *       reference. Multiple placements of the same NPC type share one
 *       definition (e.g., a "Guard" NPC placed in every town).</li>
 *   <li>The frontend calls {@link #update(float, Random)} every tick and
 *       reads {@link #getPosition()} for rendering. Core provides the
 *       decision; the frontend owns the lifecycle.</li>
 * </ul>
 *
 * <h3>Pacing behaviour</h3>
 * If the entity's {@link AnimationProfile} has {@code wanderRadius > 0},
 * {@link PacingController} will move the entity within that radius around
 * its home position. A {@code wanderRadius} of 0 ({@link AnimationProfile#STATIC})
 * means the entity never moves — it just idles at home.
 *
 * <h3>Frontends</h3>
 * <ul>
 *   <li>j2d: render a dot/sprite at {@link #getPosition()} each frame.</li>
 *   <li>JME: drive skeleton animation via {@link #getCurrentAnimKey()}.</li>
 *   <li>libGDX: drive TextureAtlas animation the same way.</li>
 * </ul>
 */
public class ActiveEntity {

    // -------------------------------------------------------------------------
    // Authored data (immutable reference)
    // -------------------------------------------------------------------------

    private final NpcDefinition  definition;

    // -------------------------------------------------------------------------
    // Mutable runtime state
    // -------------------------------------------------------------------------

    private FloatPosition  position;
    private final FloatPosition homePosition;     // wander centre, never changes
    private FloatPosition  targetPosition;        // current wander target (nullable)
    private PacingState    pacingState;
    private float          idleTimer;
    private String         currentAnimKey;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a new active entity at the given home position.
     *
     * @param definition  the authored NPC data
     * @param homePos     the tile-centre position in float-world coordinates
     */
    public ActiveEntity(NpcDefinition definition, FloatPosition homePos) {
        this.definition     = definition;
        this.position       = homePos;
        this.homePosition   = homePos;
        this.targetPosition = null;
        this.pacingState    = PacingState.IDLE;
        this.idleTimer      = 0f;
        this.currentAnimKey = definition.getAnimationProfile().idleAnimation();
    }

    // -------------------------------------------------------------------------
    // Update — called by ActiveEntityManager each tick
    // -------------------------------------------------------------------------

    /**
     * Advances the pacing AI by one frame.
     *
     * @param dt   delta time in seconds (typically 1/60)
     * @param rng  shared random source from the manager
     */
    public void update(float dt, Random rng) {
        PacingController.Result result = PacingController.update(
                pacingState,
                position,
                homePosition,
                targetPosition,
                idleTimer,
                definition.getAnimationProfile(),
                dt,
                rng
        );

        pacingState     = result.newState();
        position        = result.newPosition();
        targetPosition  = result.newTarget();
        idleTimer       = result.newIdleTimer();
        currentAnimKey  = result.animationKey();
    }

    // -------------------------------------------------------------------------
    // Accessors — read by frontends each frame
    // -------------------------------------------------------------------------

    public NpcDefinition  getDefinition()      { return definition;      }
    public FloatPosition  getPosition()        { return position;        }
    public FloatPosition  getHomePosition()    { return homePosition;    }
    public PacingState    getPacingState()     { return pacingState;     }
    public String         getCurrentAnimKey()  { return currentAnimKey;  }

    /**
     * Returns true if this entity is currently moving toward a target.
     */
    public boolean isMoving() {
        return pacingState == PacingState.WANDERING
                || pacingState == PacingState.RETURNING;
    }

    @Override
    public String toString() {
        return "ActiveEntity[" + definition.getName()
                + " @" + position + " " + pacingState + "]";
    }
}
