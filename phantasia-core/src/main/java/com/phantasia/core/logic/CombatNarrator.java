// phantasia-core/src/main/java/com/phantasia/core/logic/CombatNarrator.java
package com.phantasia.core.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts CombatEvents into the terse text blurbs of the original
 * Phantasie III battle feed.
 *
 * The narrator is purely a presentation layer — it reads events and
 * produces strings. It holds no game state and modifies nothing.
 * Every renderer (console, LibGDX text feed, future UI) calls this
 * and displays the strings however it likes.
 *
 * Output style matches the original game:
 *   "Bilbo slashes"
 *   "Bilbo hits!  24 HP"
 *   "Bilbo misses"
 *   "Bilbo hits!  99 HP  *CRITICAL*"
 *   "Great Moth dies"
 *   "Great Moth hits!"
 *   "Bode takes 99 damage"
 *   "Bode dies"
 *   "Bonzo casts Healing I"
 *   "Korg is healed for 11 HP"
 *   "Orc falls asleep"
 *   "Bonzo wakes up"
 *   "Bilbo is parrying"
 *   "Bilbo flees!"
 *   "Bilbo is held by sleep"
 */
public class CombatNarrator {

    // ------------------------------------------------------------------
    // Encounter opening
    // ------------------------------------------------------------------

    /**
     * The announcement shown before the tactical view opens.
     * e.g. "An Encounter, the monsters surprise you!"
     */
    public String announceEncounter(EncounterCondition condition) {
        return condition.announcement();
    }

    // ------------------------------------------------------------------
    // Round narrative
    // ------------------------------------------------------------------

    /**
     * Converts a full RoundResult into an ordered list of text lines,
     * one line per event. Feed these to your text display in sequence.
     */
    public List<String> narrateRound(RoundResult result) {
        List<String> lines = new ArrayList<>();

        for (CombatEvent event : result.events()) {
            String line = narrateEvent(event);
            if (line != null) lines.add(line);
        }

        // Append end-of-combat line if combat is over
        if (result.isCombatOver()) {
            lines.add(narrateOutcome(result.outcome()));
        }

        return lines;
    }

    /**
     * Narrates a single event. Returns null for events that
     * produce no visible text (e.g. RoundHeader).
     */
    public String narrateEvent(CombatEvent event) {
        return switch (event) {

            case CombatEvent.RoundHeader h ->
                    null;   // Round headers are silent in the text feed

            case CombatEvent.Hit h -> {
                // "Bilbo slashes" is emitted separately by the action line.
                // The hit line reports the result:
                //   "Bilbo hits!  24 HP"
                //   "Bilbo hits!  99 HP  *CRITICAL*"
                String base = h.attackerName() + " hits!  " + h.damage() + " HP";
                yield h.wasCritical() ? base + "  *CRITICAL*" : base;
            }

            case CombatEvent.Miss m ->
                    m.attackerName() + " misses";

            case CombatEvent.Death d ->
                    d.entityName() + " dies";

            case CombatEvent.SpellCast s -> {
                if (!s.succeeded()) {
                    yield s.casterName() + " — spell fails";
                }
                // Cast announcement: "Bonzo casts Healing I"
                // The effect line (heal/damage) is implied by the magnitude
                // and rendered as a follow-on line.
                int mag = s.magnitude();
                if (mag < 0) {
                    // Negative magnitude = heal
                    yield s.targetName() + " is healed for " + Math.abs(mag) + " HP";
                } else if (mag > 0) {
                    yield s.targetName() + " takes " + mag + " damage";
                } else {
                    // Status spells (sleep, awaken, buff) — effect described
                    // by the accompanying StatusChange event
                    yield s.casterName() + " casts " + s.spellName();
                }
            }

            case CombatEvent.StatusChange sc -> {
                yield switch (sc.condition()) {
                    case "Asleep"   -> sc.applied()
                            ? sc.entityName() + " falls asleep"
                            : sc.entityName() + " wakes up";
                    case "Parrying" -> sc.applied()
                            ? sc.entityName() + " is parrying"
                            : null;   // clearing parry is silent
                    case "No Magic Power" ->
                            sc.entityName() + " has no magic power";
                    default -> sc.applied()
                            ? sc.entityName() + " — " + sc.condition().toLowerCase()
                            : null;
                };
            }

            case CombatEvent.FleeAttempt f ->
                    f.succeeded()
                            ? f.entityName() + " flees!"
                            : f.entityName() + " can't escape!";
        };
    }

    /**
     * Returns the action declaration line for an attacker — the line
     * that precedes the hit/miss result.
     *
     * e.g. "Bilbo slashes" / "Bilbo attacks" / "Bilbo thrusts"
     *
     * Called by the renderer before it processes the Hit/Miss events
     * for that actor's turn. The action name comes from the Action enum
     * stored on the entity at time of resolution.
     */
    public String announceAction(String actorName, String actionName) {
        String verb = switch (actionName.toUpperCase()) {
            case "SLASH"     -> "slashes";
            case "THRUST"    -> "thrusts";
            case "LUNGE"     -> "lunges";
            case "AIM_BLOW"  -> "aims a blow";
            case "CAST"      -> "casts";
            case "PARRY"     -> "parries";
            case "RUN"       -> "attempts to flee";
            default          -> "attacks";   // ATTACK and fallback
        };
        return actorName + " " + verb;
    }

    // ------------------------------------------------------------------
    // End of combat
    // ------------------------------------------------------------------

    public String narrateOutcome(CombatOutcome outcome) {
        return switch (outcome) {
            case VICTORY -> "The battle is won!";
            case DEFEAT  -> "The party has been defeated...";
            case ESCAPED -> "The party escapes!";
            case ONGOING -> null;  // shouldn't be called while ongoing
        };
    }
}