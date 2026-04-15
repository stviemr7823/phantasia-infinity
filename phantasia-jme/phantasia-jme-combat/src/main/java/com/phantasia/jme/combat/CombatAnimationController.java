package com.phantasia.jme.combat;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;

import com.phantasia.jme.states.*;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Drives timed 3D animations for combat events.
 *
 * Each living spatial gets its own {@link SpatialAnimator} — a small
 * state machine that knows where the entity rests, where it lunges,
 * and what to do when it dies.
 *
 * DESIGN:
 *   - No JME Controls or scene-graph callbacks. All state lives here,
 *     updated by a single {@code update(tpf)} call from VisualBridgeAppState.
 *   - The controller never touches the event queue — that stays in
 *     VisualBridgeAppState. The controller only receives imperative
 *     commands: "play a hit on X hitting Y", "play death on Z".
 *   - Animations are non-blocking. Commanding a new animation on a
 *     spatial that is mid-lunge cancels the current animation and
 *     snaps to the rest position before starting the new one. This
 *     keeps the queue from stalling if rounds are fast.
 *
 * USAGE:
 *   // In VisualBridgeAppState.initialize():
 *   animController = new CombatAnimationController();
 *
 *   // After CombatState.setupScene() builds spatials:
 *   animController.register("Galahad", galahaSpatial);
 *   animController.register("Orc A",   orcSpatial);
 *
 *   // In VisualBridgeAppState.update(tpf):
 *   animController.update(tpf);
 *
 *   // When visualizing a Hit event:
 *   animController.playHit(hit.attackerName(), hit.targetName(),
 *                          hit.damage(), hit.wasCritical(), floatingTextState);
 *
 *   // When visualizing a Death event:
 *   animController.playDeath(death.entityName());
 *
 *   // On combat end / new battle:
 *   animController.clear();
 */
public class CombatAnimationController
{

    // -------------------------------------------------------------------------
    // Tuning constants
    // -------------------------------------------------------------------------

    /**
     * Fraction of the gap between attacker and target to lunge toward.
     */
    private static final float LUNGE_FRACTION = 0.40f;

    /**
     * World units per second for the lunge movement.
     */
    private static final float LUNGE_SPEED = 8.0f;

    /**
     * World units per second for the return movement.
     */
    private static final float RETURN_SPEED = 5.0f;

    /**
     * Amplitude of the target's hit-shake in world units.
     */
    private static final float SHAKE_AMPLITUDE = 0.15f;

    /**
     * Duration of the target's hit-shake in seconds.
     */
    private static final float SHAKE_DURATION = 0.25f;

    /**
     * How fast the dead spatial sinks below the floor (world units/sec).
     */
    private static final float DEATH_SINK_SPEED = 1.2f;

    /**
     * How far below origin the spatial sinks before being removed.
     */
    private static final float DEATH_SINK_DEPTH = 2.5f;

    // -------------------------------------------------------------------------
    // Animator map
    // -------------------------------------------------------------------------

    private final Map<String, SpatialAnimator> animators = new HashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Registers a spatial under the given entity name.
     * Must be called after CombatState.setupScene() assigns spatials.
     */
    public void register(String entityName, Spatial spatial)
    {
        animators.put(entityName,
                new SpatialAnimator(spatial, spatial.getLocalTranslation().clone()));
    }

    /**
     * Removes all registered animators. Call between battles or on cleanup.
     */
    public void clear()
    {
        animators.clear();
    }

    // -------------------------------------------------------------------------
    // Animation commands
    // -------------------------------------------------------------------------

    /**
     * Plays a full hit sequence:
     * 1. Attacker lunges toward target.
     * 2. Target shakes on impact.
     * 3. Attacker returns to rest position.
     * 4. Damage number floats up from target (via FloatingTextState).
     * <p>
     * Safe to call even if either spatial is not registered — degrades gracefully.
     */
    public void playHit(String attackerName, String targetName,
                        int damage, boolean wasCritical,
                        FloatingTextState floatingText)
    {

        SpatialAnimator attAnim = animators.get(attackerName);
        SpatialAnimator tgtAnim = animators.get(targetName);

        if (attAnim == null || tgtAnim == null) return;

        // Cancel any current animation on both spatials first
        attAnim.snapToRest();
        tgtAnim.snapToRest();

        // Compute the lunge target: 40% of the way from attacker to target
        Vector3f lungeTarget = attAnim.restPosition.clone()
                .interpolateLocal(tgtAnim.restPosition, LUNGE_FRACTION);

        // Queue: attacker lunges, target shakes, attacker returns
        attAnim.playLunge(lungeTarget, LUNGE_SPEED, RETURN_SPEED);
        tgtAnim.playShake(SHAKE_AMPLITUDE, SHAKE_DURATION);

        // Spawn the floating damage number above the target
        if (floatingText != null)
        {
            String label = wasCritical
                    ? damage + " *CRIT*"
                    : String.valueOf(damage);
            ColorRGBA color = wasCritical
                    ? new ColorRGBA(1f, 0.6f, 0f, 1f)  // orange for crits
                    : ColorRGBA.Red;
            Vector3f textPos = tgtAnim.restPosition.add(0, 2.2f, 0);
            floatingText.spawnText(label, textPos, color);
        }
    }

