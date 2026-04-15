// phantasia-editor/src/main/java/com/phantasia/editor/commands/RemoveMapCommand.java
package com.phantasia.editor.commands;

import com.phantasia.editor.EditCommand;

import java.util.Map;
import java.util.Objects;

/**
 * Removes an entry from a keyed map. Undo restores it.
 *
 * @param <K> the key type (usually Integer)
 * @param <V> the value type
 */
public class RemoveMapCommand<K, V> implements EditCommand {

    private final Map<K, V> map;
    private final K         key;
    private final String    category;
    private final String    dirtyKey;
    private final String    description;
    private V               removed;

    public RemoveMapCommand(Map<K, V> map, K key,
                            String category, String dirtyKey,
                            String description) {
        this.map         = Objects.requireNonNull(map);
        this.key         = Objects.requireNonNull(key);
        this.category    = Objects.requireNonNull(category);
        this.dirtyKey    = Objects.requireNonNull(dirtyKey);
        this.description = Objects.requireNonNull(description);
    }

    @Override
    public void execute() {
        removed = map.remove(key);
    }

    @Override
    public void undo() {
        if (removed != null) {
            map.put(key, removed);
        }
    }

    @Override public String description() { return description; }
    @Override public String dirtyKey()    { return dirtyKey; }

    public String category()     { return category; }
    public V      removedValue() { return removed; }
}
