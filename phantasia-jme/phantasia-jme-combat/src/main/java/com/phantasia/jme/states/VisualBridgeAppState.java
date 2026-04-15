// phantasia-jme/src/main/java/com/phantasia/jme/states/VisualBridgeAppState.java
package com.phantasia.jme.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.phantasia.core.logic.*;
import com.phantasia.jme.combat.CombatAnimationController;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * The bridge between the core combat engine and the JME visual layer.
 * <p>
 * Responsibilities:
 * 1. Receive a RoundResult from CombatState, narrate it to the HUD log,
 * and enqueue all events for timed playback.
 * 2. Drain the event queue at a steady pace — one event per DELAY seconds,
 * but only after the previous event's animation has fully resolved.
 * 3. Delegate all 3D work to CombatAnimationController.
 * 4. Detect end-of-battle once the queue is empty and animations have settled,
 * then call CombatState.conclude() which fires GameEvent.CombatConcluded.
 * HUDState receives that event via the bus and shows the end screen.
 * <p>
 * END-OF-BATTLE FLOW:
 * Old: checkForEndOfBattle() → combatState.conclude() → hud.showEndScreen()
 * New: checkForEndOfBattle() → combatState.conclude()
 * ↓ fires GameEvent.CombatConcluded
 * HUDState.busSubscription → showEndScreen()
 * <p>
 * VisualBridgeAppState no longer imports or references HUDState directly.
 * This removes the last awkward direct dependency between the two states.
 * <p>
 * ANIMATION GATING:
 * After the minimum EVENT_DELAY, the bridge waits for the animation to
 * fully settle before dequeuing the next event. A grace period (ANIM_GRACE)
 * acts as a safety valve so a stalled animation never blocks the queue
 * indefinitely.
 */
public class VisualBridgeAppState extends BaseAppState
{

    // -------------------------------------------------------------------------
    // Timing constants
    // -------------------------------------------------------------------------

    /**
     * Minimum seconds between events being dequeued.
     */
    private static final float EVENT_DELAY = 1.2f;

    /**
     * After EVENT_DELAY expires, wait up to this many additional seconds for
     * animations to settle before force-dequeuing the next event.
     */
    private static final float ANIM_GRACE = 0.8f;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final CombatNarrator narrator = new CombatNarrator();
    private final Queue<CombatEvent> eventQueue = new LinkedList<>();
    private final CombatAnimationController animController = new CombatAnimationController();

    private float eventTimer = 0;
    private float graceTimer = 0;
    private boolean delayMet = false;

    /**
     * Guards conclude() so it fires exactly once per battle, regardless of how
     * many frames the queue-empty + combat-over condition holds true.
     * Reset by clearSpatials() at the start of each new battle.
     */
    private boolean endFired = false;

    // -------------------------------------------------------------------------
    // AppState lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void initialize(Application app)
    {
    }

    @Override
    protected void onEnable()
    {
    }

    @Override
    protected void onDisable()
    {
    }

    @Override
    protected void cleanup(Application app)
    {
        eventQueue.clear();
        animController.clear();
        endFired = false;
    }

    // -------------------------------------------------------------------------
    // Scene registration
    // -------------------------------------------------------------------------

    /**
     * Registers a spatial with the animation controller.
     * Called by CombatState.spawnPlaceholder() once per combatant.
     */
    public void registerSpatial(String entityName, com.jme3.scene.Spatial spatial)
    {
        animController.register(entityName, spatial);
    }

    /**
     * Clears all registered spatials and resets the end-of-battle guard.
     * Must be called at the start of every new battle before spatials are
     * re-registered — ensures a stale endFired from the previous fight
     * doesn't suppress the end screen for the next one.
     */
    public void clearSpatials()
    {
        animController.clear();
        endFired = false;
    }

    // -------------------------------------------------------------------------
    // Round processing
    // -------------------------------------------------------------------------

    /**
     * Called by CombatState.executeRound() with the full round result.
     * Narrates all events to the HUD log and enqueues them for visual playback.
     */
    public void processRoundResults(RoundResult result)
    {
        List<String> lines = narrator.narrateRound(result);
        for (String line : lines)
        {
            if (line != null)
            {
                GameEventBus.get().fire(new GameEvent.CombatLogEntry(line));
                System.out.println("NARRATOR: " + line);
            }
        }

        eventQueue.addAll(result.events());

        // Reset timers for this new batch
        eventTimer = 0;
        graceTimer = 0;
        delayMet = false;
    }

    // -------------------------------------------------------------------------
    // Update loop
    // -------------------------------------------------------------------------

