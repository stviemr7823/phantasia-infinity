// phantasia-core/src/main/java/com/phantasia/core/world/ServiceData.java
package com.phantasia.core.world;

import com.phantasia.core.data.QuestFlag;
import com.phantasia.core.world.WorldPosition;

/**
 * Service data records carried by {@link PlacedFeature} instances.
 *
 * Each record type corresponds to a {@link PlacedFeatureType} and holds
 * the authored data that drives the interaction. These are simple data
 * carriers — the interaction logic lives in the frontend (j2d, JME, etc.).
 *
 * All records are immutable. The editor constructs them; the runtime reads them.
 */
public final class ServiceData {

    private ServiceData() {} // namespace only

    // -------------------------------------------------------------------------
    // Inn
    // -------------------------------------------------------------------------

    /**
     * Data for INN_COUNTER features.
     *
     * @param costPerHp     gold cost per HP restored (party-wide)
     * @param greeting      text shown when the inn is opened (nullable)
     * @param restoredDead  true if the inn also revives dead party members
     */
    public record InnData(
            int     costPerHp,
            String  greeting,
            boolean restoredDead
    ) {
        /** Default inn: 10gp per HP, no resurrection. */
        public static InnData standard() {
            return new InnData(10, "Welcome, travelers. Rest well.", false);
        }
    }

    // -------------------------------------------------------------------------
    // Guild
    // -------------------------------------------------------------------------

    /**
     * Data for GUILD_COUNTER features.
     *
     * @param trainingCostBase  base gold cost for one level of training
     * @param trainingCostScale multiplier per current level (cost = base + level * scale)
     * @param maxLevel          highest level this guild can train to (0 = no cap)
     */
    public record GuildData(
            int trainingCostBase,
            int trainingCostScale,
            int maxLevel
    ) {
        public static GuildData standard() {
            return new GuildData(100, 50, 0);
        }
    }

    // -------------------------------------------------------------------------
    // Bank
    // -------------------------------------------------------------------------

    /**
     * Data for BANK_COUNTER features.
     *
     * @param depositFeePercent   percentage fee on deposits (0 = free)
     * @param withdrawalFeePercent percentage fee on withdrawals (0 = free)
     */
    public record BankData(
            int depositFeePercent,
            int withdrawalFeePercent
    ) {
        public static BankData standard() {
            return new BankData(0, 0);
        }
    }

    // -------------------------------------------------------------------------
    // Locked door
    // -------------------------------------------------------------------------

    /**
     * Data for LOCKED_DOOR features.
     *
     * A locked door is impassable until the party has the required key item
     * OR the required quest flag is set. Once unlocked, the door becomes
     * passable permanently (the consumedFlag on PlacedFeature tracks this).
     *
     * @param requiredItemId  item ID needed to unlock (0 = no item required)
     * @param requiredFlag    quest flag that unlocks the door (null = item-only)
     * @param lockedMessage   text shown when the player bumps the locked door
     */
    public record LockData(
            int       requiredItemId,
            QuestFlag requiredFlag,
            String    lockedMessage
    ) {
        /** Door locked by item only. */
        public static LockData byItem(int itemId, String message) {
            return new LockData(itemId, null, message);
        }

        /** Door locked by quest flag only. */
        public static LockData byFlag(QuestFlag flag, String message) {
            return new LockData(0, flag, message);
        }
    }

    // -------------------------------------------------------------------------
    // Stairs link
    // -------------------------------------------------------------------------

    /**
     * Data for STAIRS_LINK features.
     *
     * Links this stair tile to another floor (or another interior map entirely).
     * For standard dungeon floor progression, targetMapId is the same dungeon;
     * targetFloor is the floor index; targetPosition is the arrival tile.
     *
     * @param targetMapId    interior map ID to transition to (0 = same map)
     * @param targetFloor    floor index within the target map
     * @param targetPosition arrival tile coordinates on the target floor
     */
    public record StairsData(
            int           targetMapId,
            int           targetFloor,
            WorldPosition targetPosition
    ) {
        /** Simple floor link within the same dungeon. */
        public static StairsData toFloor(int floor, int x, int y) {
            return new StairsData(0, floor, new WorldPosition(x, y));
        }
    }

    // -------------------------------------------------------------------------
    // Exit (return to overworld)
    // -------------------------------------------------------------------------

    /**
     * Data for EXIT features.
     *
     * When the player steps on an exit tile, they return to the overworld
     * at the specified position. If returnPosition is null, the party returns
     * to wherever they entered from (stored on GameSession).
     *
     * @param returnPosition  overworld position to return to (null = entry point)
     */
    public record ExitData(
            WorldPosition returnPosition
    ) {
        /** Exit that returns the party to their entry point. */
        public static ExitData toEntryPoint() {
            return new ExitData(null);
        }
    }

    // -------------------------------------------------------------------------
    // Trap
    // -------------------------------------------------------------------------

    /**
     * Data for TRAP features.
     *
     * @param damageMin     minimum HP damage to each party member
     * @param damageMax     maximum HP damage to each party member
     * @param message       text shown when the trap triggers
     * @param detectSkill   DEX threshold to detect and avoid (0 = undetectable)
     */
    public record TrapData(
            int    damageMin,
            int    damageMax,
            String message,
            int    detectSkill
    ) {
        public static TrapData standard() {
            return new TrapData(3, 10, "A hidden trap springs!", 0);
        }
    }
}