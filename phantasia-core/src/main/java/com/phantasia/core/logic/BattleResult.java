// phantasia-core/src/main/java/com/phantasia/core/logic/BattleResult.java
package com.phantasia.core.logic;

/**
 * An immutable summary of a completed battle, bundling the outcome and loot
 * into a single object that any frontend can consume uniformly.
 *
 * CombatManager.conclude() returns this once isCombatOver() is true.
 * The JME layer (and any future frontend) should call conclude() once and
 * hand the BattleResult to the HUD — rather than constructing LootManager
 * themselves and juggling two separate overloads.
 *
 * USAGE:
 *   // After the last round:
 *   if (combatManager.isCombatOver()) {
 *       BattleResult result = combatManager.conclude(party, ledger);
 *       hud.showEndScreen(result);
 *   }
 *
 * @param outcome    VICTORY, DEFEAT, ESCAPED, or ONGOING (should not be ONGOING here)
 * @param lootResult The loot distributed to the party; null if outcome != VICTORY
 */
public record BattleResult(
        CombatOutcome          outcome,
        LootManager.LootResult lootResult
) {
    /** Convenience: true when the party won and loot was distributed. */
    public boolean hasLoot() {
        return lootResult != null && lootResult.hasLoot();
    }

    /** Convenience: true if any items overflowed (packs full). */
    public boolean hadOverflow() {
        return lootResult != null && lootResult.hadOverflow();
    }
}