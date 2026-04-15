// phantasia-core/src/main/java/com/phantasia/core/model/PlacedNpc.java
package com.phantasia.core.model;

import com.phantasia.core.data.QuestFlag;
import com.phantasia.core.logic.Direction;

/**
 * An NPC placed at a specific position on an interior map.
 *
 * PlacedNpc is the bridge between an {@link NpcDefinition} (what the NPC is)
 * and a location on a town or dungeon grid (where it appears). The same
 * NpcDefinition can be placed in multiple locations — for example, an NPC
 * that moves between towns depending on quest progress would have two
 * PlacedNpc records with different appear/disappear conditions.
 *
 * VISIBILITY:
 *   - appearCondition:    NPC only appears after this flag is set.
 *   - disappearCondition: NPC is hidden once this flag is set.
 *   - Both null = always visible.
 *   - If both are set, the NPC is visible in the window between the two flags.
 *
 * INTERACTION:
 *   The player bumps into the NPC's tile (NPCs are impassable). The renderer
 *   identifies the PlacedNpc at that position, looks up the NpcDefinition by
 *   npcId, and dispatches the interaction based on the NPC's role.
 *
 * FACING:
 *   The NPC's idle facing direction. Renderers may override this to face
 *   the player during interaction. For j2d sprites, this determines which
 *   directional frame to show in the idle animation.
 */
public record PlacedNpc(
        int       npcId,                // references NpcDefinition.id
        int       x,                    // grid position
        int       y,
        Direction facing,               // idle facing direction
        QuestFlag appearCondition,      // visible after this flag (null = always)
        QuestFlag disappearCondition    // hidden after this flag (null = never)
) {

    /**
     * Convenience constructor for unconditionally visible NPCs.
     */
    public PlacedNpc(int npcId, int x, int y, Direction facing) {
        this(npcId, x, y, facing, null, null);
    }

    /**
     * Returns true if this NPC should be visible given the current flag state.
     *
     * @param flagChecker a function that tests whether a flag is set
     *                    (typically session::hasFlag)
     */
    public boolean isVisible(java.util.function.Predicate<QuestFlag> flagChecker) {
        if (appearCondition != null && !flagChecker.test(appearCondition)) {
            return false;
        }
        if (disappearCondition != null && flagChecker.test(disappearCondition)) {
            return false;
        }
        return true;
    }
}
