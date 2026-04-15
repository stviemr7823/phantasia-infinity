// phantasia-editor/src/main/java/com/phantasia/editor/EditorStateListener.java
package com.phantasia.editor;

/**
 * Observer interface for {@link EditorState} mutations.
 *
 * The asset explorer, properties panel, workspace tabs, and status bar
 * all implement this to stay current when data changes. Notifications
 * fire on the EDT (since all EditorState mutations happen on the EDT).
 *
 * Listeners receive coarse-grained notifications keyed by dirty key
 * (e.g. "interiorMap:5", "monster:3"). Fine-grained field-level
 * observation is not needed — panels that care about a specific record
 * check its identity against the dirty key and refresh if it matches.
 */
public interface EditorStateListener {

    /**
     * A record was modified (command executed, undone, or redone).
     *
     * @param dirtyKey the affected record's dirty key
     *                 (e.g. "worldMap", "npc:12", "monster:0")
     */
    default void onDataChanged(String dirtyKey) {}

    /**
     * The dirty set changed — a record became dirty or was cleaned
     * (via bake or undo-to-clean). The status bar listens for this.
     *
     * @param dirtyCount the current number of dirty records
     */
    default void onDirtyCountChanged(int dirtyCount) {}

    /**
     * The undo/redo stack changed — a command was pushed, popped,
     * or the stacks were cleared. The Edit menu listens for this
     * to enable/disable Undo/Redo and update their labels.
     *
     * @param undoDescription description of the top undo command, or null if empty
     * @param redoDescription description of the top redo command, or null if empty
     */
    default void onUndoRedoChanged(String undoDescription, String redoDescription) {}

    /**
     * A record was added to or removed from a collection (monsters,
     * items, NPCs, etc.). The asset explorer listens for this to
     * rebuild its tree nodes.
     *
     * @param category the collection that changed (e.g. "monsters", "npcs",
     *                 "interiorMaps", "quests")
     */
    default void onCollectionChanged(String category) {}

    /**
     * The entire project was replaced — new project, load project,
     * or initial startup load. All panels should fully refresh.
     */
    default void onProjectLoaded() {}
}