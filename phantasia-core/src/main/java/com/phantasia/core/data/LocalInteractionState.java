package com.phantasia.core.data;

import java.util.HashMap;
import java.util.Map;

/** Runtime pending rewards for a play session, independent of authored maps.
 * Pending rolls are not yet part of the legacy save format.
 */
public final class LocalInteractionState {
    public record Key(int mapId, int x, int y) {}
    private final Map<Key, LootTable.LootResult> pending = new HashMap<>();
    public LootTable.LootResult pending(Key key) { return pending.get(key); }
    public void remember(Key key, LootTable.LootResult loot) { pending.put(key, loot); }
    public void forget(Key key) { pending.remove(key); }
    public Map<Key, LootTable.LootResult> snapshot() {
        var copy = new HashMap<Key, LootTable.LootResult>();
        pending.forEach((k, v) -> copy.put(k, new LootTable.LootResult(v.gold(), java.util.List.copyOf(v.items()))));
        return Map.copyOf(copy);
    }
}
