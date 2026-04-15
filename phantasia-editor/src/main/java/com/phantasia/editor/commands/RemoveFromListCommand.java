// phantasia-editor/src/main/java/com/phantasia/editor/commands/RemoveFromListCommand.java
package com.phantasia.editor.commands;

import com.phantasia.editor.EditCommand;

import java.util.List;
import java.util.Objects;

/**
 * Removes an element from an indexed list (monsters, items, spells).
 * Undo inserts it back at its original position; redo removes it again.
 *
 * @param <T> the element type
 */
public class RemoveFromListCommand<T> implements EditCommand {

    private final List<T>  list;
    private final int      index;
    private final String   category;
    private final String   dirtyKey;
    private final String   description;
    private T              removed;

    public RemoveFromListCommand(List<T> list, int index,
                                 String category, String dirtyKey,
                                 String description) {
        this.list        = Objects.requireNonNull(list);
        this.index       = index;
        this.category    = Objects.requireNonNull(category);
        this.dirtyKey    = Objects.requireNonNull(dirtyKey);
        this.description = Objects.requireNonNull(description);
    }

    @Override
    public void execute() {
        if (index >= 0 && index < list.size()) {
            removed = list.remove(index);
        }
    }

    @Override
    public void undo() {
        if (removed != null && index >= 0 && index <= list.size()) {
            list.add(index, removed);
        }
    }

    @Override public String description() { return description; }
    @Override public String dirtyKey()    { return dirtyKey; }

    public String category()   { return category; }
    public int    removedIndex() { return index; }
    public T      removedElement() { return removed; }
}