// phantasia-core/src/main/java/com/phantasia/core/logic/WorldEvent.java
package com.phantasia.core.logic;

import com.phantasia.core.world.ScriptedEncounter;
import com.phantasia.core.world.TileEvent;

/**
 * An immutable description of something significant that happened as a
 * direct result of the party moving onto a tile.
 *
 * WorldEvent is the world-exploration counterpart to CombatEvent — it uses
 * the same sealed-interface pattern so every frontend switch is exhaustive
 * and the compiler enforces completeness when new subtypes are added.
 *
 * DESIGN:
 *   WorldEvent subtypes carry only the data the frontend needs to act.
 *   They never carry renderer-specific objects (Spatials, Nodes, textures).
 *   The core decides *what* happened; the frontend decides *how* to show it.
 *
 * SUBTYPES:
 *
 *   EnterTown           — party stepped onto a town tile.
 *                         Frontend should transition to TownState.
 *
 *   EnterDungeon        — party stepped onto a dungeon tile.
 *                         Frontend should transition to DungeonState.
 *
 *   RandomEncounter     — terrain step timer fired a random encounter.
 *                         Frontend should transition to CombatState.
 *                         (EncounterFactory generates the actual monsters.)
 *
 *   ScriptedBattle      — party stepped onto a tile with a fixed guardian.
 *                         Frontend should build the specific monsters named
 *                         here and transition to CombatState.
 *
 *   TileEventPrompt     — party stepped onto an interactive tile (altar,
 *                         chest, inscription). Frontend should present
 *                         the prompt and await player confirmation before
 *                         resolving the outcome.
 *
 * USAGE (any frontend):
 *   Optional<WorldEvent> event = result.worldEvent();
 *   if (event.isPresent()) {
 *       switch (event.get()) {
 *           case WorldEvent.EnterTown t        -> sceneManager.loadTown(t.id());
 *           case WorldEvent.EnterDungeon d     -> sceneManager.loadDungeon(d.id());
 *           case WorldEvent.RandomEncounter r  -> triggerCombat();
 *           case WorldEvent.ScriptedBattle s   -> triggerScriptedCombat(s);
 *           case WorldEvent.TileEventPrompt p  -> showPrompt(p.tileEvent());
 *       }
 *   }
 */
public sealed interface WorldEvent permits
        WorldEvent.EnterTown,
        WorldEvent.EnterDungeon,
        WorldEvent.RandomEncounter,
        WorldEvent.ScriptedBattle,
        WorldEvent.TileEventPrompt,
        WorldEvent.NpcInteraction
{
    // -------------------------------------------------------------------------
    // Subtypes
    // -------------------------------------------------------------------------

    /**
     * The party stepped onto a TOWN feature tile.
     *
     * @param id    index into the town data table
     * @param name  display name, e.g. "Scandor"
     */
    record EnterTown(int id, String name) implements WorldEvent {}

    /**
     * The party stepped onto a DUNGEON feature tile.
     *
     * @param id    index into the dungeon data table
     * @param name  display name, e.g. "Pendragon Archives"
     */
    record EnterDungeon(int id, String name) implements WorldEvent {}

    /**
     * The terrain step timer fired a random encounter.
     * No additional data needed — the frontend calls EncounterFactory
     * to generate the actual monster formation.
     */
    record RandomEncounter() implements WorldEvent {}

    /**
     * The party stepped onto a tile with a scripted guardian.
     *
     * @param monsterName  key into MonsterFactory for the specific enemy
     * @param count        exact number of enemies in this encounter
     * @param repeatable   true if the tile respawns after the party wins
     */
    record ScriptedBattle(
            String  monsterName,
            int     count,
            boolean repeatable
    ) implements WorldEvent {}

    /**
     * The party stepped onto an interactive tile (altar, chest, inscription,
     * sealed door). The frontend should display the event's description and
     * prompt, wait for player input, and call TileEvent.resolve() when done.
     *
     * @param tileEvent  the full event definition including prompt and outcomes
     */
    record TileEventPrompt(TileEvent tileEvent) implements WorldEvent {}

    record NpcInteraction(String npcId, String npcName) implements WorldEvent {}
}