    /**
     * Plays a miss — attacker makes a short lunge that stops short,
     * and a grey "Miss" label floats up from the target position.
     */
    public void playMiss(String attackerName, String targetName,
                         FloatingTextState floatingText)
    {

        SpatialAnimator attAnim = animators.get(attackerName);
        SpatialAnimator tgtAnim = animators.get(targetName);

        if (attAnim == null) return;
        attAnim.snapToRest();

        // Lunge only 20% — the attack doesn't connect
        Vector3f lungeTarget = tgtAnim != null
                ? attAnim.restPosition.clone()
                  .interpolateLocal(tgtAnim.restPosition, 0.20f)
                : attAnim.restPosition.clone().add(0.3f, 0, 0);

        attAnim.playLunge(lungeTarget, LUNGE_SPEED * 0.8f, RETURN_SPEED);

        if (floatingText != null && tgtAnim != null)
        {
            floatingText.spawnText("Miss",
                    tgtAnim.restPosition.add(0, 2.2f, 0),
                    new ColorRGBA(0.7f, 0.7f, 0.7f, 1f));
        }
    }

    /**
     * Plays a spell cast — caster pulses bright white briefly,
     * and a colored label floats above the target.
     */
    public void playSpell(String casterName, String targetName,
                          String spellName, int magnitude,
                          FloatingTextState floatingText)
    {

        SpatialAnimator casterAnim = animators.get(casterName);
        SpatialAnimator targetAnim = animators.get(targetName);

        if (casterAnim != null)
        {
            casterAnim.snapToRest();
            casterAnim.playPulse();
        }

        if (floatingText != null && targetAnim != null)
        {
            String label;
            ColorRGBA color;
            if (magnitude < 0)
            {
                // Heal — show positive number in green
                label = "+" + Math.abs(magnitude) + " HP";
                color = new ColorRGBA(0.2f, 0.9f, 0.3f, 1f);
            } else if (magnitude > 0)
            {
                label = String.valueOf(magnitude);
                color = new ColorRGBA(0.4f, 0.4f, 1f, 1f);  // blue for magic
            } else
            {
                label = spellName;
                color = new ColorRGBA(0.8f, 0.4f, 1f, 1f);  // purple for status
            }
            floatingText.spawnText(label,
                    targetAnim.restPosition.add(0, 2.2f, 0), color);
        }
    }

    /**
     * Plays a death animation — the spatial sinks below the floor over
     * DEATH_SINK_DEPTH world units, then removes itself from its parent.
     */
    public void playDeath(String entityName)
    {
        SpatialAnimator anim = animators.get(entityName);
        if (anim == null) return;
        anim.snapToRest();
        anim.playDeath(DEATH_SINK_SPEED, DEATH_SINK_DEPTH);
        // Remove from map after the animation completes — handled in update()
    }

    // -------------------------------------------------------------------------
    // Update — call every frame from VisualBridgeAppState.update(tpf)
    // -------------------------------------------------------------------------

