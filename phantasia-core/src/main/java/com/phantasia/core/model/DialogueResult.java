// phantasia-core/src/main/java/com/phantasia/core/model/DialogueResult.java
package com.phantasia.core.model;

import java.util.List;

/**
 * The result of evaluating a {@link DialogueScript} against the current
 * game state.
 *
 * Contains the fully rendered dialogue text (tokens substituted, conditionals
 * resolved) and any side-effect data the caller should act on.
 *
 * @param lines       rendered dialogue text, one string per display panel
 * @param giveItemId  item to grant to the party (0 = none)
 */
public record DialogueResult(
        List<String> lines,
        int          giveItemId
) {

    /** Returns true if this result contains no meaningful dialogue. */
    public boolean isEmpty() {
        return lines.isEmpty() || (lines.size() == 1 && lines.getFirst().equals("..."));
    }

    /** Returns all lines joined with newlines — convenience for simple display. */
    public String joinedText() {
        return String.join("\n", lines);
    }
}