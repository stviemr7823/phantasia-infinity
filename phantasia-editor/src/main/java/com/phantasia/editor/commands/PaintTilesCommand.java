// phantasia-editor/src/main/java/com/phantasia/editor/commands/PaintTilesCommand.java
package com.phantasia.editor.commands;

import com.phantasia.editor.EditCommand;
import com.phantasia.core.world.InteriorMap;

import java.util.*;

/**
 * Captures a single tile-painting stroke as one undoable command.
 *
 * <p>When the designer clicks and drags to paint tiles on an interior map,
 * each tile change is collected into a single {@code PaintTilesCommand}.
 * One Ctrl+Z undoes the whole stroke, not individual tiles.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // In the interior map editor's mouse handler:
 * PaintTilesCommand cmd = new PaintTilesCommand(interiorMap);
 *
 * // During the drag:
 * cmd.addTile(x, y, oldTileIndex, newTileIndex);
 *
 * // On mouse release:
 * if (!cmd.isEmpty()) {
 *     EditorState.get().execute(cmd);
 * }
 * }</pre>
 */
public class PaintTilesCommand implements EditCommand {

    private final InteriorMap map;
    private final String      dirtyKey;

    /** Ordered list of tile changes — preserves painting sequence. */
    private final List<TileChange> changes = new ArrayList<>();

    /** De-duplication — if the same tile is painted multiple times in
     *  one stroke, only the first old value and the last new value matter. */
    private final Map<Long, TileChange> byPosition = new LinkedHashMap<>();

    public PaintTilesCommand(InteriorMap map) {
        this.map      = Objects.requireNonNull(map);
        this.dirtyKey = "interiorMap:" + map.getId();
    }

    /**
     * Records a single tile change within this stroke.
     * Safe to call multiple times for the same position — the before
     * state is captured from the first visit, the after state from the last.
     */
    public void addTile(int x, int y, int oldIndex, int newIndex) {
        long key = packPos(x, y);
        TileChange existing = byPosition.get(key);
        if (existing != null) {
            // Same tile repainted in same stroke — update the target value
            existing.newIndex = newIndex;
        } else {
            TileChange change = new TileChange(x, y, oldIndex, newIndex);
            byPosition.put(key, change);
            changes.add(change);
        }
    }

    /** Returns true if no tiles were actually changed. */
    public boolean isEmpty() {
        return byPosition.isEmpty();
    }

    /** Returns the number of distinct tiles changed in this stroke. */
    public int tileCount() {
        return byPosition.size();
    }

    @Override
    public void execute() {
        for (TileChange c : byPosition.values()) {
            map.setTile(c.x, c.y, c.newIndex);
        }
    }

    @Override
    public void undo() {
        for (TileChange c : byPosition.values()) {
            map.setTile(c.x, c.y, c.oldIndex);
        }
    }

    @Override
    public String description() {
        int count = byPosition.size();
        return "Paint " + count + " tile" + (count == 1 ? "" : "s")
                + " on " + map.getName();
    }

    @Override
    public String dirtyKey() {
        return dirtyKey;
    }

    // -------------------------------------------------------------------------

    private static long packPos(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /** Mutable — only the newIndex may be updated during a drag. */
    private static class TileChange {
        final int x, y;
        final int oldIndex;
        int       newIndex;

        TileChange(int x, int y, int oldIndex, int newIndex) {
            this.x        = x;
            this.y        = y;
            this.oldIndex = oldIndex;
            this.newIndex = newIndex;
        }
    }
}
