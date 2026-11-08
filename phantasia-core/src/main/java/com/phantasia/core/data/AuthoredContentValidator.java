package com.phantasia.core.data;

import com.phantasia.core.model.*;
import com.phantasia.core.model.item.ItemTable;
import com.phantasia.core.world.*;
import com.phantasia.core.world.ServiceData.*;
import java.util.*;

/** Structural validation for the currently supported authored interior content.
 * Does not interpret quest logic or claim that a campaign is playable.
 */
public final class AuthoredContentValidator {
    public record Issue(String code, String location, String message) {
        @Override public String toString() { return location + ": " + message; }
    }
    private final List<Issue> issues = new ArrayList<>();
    private final Map<Integer, InteriorMap> maps = new LinkedHashMap<>();
    private final Map<Integer, NpcDefinition> npcs = new LinkedHashMap<>();
    private final Map<Integer, ShopInventory> shops = new LinkedHashMap<>();

    private AuthoredContentValidator() {}

    public static List<Issue> validate(Collection<InteriorMap> maps,
                                       Collection<NpcDefinition> npcs,
                                       Collection<ShopInventory> shops) {
        var v = new AuthoredContentValidator();
        for (var m : maps) if (v.maps.putIfAbsent(m.getId(), m) != null)
            v.error("DUPLICATE_ID", "Map #" + m.getId(), "duplicate map ID");
        for (var n : npcs) if (v.npcs.putIfAbsent(n.getId(), n) != null)
            v.error("DUPLICATE_ID", "NPC #" + n.getId(), "duplicate NPC ID");
        for (var s : shops) if (v.shops.putIfAbsent(s.getShopId(), s) != null)
            v.error("DUPLICATE_ID", "Shop #" + s.getShopId(), "duplicate shop ID");
        for (var s : shops) v.shop(s, "Shop #" + s.getShopId());
        for (var n : npcs) {
            String at = "NPC #" + n.getId();
            if (n.getId() < 0 || n.getId() > 65535) v.error("ID_RANGE", at, "ID must be 0–65535");
            if (n.getName() == null || n.getName().isBlank()) v.error("NAME", at, "name is required");
            if (n.getRole() == null) v.error("ROLE", at, "role is required");
            if (n.getRole() == NpcRole.MERCHANT || n.getShopId() != 0)
                if (!v.shops.containsKey(n.getShopId())) v.error("SHOP_REFERENCE", at, "shop #" + n.getShopId() + " does not exist");
            if (n.getDialogue() == null) v.error("DIALOGUE", at, "dialogue script is missing");
            else for (var node : n.getDialogue().getNodes())
                if (node.giveItemId() != 0) v.item(node.giveItemId(), at);
        }
        for (var m : maps) v.map(m);
        return List.copyOf(v.issues);
    }

    public static InteriorTileSet tileSet(String id) {
        if ("town_standard".equals(id)) return InteriorTileSet.standardTown();
        if ("dungeon_standard".equals(id)) return InteriorTileSet.standardDungeon();
        return null; // custom tile-set registries are not implemented yet
    }

