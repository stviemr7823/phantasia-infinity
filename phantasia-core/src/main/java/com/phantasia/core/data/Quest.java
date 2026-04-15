// phantasia-core/src/main/java/com/phantasia/core/data/Quest.java
package com.phantasia.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A named, trackable unit of story with objectives and rewards.
 *
 * Quests give structure to the quest flag system. Each quest is a designer-
 * authored story beat with a sequence of objectives (each tied to a QuestFlag),
 * a trigger condition for when it appears in the journal, and rewards granted
 * on completion.
 *
 * DESIGN INTENT:
 *   Quests in Phantasia: Infinity are linear — no branching, no faction
 *   reputation, no multiple endings. A quest is a sequence of things to do,
 *   and the game checks them off. This matches the original Phantasie III
 *   structure: collect the scrolls, build toward the final confrontation.
 *
 * LIFECYCLE:
 *   - Authored in the editor's Quest panel
 *   - Baked to quests.dat
 *   - Loaded at runtime by QuestJournal
 *   - Displayed in the player's journal UI
 *   - Completed when all non-optional objectives have their flags set
 *
 * COMPLETION:
 *   The quest checks each non-optional objective's completionFlag against
 *   GameSession. When all are set, the quest is complete and the
 *   completionFlag is set on the session, triggering rewards.
 */
public class Quest {

    private int                       id;
    private String                    name;            // "The First Scroll"
    private String                    description;     // journal summary
    private QuestFlag                 triggerFlag;     // quest appears when set
    private QuestFlag                 completionFlag;  // set when all objectives done
    private final List<QuestObjective> objectives = new ArrayList<>();
    private QuestRewards              rewards;

    public Quest() {
        this.rewards = new QuestRewards();
    }

    public Quest(int id, String name, String description,
                 QuestFlag triggerFlag, QuestFlag completionFlag) {
        this.id             = id;
        this.name           = name;
        this.description    = description;
        this.triggerFlag    = triggerFlag;
        this.completionFlag = completionFlag;
        this.rewards        = new QuestRewards();
    }

    // -------------------------------------------------------------------------
    // Objective management
    // -------------------------------------------------------------------------

    public void addObjective(QuestObjective obj) {
        objectives.add(obj);
    }

    public List<QuestObjective> getObjectives() {
        return Collections.unmodifiableList(objectives);
    }

    // -------------------------------------------------------------------------
    // Status evaluation
    // -------------------------------------------------------------------------

    /**
     * Returns true if the quest is visible in the journal.
     * @param flagChecker typically session::hasFlag
     */
    public boolean isTriggered(java.util.function.Predicate<QuestFlag> flagChecker) {
        return triggerFlag == null || flagChecker.test(triggerFlag);
    }

    /**
     * Returns true if all required (non-optional) objectives are complete.
     * @param flagChecker typically session::hasFlag
     */
    public boolean isComplete(java.util.function.Predicate<QuestFlag> flagChecker) {
        return objectives.stream()
                .filter(o -> !o.optional())
                .allMatch(o -> flagChecker.test(o.completionFlag()));
    }

    /**
     * Returns the number of completed objectives (including optional).
     * @param flagChecker typically session::hasFlag
     */
    public long completedCount(java.util.function.Predicate<QuestFlag> flagChecker) {
        return objectives.stream()
                .filter(o -> flagChecker.test(o.completionFlag()))
                .count();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int            getId()             { return id; }
    public String         getName()           { return name; }
    public String         getDescription()    { return description; }
    public QuestFlag      getTriggerFlag()    { return triggerFlag; }
    public QuestFlag      getCompletionFlag() { return completionFlag; }
    public QuestRewards   getRewards()        { return rewards; }

    public void setId(int id)                          { this.id = id; }
    public void setName(String name)                   { this.name = name; }
    public void setDescription(String desc)            { this.description = desc; }
    public void setTriggerFlag(QuestFlag flag)         { this.triggerFlag = flag; }
    public void setCompletionFlag(QuestFlag flag)      { this.completionFlag = flag; }
    public void setRewards(QuestRewards rewards)       { this.rewards = rewards; }

    @Override
    public String toString() {
        return "Quest[" + id + "] " + name
                + " (" + objectives.size() + " objectives)";
    }
}
