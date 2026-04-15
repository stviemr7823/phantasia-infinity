// phantasia-core/src/main/java/com/phantasia/core/data/GameSession.java
package com.phantasia.core.data;

import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.world.DungeonFloor;
import com.phantasia.core.world.WorldPosition;

import java.util.ArrayList;
import java.util.List;

/**
 * The primary data container for a single playthrough.
 *
 * GameSession holds everything that defines the current state of a game
 * in progress: the party roster, the party's pooled gold (via PartyLedger),
 * the world position, and the 64-bit quest flag bitfield.
 *
 * LIFECYCLE:
 *   - Created by SaveManager.newGame() for a fresh start, or by
 *     SaveManager.load() when restoring from a save file.
 *   - Mutated during play (position, gold, quest flags, party roster).
 *   - Serialized by SaveManager.save() on game exit.
 *
 * QUEST FLAGS:
 *   Stored as a 64-bit long, supporting up to 64 named flags from the
 *   {@link QuestFlag} enum. All bit operations use 1L (long literal)
 *   to ensure correct shifting for bits 32–63.
 *
 * THREAD SAFETY:
 *   Not thread-safe. All mutations happen on the game loop thread.
 */
public class GameSession {

    private final List<PlayerCharacter> party;
    private final PartyLedger           ledger;
    private WorldPosition               position;
    private long                        questFlags;

    // Runtime-only state (not serialized in the save file)
    private DungeonFloor                currentDungeonFloor;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Full constructor — used by SaveManager.load() and newGame().
     *
     * @param party      the party roster (mutable list, owned by this session)
     * @param ledger     the party's gold ledger
     * @param position   the current world map position
     * @param questFlags the 64-bit quest flag bitfield
     */
    public GameSession(List<PlayerCharacter> party,
                       PartyLedger           ledger,
                       WorldPosition         position,
                       long                  questFlags) {
        this.party      = party;
        this.ledger     = ledger;
        this.position   = position;
        this.questFlags = questFlags;
    }

    /**
     * Creates a brand-new GameSession with a fresh party and default values.
     * Delegates to EncounterFactory for the starting roster.
     */
    public static GameSession freshStart() {
        List<PlayerCharacter> newParty = EncounterFactory.generateParty();
        return new GameSession(newParty, PartyLedger.newGame(),
                new WorldPosition(0, 0), 0L);
    }

    /**
     * Creates a brand-new GameSession with an externally-built party.
     * Used by character creation and SaveManager.newGame().
     *
     * @param party     the newly-created party
     * @param startPos  the starting world position
     */
    public static GameSession newGame(List<PlayerCharacter> party,
                                      WorldPosition         startPos) {
        return new GameSession(party, PartyLedger.newGame(), startPos, 0L);
    }

    // -------------------------------------------------------------------------
    // Party
    // -------------------------------------------------------------------------

    /** Returns the party roster (mutable — add/remove during play). */
    public List<PlayerCharacter> getParty() { return party; }

    /** Returns the first alive party member, or the first member if all dead. */
    public PlayerCharacter getPartyLead() {
        return party.stream()
                .filter(PlayerCharacter::isAlive)
                .findFirst()
                .orElse(party.getFirst());
    }

    // -------------------------------------------------------------------------
    // Gold (via PartyLedger)
    // -------------------------------------------------------------------------

    /** Returns the party's gold ledger (field gold + bank gold). */
    public PartyLedger getLedger() { return ledger; }

    // -------------------------------------------------------------------------
    // World position
    // -------------------------------------------------------------------------

    /** Returns the party's current world map position. */
    public WorldPosition getWorldPosition() { return position; }

    /** Alias for getWorldPosition() — used by some older callers. */
    public WorldPosition getPosition() { return position; }

    /** Updates the party's world map position. */
    public void setPosition(WorldPosition pos) { this.position = pos; }

    // -------------------------------------------------------------------------
    // Quest flags (64-bit)
    // -------------------------------------------------------------------------

    /** Returns the raw 64-bit quest flag bitfield. */
    public long getQuestFlags() { return questFlags; }

    /** Returns true if the given quest flag is set. */
    public boolean hasFlag(QuestFlag flag) {
        return (questFlags & (1L << flag.bit)) != 0;
    }

    /** Sets the given quest flag. Idempotent. */
    public void setFlag(QuestFlag flag) {
        questFlags |= (1L << flag.bit);
    }

    /** Clears the given quest flag. Idempotent. */
    public void clearFlag(QuestFlag flag) {
        questFlags &= ~(1L << flag.bit);
    }

    // -------------------------------------------------------------------------
    // Dungeon state (runtime only — not serialized)
    // -------------------------------------------------------------------------

    /** Returns the current dungeon floor, or null if not in a dungeon. */
    public DungeonFloor getDungeonFloor() {
        return currentDungeonFloor;
    }

    /** Sets the current dungeon floor (null when leaving a dungeon). */
    public void setCurrentDungeonFloor(DungeonFloor floor) {
        this.currentDungeonFloor = floor;
    }
}