    private void error(String code, String at, String text) {
        if (issues.size() < 200) issues.add(new Issue(code, at, text));
    }
    private void item(int id, String at) {
        if (!ItemTable.exists(id)) error("ITEM_REFERENCE", at, "item #" + id + " does not exist");
    }
    private void shop(ShopInventory s, String at) {
        if (s.getShopId() < 0 || s.getShopId() > 65535) error("ID_RANGE", at, "ID must be 0–65535");
        if (s.getName() == null || s.getName().isBlank()) error("NAME", at, "name is required");
        for (var e : s.getEntries()) {
            item(e.itemId(), at);
            if (e.quantity() < -1 || e.quantity() > 65534) error("QUANTITY", at, "stock must be -1 (unlimited) or 0–65534");
            if (!Float.isFinite(e.priceMultiplier()) || e.priceMultiplier() <= 0)
                error("PRICE", at, "price multiplier must be finite and positive");
        }
    }
    private void map(InteriorMap m) {
        String at = "Map #" + m.getId() + " (" + m.getName() + ")";
        if (m.getId() < 0) error("ID_RANGE", at, "ID cannot be negative");
        if (m.getName() == null || m.getName().isBlank()) error("NAME", at, "name is required");
        if (m.getMapType() == null) error("MAP_TYPE", at, "map type is required");
        if (m.getWidth() <= 0 || m.getHeight() <= 0 || (long) m.getWidth() * m.getHeight() > 1_000_000)
            error("DIMENSIONS", at, "dimensions must be positive, with at most 1,000,000 tiles");
        InteriorTileSet tiles = tileSet(m.getTileSetId());
        if (tiles == null) error("TILE_SET", at, "unknown tile set: " + m.getTileSetId());
        else for (int y = 0; y < m.getHeight(); y++) for (int x = 0; x < m.getWidth(); x++)
            if (tiles.getEntry(m.getTile(x, y)) == null)
                error("TILE_INDEX", at + " tile (" + x + "," + y + ")", "unknown tile index " + m.getTile(x, y));
        var s = m.getSettings();
        if (s.getAmbientLight() == null || s.getTorchRadius() < 0 || s.getEncounterInterval() < 0
                || (s.isEncountersEnabled() && s.getEncounterInterval() == 0))
            error("SETTINGS", at, "check light, torch radius and encounter interval");
        Set<String> entranceIds = new HashSet<>();
        for (var entry : m.getEntrances()) {
            String where = at + " entrance " + entry.id();
            if (entry.id() == null || entry.id().isBlank()) error("ENTRANCE_ID", where, "entrance name is required");
            if (!entranceIds.add(entry.id())) error("ENTRANCE_ID", where, "duplicate entrance name");
            if (entry.facing() == null) error("FACING", where, "arrival facing is required");
            if (!MapEntrances.isClear(m, entry.position())) error("ENTRANCE_POSITION", where, "arrival must be on a clear, passable tile away from exits, stairs and traps");
        }
        if (m.getDefaultEntranceId() == null || m.getDefaultEntranceId().isBlank()
                || m.getEntrance(m.getDefaultEntranceId()) == null)
            error("DEFAULT_ENTRANCE", at, "choose a default entrance");
        Set<WorldPosition> occupied = new HashSet<>();
        for (var f : m.getFeatures()) {
            String where = at + " " + f.featureType() + " (" + f.x() + "," + f.y() + ")";
            if (!m.inBounds(f.x(), f.y())) error("PLACEMENT", where, "feature is outside the map");
            if (!occupied.add(new WorldPosition(f.x(), f.y()))) error("OVERLAP", where, "multiple features occupy this tile");
            feature(m, f, where);
        }
        Set<WorldPosition> npcTiles = new HashSet<>();
        for (var n : m.getPlacedNpcs()) {
            String where = at + " NPC #" + n.npcId() + " (" + n.x() + "," + n.y() + ")";
            if (!m.inBounds(n.x(), n.y())) error("PLACEMENT", where, "NPC is outside the map");
            if (!npcs.containsKey(n.npcId())) error("NPC_REFERENCE", where, "NPC definition does not exist");
            if (n.facing() == null) error("FACING", where, "facing is required");
            if (n.appearCondition() != null && n.appearCondition() == n.disappearCondition())
                error("NPC_CONDITIONS", where, "appearance and disappearance cannot use the same flag");
            // Conditional NPCs can share a tile at different quest stages.
            if (n.appearCondition() == null && n.disappearCondition() == null
                    && !npcTiles.add(new WorldPosition(n.x(), n.y())))
                error("OVERLAP", where, "multiple unconditional NPCs occupy this tile");
        }
    }
    public static boolean payloadMatches(PlacedFeatureType type, Object d) {
        if (type == null) return false;
        return switch (type) {
            case SIGN, ALTAR -> d instanceof String;
            case SHOP_COUNTER -> d instanceof Integer || d instanceof ShopInventory;
            case INN_COUNTER -> d instanceof InnData;
            case GUILD_COUNTER -> d instanceof GuildData;
            case BANK_COUNTER -> d instanceof BankData;
            case CHEST -> d instanceof LootTable;
            case LOCKED_DOOR -> d instanceof LockData;
            case STAIRS_LINK -> d instanceof StairsData || d instanceof MapLinkData;
            case EXIT -> d == null || d instanceof ExitData; // v1/default entry-point exit
            case TRAP -> d instanceof TrapData;
        };
    }

