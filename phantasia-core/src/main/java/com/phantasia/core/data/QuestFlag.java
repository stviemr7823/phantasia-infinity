// phantasia-core/src/main/java/com/phantasia/core/data/QuestFlag.java
package com.phantasia.core.data;

/**
 * Named flags for tracking story progression, NPC states, and world changes.
 *
 * FLAGS ARE PERSISTED — never reorder, rename, or remove existing enum
 * entries. The bit index is baked into save files and reordering
 * would corrupt loaded games.
 *
 * CAPACITY:
 *   64 flags stored in a single long. GameSession, SaveManager, and all
 *   flag-checking code uses long (64-bit) arithmetic. If the game grows
 *   beyond 64 flags, add a second long word and adjust SaveManager.
 *
 * LAYOUT:
 *   Bits  0–15  Story milestones (main quest progression)
 *   Bits 16–31  Town, NPC, and dialogue states
 *   Bits 32–47  Dungeon discovery and completion
 *   Bits 48–55  Item and loot tracking
 *   Bits 56–63  Miscellaneous / debug
 */
public enum QuestFlag {

    // -----------------------------------------------------------------------
    // Story milestones  (bits 0–15)
    // -----------------------------------------------------------------------

    /** The party has spoken to the king for the first time. */
    MET_KING            (0),

    /** The party has found the first dungeon entrance. */
    FOUND_DUNGEON_1     (1),

    /** The lich in dungeon 1 has been defeated. */
    LICH_DEFEATED       (2),

    /** The party has retrieved the Dark Key (item 170). */
    HAS_DARK_KEY        (3),

    /** The party has retrieved the Light Key (item 172). */
    HAS_LIGHT_KEY       (4),

    /** The final boss chamber has been unlocked. */
    FINAL_DOOR_OPEN     (5),

    /** The Wand of Nikademus (item 180) has been obtained. */
    HAS_WAND_NIKADEMUS  (6),

    /** The Dragon King has been defeated — credits-worthy. */
    DRAGON_KING_SLAIN   (7),

    STORY_FLAG_8        (8),
    STORY_FLAG_9        (9),
    STORY_FLAG_10       (10),
    STORY_FLAG_11       (11),
    STORY_FLAG_12       (12),
    STORY_FLAG_13       (13),
    STORY_FLAG_14       (14),
    STORY_FLAG_15       (15),

    // -----------------------------------------------------------------------
    // Town & NPC states  (bits 16–31)
    // -----------------------------------------------------------------------

    /** Town healer has been visited at least once. */
    VISITED_HEALER      (16),

    /** Guild registration is complete — party can level up at the guild. */
    GUILD_REGISTERED    (17),

    /** The sage in town 2 has shared the dungeon password. */
    SAGE_PASSWORD_KNOWN (18),

    NPC_FLAG_19         (19),
    NPC_FLAG_20         (20),
    NPC_FLAG_21         (21),
    NPC_FLAG_22         (22),
    NPC_FLAG_23         (23),
    NPC_FLAG_24         (24),
    NPC_FLAG_25         (25),
    NPC_FLAG_26         (26),
    NPC_FLAG_27         (27),
    NPC_FLAG_28         (28),
    NPC_FLAG_29         (29),
    NPC_FLAG_30         (30),
    NPC_FLAG_31         (31),

    // -----------------------------------------------------------------------
    // Dungeon discovery  (bits 32–47)
    // -----------------------------------------------------------------------

    DUNGEON_1_CLEARED   (32),
    DUNGEON_2_FOUND     (33),
    DUNGEON_2_CLEARED   (34),
    SECRET_PASSAGE_FOUND(35),

    DUNGEON_FLAG_36     (36),
    DUNGEON_FLAG_37     (37),
    DUNGEON_FLAG_38     (38),
    DUNGEON_FLAG_39     (39),
    DUNGEON_FLAG_40     (40),
    DUNGEON_FLAG_41     (41),
    DUNGEON_FLAG_42     (42),
    DUNGEON_FLAG_43     (43),
    DUNGEON_FLAG_44     (44),
    DUNGEON_FLAG_45     (45),
    DUNGEON_FLAG_46     (46),
    DUNGEON_FLAG_47     (47),

    // -----------------------------------------------------------------------
    // Item and loot tracking  (bits 48–55)
    // -----------------------------------------------------------------------

    /** Scroll fragment 1 has been found. */
    SCROLL_1_FOUND      (48),

    ITEM_FLAG_49        (49),
    ITEM_FLAG_50        (50),
    ITEM_FLAG_51        (51),
    ITEM_FLAG_52        (52),
    ITEM_FLAG_53        (53),
    ITEM_FLAG_54        (54),
    ITEM_FLAG_55        (55),

    // -----------------------------------------------------------------------
    // Miscellaneous / debug  (bits 56–63)
    // -----------------------------------------------------------------------

    /** Set once the intro cutscene has played so it never replays. */
    INTRO_PLAYED        (56),

    /** Debug / cheat flag — all areas unlocked. */
    DEV_UNLOCK_ALL      (57),

    MISC_FLAG_58        (58),
    MISC_FLAG_59        (59),
    MISC_FLAG_60        (60),
    MISC_FLAG_61        (61),
    MISC_FLAG_62        (62),
    MISC_FLAG_63        (63);

    // -----------------------------------------------------------------------

    /** The bit index this flag occupies within the 64-bit quest flags long. */
    public final int bit;

    QuestFlag(int bit) {
        if (bit < 0 || bit > 63)
            throw new IllegalArgumentException("QuestFlag bit must be 0–63: " + bit);
        this.bit = bit;
    }
}