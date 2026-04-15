// phantasia-core/src/main/java/com/phantasia/core/data/QuestObjective.java
package com.phantasia.core.data;

/**
 * A single objective within a {@link Quest}.
 *
 * Each objective has a description shown in the journal and a QuestFlag
 * that marks it as complete. The quest checks all non-optional objectives
 * to determine overall completion.
 *
 * @param description    journal text ("Speak with Filmon in Pendragon")
 * @param completionFlag this objective is done when this flag is set
 * @param optional       optional objectives don't block quest completion
 * @param order          display order in the journal (lower = earlier)
 */
public record QuestObjective(
        String    description,
        QuestFlag completionFlag,
        boolean   optional,
        int       order
) {

    /** Convenience constructor for required objectives. */
    public QuestObjective(String description, QuestFlag completionFlag, int order) {
        this(description, completionFlag, false, order);
    }
}