// phantasia-core/src/main/java/com/phantasia/core/world/TileEvent.java
package com.phantasia.core.world;

/**
 * A player-triggered interactive event bound to a tile.
 * Unlike encounters, these require a deliberate player action
 * and present a choice before any consequence fires.
 *
 * Examples:
 *   - Altar: "Destroy it?" -> YES triggers Great Beast combat + reward
 *                          -> NO  triggers nothing
 *   - Sealed door: "Force it open?" -> YES costs HP, reveals passage
 *   - Ancient chest: "Open it?" -> YES rolls trap + loot
 */
public class TileEvent {

    public enum EventType {
        ALTAR,          // Destroy for reward — powerful guardian fight
        SEALED_DOOR,
        ANCIENT_CHEST,
        INSCRIPTION,    // Lore only, no choice required
        TRAP
    }

    public final EventType type;
    public final String    description;   // Shown to player on tile entry
    public final String    prompt;        // The yes/no question
    public final EventOutcome yesOutcome;
    public final EventOutcome noOutcome;
    private boolean resolved;             // Has this event been completed?

    public TileEvent(EventType type, String description, String prompt,
                     EventOutcome yesOutcome, EventOutcome noOutcome) {
        this.type        = type;
        this.description = description;
        this.prompt      = prompt;
        this.yesOutcome  = yesOutcome;
        this.noOutcome   = noOutcome;
        this.resolved    = false;
    }

    public boolean isResolved()  { return resolved; }
    public void    resolve()     { this.resolved = true; }
}