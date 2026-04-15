// phantasia-core/src/main/java/com/phantasia/core/logic/RoundResult.java
package com.phantasia.core.logic;

import java.util.List;

/**
 * The complete record of one combat round.
 *
 * CombatManager resolves every action from every combatant —
 * party and monsters both — before returning this. The renderer
 * receives the full picture and presents it however it likes:
 * scrolling text, sequential animations, a dramatic replay.
 *
 * The combat state after the round is embedded here so the
 * renderer never needs to query core mid-presentation.
 */
public record RoundResult(
        int              roundNumber,
        List<CombatEvent> events,
        CombatOutcome    outcome
) {
    public boolean isCombatOver() {
        return outcome != CombatOutcome.ONGOING;
    }

    public boolean isVictory() {
        return outcome == CombatOutcome.VICTORY;
    }

    public boolean isDefeat() {
        return outcome == CombatOutcome.DEFEAT;
    }

    public boolean isEscape() {
        return outcome == CombatOutcome.ESCAPED;
    }
}