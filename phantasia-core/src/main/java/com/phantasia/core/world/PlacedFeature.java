// phantasia-core/src/main/java/com/phantasia/core/world/PlacedFeature.java
package com.phantasia.core.world;

import com.phantasia.core.data.QuestFlag;

/**
 * An interactive object placed on an interior map grid.
 *
 * PlacedFeature represents the counters, chests, locked doors, stairs,
 * and other interactive elements that give an interior map its gameplay.
 * Each feature has a position, a type determining its behavior, an
 * optional service data payload, and optional quest flag gating.
 *
 * SERVICE DATA:
 *   The serviceData field carries type-specific payload. It is intentionally
 *   typed as Object to allow different feature types to carry different
 *   data structures without a complex sealed hierarchy. The caller casts
 *   based on featureType:
 *
 *     SHOP_COUNTER  → ShopInventory
 *     INN_COUNTER   → InnData
 *     GUILD_COUNTER → GuildData
 *     BANK_COUNTER  → BankData
 *     CHEST         → LootTable
 *     LOCKED_DOOR   → LockData
 *     STAIRS_LINK   → StairsData
 *     EXIT          → ExitData
 *     ALTAR, SIGN   → String (flavour text)
 *
 * QUEST GATING:
 *   requiredFlag — feature only active/visible after this flag is set.
 *   consumedFlag — set this flag after the player interacts (one-shot).
 *   Both are nullable (no gating / no consumption).
 *
 * @param x             grid position
 * @param y             grid position
 * @param featureType   determines interaction behavior
 * @param serviceData   type-specific payload (nullable for simple features)
 * @param requiredFlag  gate: only active if this flag is set (nullable)
 * @param consumedFlag  one-shot: set after interaction (nullable)
 * @param assetId       visual override — null uses default from featureType
 */
public record PlacedFeature(
        int                 x,
        int                 y,
        PlacedFeatureType   featureType,
        Object              serviceData,
        QuestFlag           requiredFlag,
        QuestFlag           consumedFlag,
        String              assetId
) {

    /** Convenience constructor for ungated features with no visual override. */
    public PlacedFeature(int x, int y, PlacedFeatureType type, Object data) {
        this(x, y, type, data, null, null, null);
    }

    /** Convenience constructor for simple features with no data or gating. */
    public PlacedFeature(int x, int y, PlacedFeatureType type) {
        this(x, y, type, null, null, null, null);
    }

    /**
     * Returns true if this feature is active given the current flag state.
     * @param flagChecker typically session::hasFlag
     */
    public boolean isActive(java.util.function.Predicate<QuestFlag> flagChecker) {
        return requiredFlag == null || flagChecker.test(requiredFlag);
    }
}
