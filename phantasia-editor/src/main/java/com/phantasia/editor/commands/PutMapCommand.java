// phantasia-editor/src/main/java/com/phantasia/editor/commands/PutMapCommand.java
package com.phantasia.editor.commands;

import com.phantasia.editor.EditCommand;

import java.util.Map;
import java.util.Objects;

/**
 * Puts a value into a keyed map (NPCs, quests, towns, dungeons, shops,
 * interior maps). If an entry with the same key existed, it's captured
 * for undo.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * NpcDefinition npc = new NpcDefinition(id, "Greta", NpcRole.MERCHANT, ...);
 * EditorState state = EditorState.get();
 * state.execute(new PutMapCommand<>(
 *     state.getNpcsMutable(), id, npc,
 *     "npcs", "npc:" + id,
 *     "Add NPC 'Greta'"
 * ));
 * }</pre>
 *
 * @param <K> the key type (usually Integer)
 * @param <V> the value type
 */
public class PutMapCommand<K, V> implements EditCommand {

    private final Map<K, V> map;
    private final K         key;
    private final V         newValue;
    private final String    category;
    private final String    dirtyKey;
    private final String    description;
    private V               previousValue;
    private boolean         hadPrevious;

    public PutMapCommand(Map<K, V> map, K key, V newValue,
                         String category, String dirtyKey,
                         String description) {
        this.map         = Objects.requireNonNull(map);
        this.key         = Objects.requireNonNull(key);
        this.newValue    = Objects.requireNonNull(newValue);
        this.category    = Objects.requireNonNull(category);
        this.dirtyKey    = Objects.requireNonNull(dirtyKey);
        this.description = Objects.requireNonNull(description);
    }

    @Override
    public void execute() {
        previousValue = map.put(key, newValue);
        hadPrevious = (previousValue != null);
    }

    @Override
    public void undo() {
        if (hadPrevious) {
            map.put(key, previousValue);
        } else {
            map.remove(key);
        }
    }

    @Override public String description() { return description; }
    @Override public String dirtyKey()    { return dirtyKey; }

    public String category()      { return category; }
    public boolean hadPrevious()  { return hadPrevious; }
    public V previousValue()      { return previousValue; }
}
