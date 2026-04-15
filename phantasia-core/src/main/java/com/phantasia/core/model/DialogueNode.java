// phantasia-core/src/main/java/com/phantasia/core/model/DialogueNode.java
package com.phantasia.core.model;

import com.phantasia.core.data.QuestFlag;

import java.util.List;
import java.util.Set;

/**
 * A single dialogue entry in an NPC's script.
 *
 * Each node carries the text to display and the conditions under which
 * it should be selected. {@link DialogueScript} evaluates nodes in order
 * and returns the first whose conditions are satisfied.
 *
 * TEXT TOKENS:
 *   The text field may contain substitution tokens that are resolved
 *   at display time by {@link DialogueTextRenderer}:
 *
 *     {player_name}  — party leader's name
 *     {npc_name}     — this NPC's display name
 *     {gold}         — party gold
 *     {party_size}   — number of living party members
 *
 *   Conditional blocks are also supported:
 *     {if gold >= 100}You seem prosperous.{else}Times are hard, eh?{endif}
 *
 * FLAG GATING:
 *   - requiredFlags:  ALL must be set for this node to match.
 *   - excludedFlags:  NONE may be set for this node to match.
 *   - Empty sets mean "no condition" (always passes that check).
 *
 * SIDE EFFECTS:
 *   - flagToSet:   set on the session after the dialogue is delivered.
 *   - giveItemId:  item added to party inventory after dialogue (0 = none).
 *
 * MULTI-LINE:
 *   The lines list supports sequential text display — each entry is shown
 *   as a separate dialogue panel that the player advances through.
 *   For single-line dialogue, use a one-element list.
 */
public record DialogueNode(
        List<String>    lines,            // dialogue text (one string per panel)
        Set<QuestFlag>  requiredFlags,    // ALL must be set (empty = always matches)
        Set<QuestFlag>  excludedFlags,    // NONE may be set (empty = no exclusions)
        QuestFlag       flagToSet,        // set after delivery (null = none)
        int             giveItemId        // item granted after delivery (0 = none)
) {

    /**
     * Convenience constructor for simple single-line, single-flag nodes.
     * Preserves backward compatibility with existing usage.
     */
    public DialogueNode(String text,
                        QuestFlag requiredFlag,
                        QuestFlag forbiddenFlag,
                        QuestFlag flagToSet) {
        this(
                List.of(text),
                requiredFlag  != null ? Set.of(requiredFlag)  : Set.of(),
                forbiddenFlag != null ? Set.of(forbiddenFlag) : Set.of(),
                flagToSet,
                0
        );
    }
}