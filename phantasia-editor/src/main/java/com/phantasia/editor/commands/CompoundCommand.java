// phantasia-editor/src/main/java/com/phantasia/editor/commands/CompoundCommand.java
package com.phantasia.editor.commands;

import com.phantasia.editor.EditCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Groups multiple commands into a single undoable unit.
 *
 * <p>Use this when a user action logically involves several mutations
 * that should be treated as atomic. Examples:</p>
 * <ul>
 *   <li>Deleting a town: remove the TownDefinition, remove its interior
 *       map, remove the overworld feature — all as one undo step.</li>
 *   <li>Generating a dungeon floor: fill tiles + place stairs + set
 *       settings — one undo reverts the entire generation.</li>
 * </ul>
 *
 * <p>Execute runs children in order; undo runs them in reverse.</p>
 */
public class CompoundCommand implements EditCommand {

    private final String            description;
    private final String            dirtyKey;
    private final List<EditCommand> children;

    /**
     * @param description the label for the Edit menu
     * @param dirtyKey    the primary dirty key (use the most significant one)
     * @param children    the commands to group, in execution order
     */
    public CompoundCommand(String description, String dirtyKey,
                           List<EditCommand> children) {
        this.description = Objects.requireNonNull(description);
        this.dirtyKey    = Objects.requireNonNull(dirtyKey);
        this.children    = new ArrayList<>(children);
    }

    @Override
    public void execute() {
        for (EditCommand cmd : children) {
            cmd.execute();
        }
    }

    @Override
    public void undo() {
        // Reverse order
        for (int i = children.size() - 1; i >= 0; i--) {
            children.get(i).undo();
        }
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String dirtyKey() {
        return dirtyKey;
    }

    /** Returns the child commands (unmodifiable). */
    public List<EditCommand> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /** Returns the number of child commands. */
    public int size() {
        return children.size();
    }
}