    @Override
    public void update(float tpf)
    {
        animController.update(tpf);

        if (eventQueue.isEmpty())
        {
            checkForEndOfBattle();
            return;
        }

        // Phase 1 — minimum inter-event delay
        if (!delayMet)
        {
            eventTimer += tpf;
            if (eventTimer >= EVENT_DELAY)
            {
                delayMet = true;
                graceTimer = 0;
            }
            return;
        }

        // Phase 2 — wait for animation, but cap with grace period
        if (animController.isAnimating())
        {
            graceTimer += tpf;
            if (graceTimer < ANIM_GRACE) return;
            System.out.println("[VisualBridge] Grace period expired — forcing next event.");
        }

        CombatEvent next = eventQueue.poll();
        visualizeEvent(next);

        eventTimer = 0;
        graceTimer = 0;
        delayMet = false;
    }

    // -------------------------------------------------------------------------
    // End-of-battle detection
    // -------------------------------------------------------------------------

    /**
     * Called every frame while the event queue is empty.
     * <p>
     * Fires exactly once per battle when:
     * (a) the event queue has fully drained,
     * (b) all animations have settled (no death-sinking spatials still moving),
     * (c) CombatState confirms combat is over.
     * <p>
     * Calls CombatState.conclude(), which:
     * 1. Distributes loot into the session ledger.
     * 2. Fires GameEvent.CombatConcluded onto the bus.
     * 3. HUDState receives the event and shows the end screen.
     * <p>
     * VisualBridgeAppState does NOT reference HUDState here — the bus
     * decouples the conclusion notification from the presentation.
     */
    private void checkForEndOfBattle()
    {
        if (endFired) return;

        CombatState combatState = getState(CombatState.class);
        if (combatState == null || !combatState.isCombatOver()) return;

        // Wait for death-sink animations to complete before concluding
        if (animController.isAnimating()) return;

        endFired = true;
        combatState.conclude();
        // CombatState.conclude() fires GameEvent.CombatConcluded → HUDState shows end screen
    }

    // -------------------------------------------------------------------------
    // Event visualization dispatch
    // -------------------------------------------------------------------------

    private void visualizeEvent(CombatEvent event)
    {
        switch (event)
        {
            case CombatEvent.Hit hit -> visualizeHit(hit);
            case CombatEvent.Miss miss -> visualizeMiss(miss);
            case CombatEvent.SpellCast spell -> visualizeSpell(spell);
            case CombatEvent.Death death -> visualizeDeath(death);
            case CombatEvent.StatusChange sc -> visualizeStatus(sc);
            case CombatEvent.FleeAttempt f -> visualizeFlee(f);
            case CombatEvent.RoundHeader h ->
            {
            } // log only — no 3D effect
        }
    }

    private void visualizeHit(CombatEvent.Hit hit)
    {
        FloatingTextState text = getState(FloatingTextState.class);
        animController.playHit(
                hit.attackerName(), hit.targetName(),
                hit.damage(), hit.wasCritical(), text);
    }

    private void visualizeMiss(CombatEvent.Miss miss)
    {
        FloatingTextState text = getState(FloatingTextState.class);
        animController.playMiss(miss.attackerName(), miss.targetName(), text);
    }

    private void visualizeSpell(CombatEvent.SpellCast spell)
    {
        if (!spell.succeeded()) return;
        FloatingTextState text = getState(FloatingTextState.class);
        animController.playSpell(
                spell.casterName(), spell.targetName(),
                spell.spellName(), spell.magnitude(), text);
    }

    private void visualizeDeath(CombatEvent.Death death)
    {
        animController.playDeath(death.entityName());
    }

    private void visualizeStatus(CombatEvent.StatusChange sc)
    {
        FloatingTextState text = getState(FloatingTextState.class);
        if (text == null) return;

        CombatState combatState = getState(CombatState.class);
        if (combatState == null) return;

        var spatial = combatState.getRegistry().getSpatial(sc.entityName());
        if (spatial == null) return;

        String label = sc.applied() ? sc.condition() : sc.condition() + " cleared";

        com.jme3.math.ColorRGBA color = switch (sc.condition())
        {
            case "Asleep" -> new com.jme3.math.ColorRGBA(0.4f, 0.6f, 1.0f, 1f);
            case "Parrying" -> new com.jme3.math.ColorRGBA(0.9f, 0.9f, 0.3f, 1f);
            default -> com.jme3.math.ColorRGBA.White;
        };

        text.spawnText(label, spatial.getLocalTranslation().add(0, 2.2f, 0), color);
    }

    private void visualizeFlee(CombatEvent.FleeAttempt f)
    {
        FloatingTextState text = getState(FloatingTextState.class);
        if (text == null) return;

        CombatState combatState = getState(CombatState.class);
        if (combatState == null) return;

        var spatial = combatState.getRegistry().getSpatial(f.entityName());
        if (spatial == null) return;

        String label = f.succeeded() ? "Fled!" : "Can't escape!";
        com.jme3.math.ColorRGBA color = f.succeeded()
                ? com.jme3.math.ColorRGBA.Yellow
                : com.jme3.math.ColorRGBA.Red;

        text.spawnText(label, spatial.getLocalTranslation().add(0, 2.2f, 0), color);
    }
}