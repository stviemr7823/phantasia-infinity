// phantasia-core/src/main/java/com/phantasia/core/world/PlacedFeatureType.java
package com.phantasia.core.world;

/**
 * Types of interactive features that can be placed on interior maps.
 *
 * Each type determines the interaction model (bump or step trigger)
 * and the expected service data payload type on {@link PlacedFeature}.
 */
public enum PlacedFeatureType {

    /** Shop counter — opens ShopInventory on bump. */
    SHOP_COUNTER,

    /** Inn counter — opens rest/heal menu on bump. Data: InnData. */
    INN_COUNTER,

    /** Guild counter — opens training menu on bump. Data: GuildData. */
    GUILD_COUNTER,

    /** Bank counter — opens deposit/withdraw menu on bump. Data: BankData. */
    BANK_COUNTER,

    /** Treasure chest — grants loot on step. Data: LootTable. One-shot. */
    CHEST,

    /** Locked door — impassable until key item or quest flag. Data: LockData. */
    LOCKED_DOOR,

    /** Stairs linking to another floor or map. Data: StairsData. */
    STAIRS_LINK,

    /** Exit back to the overworld. Data: ExitData. */
    EXIT,

    /** Interactive altar — displays text or grants a blessing. Data: String. */
    ALTAR,

    /** Readable sign or notice board. Data: String (the sign text). */
    SIGN,

    /** Trap tile — deals damage on step. Data: TrapData. */
    TRAP
}