    /**
     * Advances all active animations by one frame.
     * Dead spatials that have finished sinking are removed from the registry.
     */
    public void update(float tpf)
    {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, SpatialAnimator> entry : animators.entrySet())
        {
            SpatialAnimator anim = entry.getValue();
            anim.update(tpf);
            if (anim.isFinishedDying())
            {
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(animators::remove);
    }

    /**
     * Returns true if any animator is currently mid-animation.
     * VisualBridgeAppState can use this to gate the next event dequeue —
     * ensuring the lunge resolves before the next event starts.
     */
    public boolean isAnimating()
    {
        for (SpatialAnimator anim : animators.values())
        {
            if (anim.isActive()) return true;
        }
        return false;
    }

    // =========================================================================
    // SpatialAnimator — inner state machine for one spatial
    // =========================================================================

    private static class SpatialAnimator
    {

        // Animation states
        private enum State
        {IDLE, LUNGE, RETURN, SHAKE, PULSE, DYING}

        final Spatial spatial;
        final Vector3f restPosition;   // Where the entity stands at rest

        private State state = State.IDLE;
        private float timer = 0;

        // Lunge/return
        private Vector3f lungeTarget;
        private float lungeSpeed;
        private float returnSpeed;

        // Shake
        private float shakeAmplitude;
        private float shakeDuration;
        private float shakePhase = 0;

        // Pulse (spell cast flash)
        private static final float PULSE_DURATION = 0.18f;
        private ColorRGBA originalColor;

        // Death sink
        private float sinkSpeed;
        private float sinkDepth;
        private boolean finishedDying = false;

        SpatialAnimator(Spatial spatial, Vector3f restPosition)
        {
            this.spatial = spatial;
            this.restPosition = restPosition.clone();
        }

        // ------------------------------------------------------------------
        // Commands
        // ------------------------------------------------------------------

        void snapToRest()
        {
            spatial.setLocalTranslation(restPosition.clone());
            restoreColor();
            state = State.IDLE;
            timer = 0;
        }

        void playLunge(Vector3f target, float lungeSpd, float returnSpd)
        {
            this.lungeTarget = target.clone();
            this.lungeSpeed = lungeSpd;
            this.returnSpeed = returnSpd;
            state = State.LUNGE;
            timer = 0;
        }

        void playShake(float amplitude, float duration)
        {
            this.shakeAmplitude = amplitude;
            this.shakeDuration = duration;
            this.shakePhase = 0;
            state = State.SHAKE;
            timer = 0;
        }

        void playPulse()
        {
            // Tint the spatial's first geometry bright white briefly
            originalColor = getGeomColor();
            setGeomColor(ColorRGBA.White);
            state = State.PULSE;
            timer = 0;
        }

        void playDeath(float sinkSpd, float depth)
        {
            this.sinkSpeed = sinkSpd;
            this.sinkDepth = depth;
            state = State.DYING;
            timer = 0;
        }

        boolean isActive()
        {
            return state != State.IDLE;
        }

        boolean isFinishedDying()
        {
            return finishedDying;
        }

        // ------------------------------------------------------------------
        // Update — advances the current animation by tpf seconds
        // ------------------------------------------------------------------

        void update(float tpf)
        {
            switch (state)
            {

                case IDLE ->
                {
                }

                case LUNGE ->
                {
                    // Move toward lunge target at lunge speed
                    Vector3f current = spatial.getLocalTranslation();
                    Vector3f dir = lungeTarget.subtract(current);
                    float dist = dir.length();

                    if (dist < 0.01f)
                    {
                        // Reached lunge target — begin return
                        spatial.setLocalTranslation(lungeTarget.clone());
                        state = State.RETURN;
                        timer = 0;
                    } else
                    {
                        float step = Math.min(lungeSpeed * tpf, dist);
                        spatial.setLocalTranslation(
                                current.add(dir.normalizeLocal().multLocal(step)));
                    }
                }

                case RETURN ->
                {
                    // Move back toward rest position at return speed
                    Vector3f current = spatial.getLocalTranslation();
                    Vector3f dir = restPosition.subtract(current);
                    float dist = dir.length();

                    if (dist < 0.01f)
                    {
                        snapToRest();  // clears state → IDLE
                    } else
                    {
                        float step = Math.min(returnSpeed * tpf, dist);
                        spatial.setLocalTranslation(
                                current.add(dir.normalizeLocal().multLocal(step)));
                    }
                }

                case SHAKE ->
                {
                    timer += tpf;
                    shakePhase += tpf;

                    if (timer >= shakeDuration)
                    {
                        snapToRest();
                    } else
                    {
                        // Sine-wave horizontal shake around rest position
                        float offset = (float) Math.sin(shakePhase * 40f)
                                * shakeAmplitude
                                * (1f - timer / shakeDuration); // fade out
                        spatial.setLocalTranslation(
                                restPosition.add(offset, 0, 0));
                    }
                }

                case PULSE ->
                {
                    timer += tpf;
                    if (timer >= PULSE_DURATION)
                    {
                        restoreColor();
                        state = State.IDLE;
                        timer = 0;
                    }
                    // Color was already set in playPulse() — nothing to interpolate
                    // at this fidelity; a simple on/off flash reads clearly in combat
                }

                case DYING ->
                {
                    // Sink straight down
                    Vector3f pos = spatial.getLocalTranslation();
                    pos.y -= sinkSpeed * tpf;
                    spatial.setLocalTranslation(pos);

                    float sunken = restPosition.y - pos.y;
                    if (sunken >= sinkDepth)
                    {
                        spatial.removeFromParent();
                        finishedDying = true;
                        state = State.IDLE;
                    }
                }
            }
        }

        // ------------------------------------------------------------------
        // Color helpers — operate on the first Geometry child, if present
        // ------------------------------------------------------------------

        private Geometry firstGeometry()
        {
            if (spatial instanceof Geometry g) return g;
            if (spatial instanceof com.jme3.scene.Node n)
            {
                for (Spatial child : n.getChildren())
                {
                    if (child instanceof Geometry g) return g;
                }
            }
            return null;
        }

        private ColorRGBA getGeomColor()
        {
            Geometry g = firstGeometry();
            if (g == null) return ColorRGBA.White.clone();
            Object c = g.getMaterial().getParam("Color");
            return c instanceof com.jme3.math.ColorRGBA rgba
                    ? rgba.clone()
                    : ColorRGBA.White.clone();
        }

        private void setGeomColor(ColorRGBA color)
        {
            Geometry g = firstGeometry();
            if (g != null) g.getMaterial().setColor("Color", color.clone());
        }

        private void restoreColor()
        {
            if (originalColor != null)
            {
                setGeomColor(originalColor);
                originalColor = null;
            }
        }
    }
}