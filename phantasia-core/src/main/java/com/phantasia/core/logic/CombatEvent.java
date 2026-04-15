// phantasia-core/src/main/java/com/phantasia/core/logic/CombatEvent.java
package com.phantasia.core.logic;

import java.util.List;

/**
 * An immutable record of a single thing that happened during a round.
 *
 * CombatManager produces these in initiative order as it resolves
 * each combatant's action. The full sequence becomes the RoundResult
 * event log — a faithful replay of exactly what happened and why.
 *
 * Sealed so the renderer can exhaustively switch on every possible
 * event type without a default fallback hiding missing cases.
 */
public sealed interface CombatEvent permits
        CombatEvent.Hit,
        CombatEvent.Miss,
        CombatEvent.Death,
        CombatEvent.SpellCast,
        CombatEvent.StatusChange,
        CombatEvent.FleeAttempt,
        CombatEvent.RoundHeader
{
    // ------------------------------------------------------------------
    // A successful attack that dealt damage
    // ------------------------------------------------------------------
    record Hit(
            String  attackerName,
            String  targetName,
            int     damage,
            String  limbStruck,     // "Left Arm", "Torso", etc.
            boolean wasCritical
    ) implements CombatEvent {}

    // ------------------------------------------------------------------
    // An attack that found no purchase
    // ------------------------------------------------------------------
    record Miss(
            String attackerName,
            String targetName
    ) implements CombatEvent {}

    // ------------------------------------------------------------------
    // A combatant's HP reached zero this round
    // ------------------------------------------------------------------
    record Death(
            String  entityName,
            boolean wasPartyMember  // true = party loss, false = monster slain
    ) implements CombatEvent {}

    // ------------------------------------------------------------------
    // A spell was cast — heal, damage, or status
    // ------------------------------------------------------------------
    record SpellCast(
            String  casterName,
            String  spellName,
            String  targetName,
            int     magnitude,      // Damage dealt or HP restored (negative = heal)
            boolean succeeded
    ) implements CombatEvent {}

    // ------------------------------------------------------------------
    // Sleep, paralysis, waking up, parrying — any condition change
    // ------------------------------------------------------------------
    record StatusChange(
            String entityName,
            String condition,       // "Asleep", "Parrying", "Awoken", "Paralyzed"
            boolean applied         // true = condition gained, false = condition cleared
    ) implements CombatEvent {}

    // ------------------------------------------------------------------
    // A party member attempted to run
    // ------------------------------------------------------------------
    record FleeAttempt(
            String  entityName,
            boolean succeeded
    ) implements CombatEvent {}

    // ------------------------------------------------------------------
    // Marks the start of a round in the event log — useful for
    // renderers that present rounds sequentially
    // ------------------------------------------------------------------
    record RoundHeader(
            int          roundNumber,
            List<String> initiativeOrder
    ) implements CombatEvent {
        public RoundHeader {
            initiativeOrder = List.copyOf(initiativeOrder);
        }
    }
}