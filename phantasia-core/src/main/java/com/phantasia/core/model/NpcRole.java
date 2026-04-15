// phantasia-core/src/main/java/com/phantasia/core/model/NpcRole.java
package com.phantasia.core.model;

/**
 * Defines the interaction type for an NPC.
 *
 * The role determines what happens when the player bumps into the NPC's
 * position on an interior map:
 *
 *   MERCHANT    — opens the linked ShopInventory
 *   QUEST_GIVER — delivers dialogue, may set quest flags or give items
 *   TRAINER     — guild training services (level up, class change)
 *   INFORMANT   — delivers dialogue only, no services or side effects
 *   INNKEEPER   — rest/heal services with cost calculation
 *   BANKER      — deposit/withdraw gold
 *   GUARD       — blocks passage; dialogue changes after quest flag is set
 *   BOSS        — triggers a scripted combat encounter on interaction
 */
public enum NpcRole {

    MERCHANT,
    QUEST_GIVER,
    TRAINER,
    INFORMANT,
    INNKEEPER,
    BANKER,
    GUARD,
    BOSS;

    /**
     * Returns true if this role delivers dialogue as its primary interaction.
     * Most roles show dialogue before or instead of opening a service menu.
     */
    public boolean hasDialogue() {
        return this != BOSS;  // bosses go straight to combat
    }

    /**
     * Returns true if this role opens a service menu after dialogue.
     */
    public boolean hasServiceMenu() {
        return switch (this) {
            case MERCHANT, TRAINER, INNKEEPER, BANKER -> true;
            default -> false;
        };
    }
}