    private void feature(InteriorMap source, PlacedFeature f, String at) {
        Object d = f.serviceData();
        if (f.featureType() == null) { error("PAYLOAD", at, "feature type is required"); return; }
        boolean valid = payloadMatches(f.featureType(), d);
        if (!valid) { error("PAYLOAD", at, "payload does not match feature type"); return; }
        if (d instanceof Integer id && !shops.containsKey(id))
            error("SHOP_REFERENCE", at, "shop #" + id + " does not exist");
        if (d instanceof ShopInventory shop) shop(shop, at);
        if (d instanceof String text && text.isBlank()) error("TEXT", at, "text is required");
        if (d instanceof InnData inn && inn.costPerHp() < 0) error("RANGE", at, "inn cost cannot be negative");
        if (d instanceof GuildData guild && (guild.trainingCostBase() < 0 || guild.trainingCostScale() < 0 || guild.maxLevel() < 0))
            error("RANGE", at, "guild costs and level cap cannot be negative");
        if (d instanceof BankData bank && (bank.depositFeePercent() < 0 || bank.depositFeePercent() > 100
                || bank.withdrawalFeePercent() < 0 || bank.withdrawalFeePercent() > 100))
            error("RANGE", at, "bank fees must be 0–100 percent");
        if (d instanceof LockData lock) {
            if (lock.requiredItemId() == 0 && lock.requiredFlag() == null) error("LOCK", at, "choose a key item or an unlock flag");
            if (lock.requiredItemId() != 0) item(lock.requiredItemId(), at);
        }
        if (d instanceof TrapData trap && (trap.damageMin() < 0 || trap.damageMax() < trap.damageMin() || trap.detectSkill() < 0))
            error("RANGE", at, "trap damage range or detection threshold is invalid");
        if (f.featureType() == PlacedFeatureType.CHEST && f.consumedFlag() == null)
            error("CHEST_COMPLETION", at, "choose a completion flag for one-shot treasure");
        if ((f.featureType() == PlacedFeatureType.CHEST || f.featureType() == PlacedFeatureType.TRAP)
                && f.requiredFlag() != null && f.requiredFlag() == f.consumedFlag())
            error("FEATURE_FLAGS", at, "activation and completion must use different flags");
        if (f.featureType() == PlacedFeatureType.CHEST || f.featureType() == PlacedFeatureType.TRAP) {
            var set = tileSet(source.getTileSetId());
            var tile = set == null ? null : set.getEntry(source.getTile(f.x(), f.y()));
            if (tile != null && !tile.passable()) error("TRIGGER_TILE", at, "chests and traps must be on passable tiles");
        }
        if (d instanceof LootTable loot) {
            if (loot.getGoldMin() < 0 || loot.getGoldMax() < loot.getGoldMin() || loot.getRollCount() < 0 || loot.getRollCount() > LootTable.MAX_ROLLS)
                error("RANGE", at, "treasure gold range or roll count is invalid");
            long totalWeight = 0;
            for (var e : loot.getGuaranteed()) { item(e.itemId(), at); if (e.quantity() <= 0) error("QUANTITY", at, "loot quantity must be positive"); }
            for (var e : loot.getRandomPool()) {
                item(e.itemId(), at); totalWeight += e.weight();
                if (e.quantity() <= 0 || e.weight() <= 0) error("QUANTITY", at, "random loot quantity and weight must be positive");
            }
            if (totalWeight > Integer.MAX_VALUE) error("RANGE", at, "total loot weight is too large");
        }
        if (d instanceof ExitData exit && exit.returnPosition() != null
                && (exit.returnPosition().x() < 0 || exit.returnPosition().y() < 0))
            error("ARRIVAL", at, "world return coordinates cannot be negative");
        if (d instanceof MapLinkData link) {
            var target = maps.get(link.targetMapId());
            if (target == null) error("MAP_REFERENCE", at, "destination map #" + link.targetMapId() + " does not exist");
            else {
                var entrance = target.getEntrance(link.entranceId());
                if (link.entranceId() == null || link.entranceId().isBlank() || entrance == null)
                    error("ENTRANCE_REFERENCE", at, "entrance '" + link.entranceId() + "' does not exist in map #" + target.getId());
                else if (!MapEntrances.isClear(target, entrance.position()))
                    error("ARRIVAL", at, "destination entrance '" + link.entranceId() + "' is blocked");
            }
        }
        if (d instanceof StairsData stairs) {
            var target = stairs.targetMapId() == 0 ? source : maps.get(stairs.targetMapId());
            if (target == null) { error("MAP_REFERENCE", at, "destination map #" + stairs.targetMapId() + " does not exist"); return; }
            if (stairs.targetFloor() != 0) error("FLOOR", at, "authored maps use separate map IDs; destination floor must be 0");
            WorldPosition p = stairs.targetPosition();
            if (p == null || !target.inBounds(p.x(), p.y())) error("ARRIVAL", at, "arrival must be inside destination map #" + target.getId());
            else {
                var tiles = tileSet(target.getTileSetId());
                var tile = tiles == null ? null : tiles.getEntry(target.getTile(p.x(), p.y()));
                if (tile == null || !tile.passable() || target.getNpcAt(p.x(), p.y()) != null
                        || (target.getFeatureAt(p.x(), p.y()) != null && target.getFeatureAt(p.x(), p.y()).featureType() == PlacedFeatureType.LOCKED_DOOR))
                    error("ARRIVAL", at, "arrival tile is blocked in destination map #" + target.getId());
            }
        }
    }
}
