// phantasia-editor/src/main/java/com/phantasia/editor/commands/SetFieldCommand.java
package com.phantasia.editor.commands;

import com.phantasia.editor.EditCommand;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A generic command for any form-field change: set a value, remember the
 * old value, undo by restoring it.
 *
 * <p>This covers the vast majority of editor mutations: changing a monster's
 * HP, an NPC's name, a quest objective's text, a shop entry's price
 * multiplier, an interior setting's torch radius — anything that reads
 * and writes a single property.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // In the monster editor panel, when the user commits a new HP value:
 * int oldHp = DataCore.getShort(block, DataLayout.HP);
 * int newHp = Integer.parseInt(hpField.getText());
 *
 * EditorState.get().execute(new SetFieldCommand<>(
 *     "monster:" + index,
 *     "Set " + monsterName + " HP to " + newHp,
 *     () -> DataCore.getShort(block, DataLayout.HP),   // getter
 *     v  -> DataCore.setShort(block, DataLayout.HP, v), // setter
 *     oldHp,
 *     newHp
 * ));
 * }</pre>
 *
 * @param <T> the field's value type (Integer, String, Boolean, enum, etc.)
 */
public class SetFieldCommand<T> implements EditCommand {

    private final String      dirtyKey;
    private final String      description;
    private final Consumer<T> setter;
    private final T           oldValue;
    private final T           newValue;

    /**
     * Creates a field-change command.
     *
     * @param dirtyKey    the dirty tracking key (e.g. "monster:3")
     * @param description human-readable label for the Edit menu
     * @param getter      reads the current value (used only for assertion — may be null)
     * @param setter      writes the value
     * @param oldValue    the value before the change
     * @param newValue    the value after the change
     */
    public SetFieldCommand(String dirtyKey, String description,
                           Supplier<T> getter, Consumer<T> setter,
                           T oldValue, T newValue) {
        this.dirtyKey    = Objects.requireNonNull(dirtyKey);
        this.description = Objects.requireNonNull(description);
        this.setter      = Objects.requireNonNull(setter);
        this.oldValue    = oldValue;
        this.newValue    = newValue;
    }

    @Override
    public void execute() {
        setter.accept(newValue);
    }

    @Override
    public void undo() {
        setter.accept(oldValue);
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String dirtyKey() {
        return dirtyKey;
    }

    /** Returns true if old and new values are equal (no-op change). */
    public boolean isNoOp() {
        return Objects.equals(oldValue, newValue);
    }

    @Override
    public String toString() {
        return "SetFieldCommand[" + description + ": " + oldValue + " → " + newValue + "]";
    }
}
