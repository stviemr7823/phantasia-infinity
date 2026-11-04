// phantasia-core/src/main/java/com/phantasia/core/logic/ActiveEntityManager.java
package com.phantasia.core.logic;

import com.phantasia.core.model.NpcDefinition;
import com.phantasia.core.world.FloatPosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Owns and updates all {@link ActiveEntity} instances on the current map.
 *
 * <p>The frontend creates one manager per map context (overworld, town
 * interior, dungeon floor) and calls {@link #update(float)} once per
 * game-loop tick. The manager drives every entity's pacing AI using the
 * stateless {@link PacingController}.</p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Create the manager for a map context.</li>
 *   <li>Call {@link #spawn(NpcDefinition, FloatPosition)} for each NPC
 *       that should be live on that map.</li>
 *   <li>Call {@link #update(float)} every game-loop tick.</li>
 *   <li>Call {@link #getAll()} each render frame to draw entities.</li>
 *   <li>Call {@link #clear()} when leaving the map.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * Not thread-safe — intended for single-threaded game-loop use only.
 */
public class ActiveEntityManager {

    // Shared RNG — one per manager so entities on the same map are
    // independently random but the manager itself is deterministic if seeded.
    private final Random rng;

    private final List<ActiveEntity> entities = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public ActiveEntityManager() {
        this.rng = new Random();
    }

    /** Seeded constructor — useful for deterministic testing. */
    public ActiveEntityManager(long seed) {
        this.rng = new Random(seed);
    }

    // -------------------------------------------------------------------------
    // Entity management
    // -------------------------------------------------------------------------

    /**
     * Spawns a new active entity at the given float-world home position.
     *
     * @param definition  the authored NPC data
     * @param homePos     tile-centre position in float-world coordinates
     * @return the newly created entity (for callers that need a direct reference)
     */
    public ActiveEntity spawn(NpcDefinition definition, FloatPosition homePos) {
        ActiveEntity entity = new ActiveEntity(definition, homePos);
        entities.add(entity);
        return entity;
    }

    /**
     * Removes all entities from this manager.
     * Call when the player leaves the current map context.
     */
    public void clear() {
        entities.clear();
    }

    public int size() {
        return entities.size();
    }

    // -------------------------------------------------------------------------
    // Update — one call per game-loop tick
    // -------------------------------------------------------------------------

    /**
     * Advances all entity pacing AIs by one frame.
     *
     * @param dt  delta time in seconds
     */
    public void update(float dt) {
        for (ActiveEntity entity : entities) {
            entity.update(dt, rng);
        }
    }

    // -------------------------------------------------------------------------
    // Read — for frontend rendering
    // -------------------------------------------------------------------------

    /**
     * Returns all live entities (unmodifiable view).
     * The frontend iterates this each render frame to draw entities.
     */
    public List<ActiveEntity> getAll() {
        return Collections.unmodifiableList(entities);
    }
}
