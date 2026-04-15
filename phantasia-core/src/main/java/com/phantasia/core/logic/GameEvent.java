// phantasia-core/src/main/java/com/phantasia/core/logic/GameEvent.java
package com.phantasia.core.logic;

import com.phantasia.core.model.PlayerCharacter;

import java.util.List;

/**
 * Observable game events fired by core logic and consumed by frontends.
 *
 * SEALED: every permitted subtype is listed in the permits clause.
 * All exhaustive switch statements (GameManagerState, HUDState) must be
 * updated whenever a new record is added here.
 *
 * REVISION NOTES:
 *   EncounterTriggered — fired by WorldState when a random or scripted
 *     encounter begins.
 *   TownEntered — fired by WorldState when the party steps onto a town tile.
 *   ReturnToWorldRequested — fired by HUDState after victory/escape.
 *   NpcMoodChanged — fired after ML interaction pipeline updates an NPC.
 *   DarkRippleReceived — fired when Nikademus emits a world adjustment.
 */
public sealed interface GameEvent permits
        GameEvent.PlayerLeveledUp,
        GameEvent.StatIncreased,
        GameEvent.PartyGoldChanged,
        GameEvent.BankBalanceChanged,
        GameEvent.CombatStarted,
        GameEvent.CombatConcluded,
        GameEvent.PartyMoved,
        GameEvent.QuestFlagSet,
        GameEvent.PartyJudged,
        GameEvent.ReloadSaveRequested,
        GameEvent.CombatLogEntry,
        GameEvent.PartyDisplaySetup,
        GameEvent.EncounterTriggered,
        GameEvent.TownEntered,
        GameEvent.ReturnToWorldRequested
{
    // -------------------------------------------------------------------------
    // Character progression
    // -------------------------------------------------------------------------

    record PlayerLeveledUp(
            PlayerCharacter character,
            int             newLevel
    ) implements GameEvent {}

    record StatIncreased(
            PlayerCharacter character,
            String          statName,
            int             oldValue,
            int             newValue
    ) implements GameEvent {}

    // -------------------------------------------------------------------------
    // Economy
    // -------------------------------------------------------------------------

    record PartyGoldChanged(int newTotal, int delta) implements GameEvent {}

    record BankBalanceChanged(int newBalance, int delta) implements GameEvent {}

    record PartyDisplaySetup(List<PlayerCharacter> party) implements GameEvent {}

    // -------------------------------------------------------------------------
    // Combat lifecycle
    // -------------------------------------------------------------------------

    record CombatStarted(
            int     partySize,
            int     enemyCount,
            boolean scripted
    ) implements GameEvent {}

    record CombatConcluded(BattleResult result) implements GameEvent {}

    // -------------------------------------------------------------------------
    // World
    // -------------------------------------------------------------------------

    record PartyMoved(
            com.phantasia.core.world.WorldPosition newPosition,
            com.phantasia.core.world.TileType      tileType
    ) implements GameEvent {}

    record QuestFlagSet(
            com.phantasia.core.data.QuestFlag flag,
            String                            flagName
    ) implements GameEvent {}

    /**
     * Fired by WorldState (jme-world) when a random or scripted encounter
     * is triggered.  GameManagerState (jme-app) handles this by attaching
     * CombatState — keeping jme-world free of any jme-combat dependency.
     */
    record EncounterTriggered() implements GameEvent {}

    /**
     * Fired by WorldState (jme-world) when the party enters a town tile.
     * GameManagerState (jme-app) handles this by attaching TownState —
     * keeping jme-world free of any jme-ui dependency.
     */
    record TownEntered(int townId, String townName) implements GameEvent {}

    /**
     * Fired by HUDState when the player clicks "Return to Map" after victory
     * or escape. GameManagerState handles re-enabling WorldState and detaching
     * CombatState — keeping jme-ui free of direct jme-world/combat imports.
     */
    record ReturnToWorldRequested() implements GameEvent {}

    // -------------------------------------------------------------------------
    // Session lifecycle
    // -------------------------------------------------------------------------

    record PartyJudged(JudgmentEngine.JudgmentResult result) implements GameEvent {}

    record ReloadSaveRequested() implements GameEvent {}

    // -------------------------------------------------------------------------
    // Combat log
    // -------------------------------------------------------------------------

    /**
     * Fired by VisualBridgeAppState (jme-combat) wherever it previously called
     * HUDState.postLogEntry() directly.  HUDState (jme-ui) subscribes and
     * calls its own postLogEntry() internally.
     */
    record CombatLogEntry(String message) implements GameEvent {}

    // -------------------------------------------------------------------------
    // NPC intelligence  (consumed by HUDState, TownState, future dialogue UI)
    // -------------------------------------------------------------------------


}
