// phantasia-editor/src/main/java/com/phantasia/editor/commands/AddToListCommand.java
package com.phantasia.editor.commands;

import com.phantasia.editor.EditCommand;

import java.util.List;
import java.util.Objects;

/**
 * Adds an element to an indexed list (monsters, items, spells).
 * Undo removes it from the end; redo adds it back.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * byte[] newMonster = DataCore.createBlank();
 * DataCore.setString(newMonster, DataLayout.NAME, "New Monster");
 *
 * EditorState state = EditorState.get();
 * state.execute(new AddToListCommand<>(
 *     state.getMonstersMutable(),   // the backing list
 *     newMonster,
 *     "monsters",
 *     "monster:" + state.getMonsterCount(),
 *     "Add monster 'New Monster'"
 * ));
 * }</pre>
 *
 * @param <T> the element type
 */
public class AddToListCommand<T> implements EditCommand {

    private final List<T>  list;
    private final T        element;
    private final String   category;     // for fireCollectionChanged
    private final String   dirtyKey;
    private final String   description;
    private int            insertedIndex = -1;

    public AddToListCommand(List<T> list, T element,
                            String category, String dirtyKey,
                            String description) {
        this.list        = Objects.requireNonNull(list);
        this.element     = Objects.requireNonNull(element);
        this.category    = Objects.requireNonNull(category);
        this.dirtyKey    = Objects.requireNonNull(dirtyKey);
        this.description = Objects.requireNonNull(description);
    }

    @Override
    public void execute() {
        list.add(element);
        insertedIndex = list.size() - 1;
    }

    @Override
    public void undo() {
        if (insertedIndex >= 0 && insertedIndex < list.size()) {
            list.remove(insertedIndex);
        }
    }

    @Override public String description() { return description; }
    @Override public String dirtyKey()    { return dirtyKey; }

    /** The category name for collection-change notifications. */
    public String category() { return category; }

    /** The index where the element was inserted. Valid after execute(). */
    public int insertedIndex() { return insertedIndex; }
}