// phantasia-j2d/src/main/java/com/phantasia/j2d/engine/GameState.java
package com.phantasia.j2d.engine;

/**
 * The set of game screens that the {@link ScreenManager} can transition between.
 *
 * Each value maps to a single {@link Screen} implementation registered in GameMain.
 * Full-screen transitions use {@code fadeTo()}; overlays use {@code push()}/{@code pop()}.
 */
public enum GameState {

    // ── Full-screen states ────────────────────────────────────────────────

    /** World roam — tile-based free movement with float coordinates. */
    WORLD_ROAM,

    /** Encounter splash — "An Encounter Begins" announcement card. */
    ENCOUNTER_SPLASH,

    /** Combat planning — assign actions before resolution. */
    COMBAT_PLANNING,

    /** Combat execution — cinematic round resolution with animations. */
    COMBAT_EXECUTION,

    /** Town interior — walkable tile map with NPCs and service counters. */
    TOWN,

    /** Shared authored town/castle/dungeon traversal. */
    LOCAL_MAP,

    /** Isolated native party progression diagnostic; registered by the test launcher. */
    NATIVE_DIAGNOSTIC,

    /** Authored-map diagnostic backed by the native party owner. */
    NATIVE_LOCAL_MAP,

    /** Native inn/bank overlay using authored service terms. */
    NATIVE_SERVICE,

    /** Dungeon roam — interior tile movement with fog of war. */
    DUNGEON,

    /** Victory screen — post-combat loot and XP summary. */
    VICTORY,

    /** Defeat screen — party wipe. */
    DEFEAT,

    // ── Overlay states (pushed/popped over a full-screen state) ──────────

    /** Shop overlay — buy/sell items with a merchant NPC. */
    SHOP,

    /** Inn overlay — restore party HP/MP for gold. */
    INN,

    /** Guild overlay — level up party members for gold. */
    GUILD,

    /** Bank overlay — deposit and withdraw party gold. */
    BANK,

    /** Dialogue overlay — NPC conversation with portrait. */
    DIALOGUE,

    /** Pause overlay — resume or quit to desktop. */
    PAUSE,

}
