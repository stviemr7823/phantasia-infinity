// phantasia-editor/src/main/java/com/phantasia/editor/EditCommand.java
package com.phantasia.editor;

/**
 * A reversible mutation to {@link EditorState}.
 *
 * Every change to game data passes through this interface so undo/redo
 * works uniformly across all editor panels. Commands are pushed onto
 * the undo stack on execution; Ctrl+Z pops and undoes; Ctrl+Shift+Z
 * replays from the redo stack.
 *
 * <h3>Granularity Rules</h3>
 * <ul>
 *   <li><b>Tile painting:</b> One click-drag stroke = one command
 *       (stores before/after state for the entire stroke).</li>
 *   <li><b>Form fields:</b> Each field commit (focus-lost or Enter)
 *       = one command. Individual keystrokes are not tracked.</li>
 *   <li><b>Feature/NPC placement:</b> One command per place/move/delete.</li>
 *   <li><b>Dialogue editing:</b> One command per node add/remove/reorder;
 *       one command per text/flag commit.</li>
 *   <li><b>List operations:</b> One command per record add/delete.</li>
 * </ul>
 *
 * @see EditorState#execute(EditCommand)
 */
public interface EditCommand {

    /**
     * Applies the change to EditorState.
     * Called once when the command is first issued, and again on redo.
     */
    void execute();

    /**
     * Reverses the change, restoring the prior state.
     * Called on Ctrl+Z (undo).
     */
    void undo();

    /**
     * Human-readable label for the Edit menu.
     * Examples: "Paint 12 tiles", "Set Skeleton HP to 45",
     * "Place shop counter at (5, 3)", "Delete NPC Filmon".
     */
    String description();

    /**
     * The dirty-tracking key affected by this command.
     * Used by {@link EditorState} to mark/clear dirty flags.
     *
     * Convention:
     * <ul>
     *   <li>{@code "worldMap"} — overworld changes</li>
     *   <li>{@code "interiorMap:5"} — interior map with ID 5</li>
     *   <li>{@code "npc:12"} — NPC definition with ID 12</li>
     *   <li>{@code "quest:3"} — quest with ID 3</li>
     *   <li>{@code "monster:7"} — monster at index 7</li>
     *   <li>{@code "item:2"} — item at index 2</li>
     *   <li>{@code "spell:11"} — spell at index 11</li>
     *   <li>{@code "shop:1"} — shop inventory with ID 1</li>
     *   <li>{@code "town:0"} — town definition with ID 0</li>
     *   <li>{@code "dungeon:1"} — dungeon definition with ID 1</li>
     * </ul>
     *
     * @return the dirty key, never null
     */
    String dirtyKey();
}