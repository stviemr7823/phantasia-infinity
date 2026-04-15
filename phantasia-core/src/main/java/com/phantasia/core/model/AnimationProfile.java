// phantasia-core/src/main/java/com/phantasia/core/model/AnimationProfile.java
package com.phantasia.core.model;

/**
 * Animation and pacing metadata for interactive entities.
 *
 * Carried by {@link NpcDefinition} and
 * {@link com.phantasia.core.world.FeatureRecord} so that frontends can
 * render "pacing AI" — living entities that wander, idle, and react
 * rather than standing as static icons.
 *
 * <p>The string fields (idleAnimation, walkAnimation, talkAnimation)
 * are renderer-agnostic keys. Each frontend maps them to its own
 * asset system:
 * <ul>
 *   <li>j2d: sprite-sheet frame ranges</li>
 *   <li>JME: skeleton animation clip names</li>
 *   <li>libGDX: TextureAtlas animation keys</li>
 * </ul>
 *
 * <p>A {@code null} AnimationProfile on an entity means it is fully
 * static — the renderer draws a single idle frame and never moves it.</p>
 *
 * @param idleAnimation  animation key played while standing still (e.g. "idle_south")
 * @param walkAnimation  animation key played while wandering (e.g. "walk_south")
 * @param talkAnimation  animation key played during dialogue (e.g. "talk"), nullable
 * @param wanderRadius   maximum wander distance from home position, in tiles (0 = stationary)
 * @param paceSpeed      movement speed while wandering, in tiles per second
 * @param facesPlayer    if true, the entity turns toward the player on approach
 */
public record AnimationProfile(
        String  idleAnimation,
        String  walkAnimation,
        String  talkAnimation,
        float   wanderRadius,
        float   paceSpeed,
        boolean facesPlayer
) {

    /**
     * A fully static profile — no wandering, no special animations.
     * Frontends render a single idle frame.
     */
    public static final AnimationProfile STATIC =
            new AnimationProfile("idle_south", "idle_south", null, 0f, 0f, false);

    /**
     * A typical NPC that wanders a small area and faces the player.
     */
    public static AnimationProfile wanderer(float radius, float speed) {
        return new AnimationProfile(
                "idle_south", "walk_south", "talk",
                radius, speed, true
        );
    }

    /**
     * Returns true if this entity has any wandering behaviour.
     */
    public boolean isStationary() {
        return wanderRadius <= 0f;
    }
}
