// phantasia-core/src/main/java/com/phantasia/core/logic/GameCommand.java
package com.phantasia.core.logic;

/**
 * The full set of player-driven commands that core can process.
 *
 * GameCommand is the single, engine-agnostic vocabulary for player intent.
 * Each frontend maps its own raw inputs (key presses, button clicks, touch
 * events) to a GameCommand value and passes it to the appropriate core
 * manager.  Core never imports JME, libGDX, or any UI library.
 *
 * DESIGN:
 *   - Sealed interface with typed record subtypes — the compiler enforces
 *     that every switch in core and every frontend is exhaustive.
 *   - Records carry the minimum data core needs to act. They never contain
 *     engine-specific types (no KeyInput constants, no GdxInput codes).
 *   - Commands are immutable value objects — cheap to create, safe to cache.
 *
 * WORLD COMMANDS (processed by NavigationManager):
 *   Move(direction)  — step one tile in a cardinal direction
 *
 * COMBAT COMMANDS (processed by CombatManager / CombatState):
 *   ExecuteRound()   — advance to the next round (ENTER key / tap)
 *   Flee()           — attempt to escape the current encounter
 *
 * TOWN / UI COMMANDS:
 *   Rest()           — pay the inn and restore the party
 *   OpenMenu(screen) — open a named screen (inventory, guild, etc.)
 *
 * USAGE — JME frontend:
 *
 *   // In WorldState.onAction():
 *   GameCommand cmd = switch (mappingName) {
 *       case "World_North" -> new GameCommand.Move(Direction.NORTH);
 *       case "World_South" -> new GameCommand.Move(Direction.SOUTH);
 *       case "World_East"  -> new GameCommand.Move(Direction.EAST);
 *       case "World_West"  -> new GameCommand.Move(Direction.WEST);
 *       default            -> null;
 *   };
 *   if (cmd != null) handleCommand(cmd);
 *
 *   // In handleCommand():
 *   private void handleCommand(GameCommand cmd) {
 *       switch (cmd) {
 *           case GameCommand.Move m -> handleMove(m.direction());
 *           default                -> {}
 *       }
 *   }
 *
 * USAGE — libGDX frontend (future):
 *
 *   // In Screen.keyDown(int keycode):
 *   GameCommand cmd = switch (keycode) {
 *       case Keys.W, Keys.UP    -> new GameCommand.Move(Direction.NORTH);
 *       case Keys.S, Keys.DOWN  -> new GameCommand.Move(Direction.SOUTH);
 *       case Keys.D, Keys.RIGHT -> new GameCommand.Move(Direction.EAST);
 *       case Keys.A, Keys.LEFT  -> new GameCommand.Move(Direction.WEST);
 *       case Keys.ENTER         -> new GameCommand.ExecuteRound();
 *       default                 -> null;
 *   };
 *   if (cmd != null) inputRouter.dispatch(cmd);
 *
 * EXTENDING:
 *   To add a new command, add a new record to the permits list and implement
 *   the GameCommand interface.  Every exhaustive switch will fail to compile
 *   until the new case is handled — that is the intended safety net.
 */
public sealed interface GameCommand permits
        GameCommand.Move,
        GameCommand.ExecuteRound,
        GameCommand.Flee,
        GameCommand.Rest,
        GameCommand.OpenMenu
{
    // -------------------------------------------------------------------------
    // World navigation
    // -------------------------------------------------------------------------

    /**
     * Move one tile in the given direction.
     * Processed by NavigationManager.
     *
     * @param direction  cardinal direction (never null)
     */
    record Move(Direction direction) implements GameCommand {

        /** Convenience: convert a legacy lowercase string to a Move command. */
        public static Move of(String directionString) {
            return new Move(Direction.fromString(directionString));
        }

        /**
         * Returns the legacy string form NavigationManager currently expects.
         * Remove this once NavigationManager is updated to accept Direction.
         */
        public String directionString() {
            return direction.name().toLowerCase();
        }
    }

    // -------------------------------------------------------------------------
    // Combat
    // -------------------------------------------------------------------------

    /**
     * Advance the current combat encounter by one round.
     * Processed by CombatState / CombatManager.
     */
    record ExecuteRound() implements GameCommand {}

    /**
     * Attempt to flee the current encounter.
     * Processed by CombatState.  The FormulaEngine decides success.
     */
    record Flee() implements GameCommand {}

    // -------------------------------------------------------------------------
    // Town / UI
    // -------------------------------------------------------------------------

    /**
     * Pay the inn fee and restore the party to full HP/MP.
     * Processed by TownState.
     */
    record Rest() implements GameCommand {}

    /**
     * Navigate to a named screen within the current context.
     * Processed by whatever state is currently active.
     *
     * @param screen  the target screen identifier (e.g. "inventory", "guild")
     */
    record OpenMenu(String screen) implements GameCommand {}
}