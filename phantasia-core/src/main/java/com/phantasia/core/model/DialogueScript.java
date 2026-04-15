// phantasia-core/src/main/java/com/phantasia/core/model/DialogueScript.java
package com.phantasia.core.model;

import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.QuestFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered sequence of {@link DialogueNode}s that drives NPC conversation.
 *
 * The script walks the node list and returns the first node whose conditions
 * are satisfied by the current game state:
 *
 *   - {@code requiredFlags}  — the node only matches if ALL these flags are set.
 *   - {@code excludedFlags}  — the node only matches if NONE of these flags are set.
 *   - Empty flag sets count as "no condition" (always passes).
 *
 * The last node in the sequence should typically have empty conditions as a
 * fallback — if no node matches, a generic default message is returned.
 *
 * TEXT RENDERING:
 *   The raw text from the matching node is passed through
 *   {@link DialogueTextRenderer} for token substitution and conditional
 *   block evaluation before being returned to the caller.
 *
 * SIDE EFFECTS:
 *   If the matching node has a {@code flagToSet}, that flag is applied to the
 *   session immediately. If it has a {@code giveItemId}, the item is returned
 *   in the {@link DialogueResult} for the caller to grant to the party.
 *
 * USAGE:
 *   DialogueScript script = npcDefinition.getDialogue();
 *   DialogueContext ctx = DialogueContext.freeze(npcDefinition, session);
 *   DialogueResult result = script.evaluate(session, ctx);
 *   // result.lines() — rendered text to display
 *   // result.giveItemId() — item to grant (0 = none)
 */
public class DialogueScript {

    private final List<DialogueNode> nodes = new ArrayList<>();

    public void addNode(DialogueNode node) {
        nodes.add(node);
    }

    public List<DialogueNode> getNodes() {
        return List.copyOf(nodes);
    }

    public int size() {
        return nodes.size();
    }

    // -------------------------------------------------------------------------
    // Evaluation
    // -------------------------------------------------------------------------

    /**
     * Evaluates the script against the current session state and returns
     * a fully rendered result.
     *
     * @param session the live game session (quest flags are read and may be set)
     * @param context the dialogue context for text rendering
     * @return the result containing rendered lines and any side effects
     */
    public DialogueResult evaluate(GameSession session, DialogueContext context) {
        for (DialogueNode node : nodes) {
            if (matches(node, session)) {
                // Apply side effect: set quest flag
                if (node.flagToSet() != null) {
                    session.setFlag(node.flagToSet());
                }

                // Render text with token substitution and conditionals
                DialogueTextRenderer renderer = new DialogueTextRenderer();
                List<String> rendered = new ArrayList<>();
                for (String line : node.lines()) {
                    rendered.add(renderer.render(line, context));
                }

                return new DialogueResult(rendered, node.giveItemId());
            }
        }

        // No matching node — return a generic fallback
        return new DialogueResult(
                List.of("..."),
                0
        );
    }

    /**
     * Legacy convenience method — returns just the text of the first matching node.
     * Preserves backward compatibility with existing callers.
     */
    public String getActiveText(GameSession session) {
        for (DialogueNode node : nodes) {
            if (matches(node, session)) {
                if (node.flagToSet() != null) {
                    session.setFlag(node.flagToSet());
                }
                return node.lines().isEmpty() ? "" : node.lines().getFirst();
            }
        }
        return "The person has nothing to say.";
    }

    // -------------------------------------------------------------------------
    // Condition matching
    // -------------------------------------------------------------------------

    private boolean matches(DialogueNode node, GameSession session) {
        // All required flags must be set
        for (QuestFlag flag : node.requiredFlags()) {
            if (!session.hasFlag(flag)) return false;
        }
        // No excluded flag may be set
        for (QuestFlag flag : node.excludedFlags()) {
            if (session.hasFlag(flag)) return false;
        }
        return true;
    }
}