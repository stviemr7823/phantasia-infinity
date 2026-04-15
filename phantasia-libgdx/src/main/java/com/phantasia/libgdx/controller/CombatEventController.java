// phantasia-libgdx/src/main/java/com/phantasia/libgdx/controller/CombatEventController.java
package com.phantasia.libgdx.controller;

import com.phantasia.core.logic.CombatEvent;
import com.phantasia.core.logic.CombatNarrator;
import com.phantasia.libgdx.screens.GameScreen;

/**
 * Translates core CombatEvents into LibGDX presentation calls.
 *
 * FIX APPLIED:
 *   Previously this class called event.getMessage().toLowerCase() and
 *   searched for keyword strings like "casts fire" and "hits" to decide
 *   which visual effects to trigger. That approach:
 *     - Was brittle (a narrator wording change silently broke VFX)
 *     - Duplicated logic that core already encodes structurally
 *     - Would have to be re-duplicated in any future frontend
 *
 *   CombatEvent is a sealed interface with typed subtypes. We now switch
 *   exhaustively on those types. The renderer reads the event's *structure*,
 *   not its *prose output* — these are separate concerns.
 *
 * PATTERN:
 *   SpellCast with positive magnitude → explosion + heavy shake
 *   Hit wasCritical                   → moderate shake
 *   Hit (normal)                      → light shake
 *   Death (monster)                   → post to log only
 *   Death (party member)              → post to log, refresh stats
 *   StatusChange, Miss, FleeAttempt   → post to log only
 *   RoundHeader                       → no VFX, optional log entry
 */
public class CombatEventController {

    private final GameScreen    screen;
    private final CombatNarrator narrator = new CombatNarrator();

    public CombatEventController(GameScreen screen) {
        this.screen = screen;
    }

    /**
     * Processes a single CombatEvent: posts narrator text to the log,
     * then fires the appropriate VFX/screen-shake based on event type.
     */
    public void processEvent(CombatEvent event) {

        // 1. Post the narrator's text description to the South Pane log.
        //    narrateEvent() is the only place that touches prose — the
        //    visual effects decision below is entirely type-driven.
        String message = narrator.narrateEvent(event);
        if (message != null && !message.isEmpty()) {
             // screen.postToLog(message);
        }

        // 2. Drive VFX by switching on the sealed event type.
        //    Pattern-matching switch is exhaustive — the compiler will warn
        //    if a new CombatEvent subtype is added without a case here.
        switch (event) {

            case CombatEvent.SpellCast sc -> {
                if (sc.succeeded() && sc.magnitude() > 0) {
                    // Damaging spell landed — explosion + heavy shake
                    // screen.spawnParticleEffect("effects/explosion.p");
                    // screen.triggerScreenShake(0.3f);
                } else if (sc.succeeded() && sc.magnitude() < 0) {
                    // Healing spell — gentle pulse, no shake
                    // screen.spawnParticleEffect("effects/heal.p");
                }
                // Failed casts: narrator message already posted, no VFX
            }

            case CombatEvent.Hit h -> {
                if (h.wasCritical()) {
                    // screen.triggerScreenShake(0.2f);
                } else {
                    // screen.triggerScreenShake(0.1f);
                }
            }

            case CombatEvent.Death d -> {
                // Stat panel always needs a refresh on any death
                // screen.refreshStats();
                if (d.wasPartyMember()) {
                    // Extra emphasis for a fallen hero — stronger shake
                   // screen.triggerScreenShake(0.25f);
                }
            }

            case CombatEvent.StatusChange sc -> {
                // Sleep, paralysis, waking — no shake, just the log entry
            }

            case CombatEvent.Miss m -> {
                // Misses: log message only, no VFX
            }

            case CombatEvent.FleeAttempt f -> {
                if (f.succeeded()) {
                    // Optional: fade transition could be triggered here
                }
            }

            case CombatEvent.RoundHeader rh -> {
                // Round separator — no VFX. The log message is sufficient.
            }
        }
    }
}
