// phantasia-j2d/src/main/java/com/phantasia/j2d/engine/GameState.java
package com.phantasia.j2d.engine;

/**
 * The set of game screens that the {@link ScreenManager} can transition between.
 *
 * Each value maps to a single {@link Screen} implementation. The ScreenManager
 * maintains a {@code Map<GameState, Screen>} and handles transitions between them.
 */
public enum GameState {

    /** World roam — tile-based free movement with float coordinates. */
    WORLD_ROAM,

    /** Encounter splash — "An Encounter Begins" announcement card. */
    ENCOUNTER_SPLASH,

    /** Combat planning — assign actions before resolution. */
    COMBAT_PLANNING,

    /** Combat execution — cinematic round resolution with animations. */
    COMBAT_EXECUTION,

    /** Town services — inn, shop, guild, bank menus. */
    TOWN,

    /** Dialogue overlay — NPC conversation with portrait. */
    DIALOGUE,

    /** Victory screen — post-combat loot and XP summary. */
    VICTORY,

    /** Defeat screen — party wipe. */
    DEFEAT,

    /** Dungeon roam — interior tile movement. */
    DUNGEON,

    /** Pause overlay — pushed on top of any screen. */
    PAUSE
}