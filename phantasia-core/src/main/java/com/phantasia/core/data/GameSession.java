// phantasia-core/src/main/java/com/phantasia/core/data/GameSession.java
package com.phantasia.core.data;

import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.world.DungeonFloor;
import com.phantasia.core.world.WorldClock;
import com.phantasia.core.world.WorldPosition;

import java.util.ArrayList;
import java.util.List;

/**
 * The primary data container for a single playthrough.
 *
 * <p>GameSession holds everything that defines the current state of a game
 * in progress: the party roster, the party's pooled gold (via PartyLedger),
 * the world position, the 64-bit quest flag bitfield, and the world clock.</p>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>Created by {@link #freshStart()} for a quick-start with a default party.</li>
 *   <li>Created by {@link SaveManager#newGame(List, WorldPosition)} after character creation.</li>
 *   <li>Restored by {@link SaveManager#load(String)} from a save file.</li>
 *   <li>Mutated during play (position, gold, quest flags, party roster).</li>
 *   <li>Serialized by {@link SaveManager#save(GameSession, String)} on exit.</li>
 * </ul>
 *
 * <h3>Quest flags</h3>
 * Stored as a 64-bit long, supporting up to 64 named flags from the
 * {@link QuestFlag} enum. All bit operations use {@code 1L} (long literal)
 * to ensure correct shifting for bits 32–63.
 *
 * <h3>Thread safety</h3>
 * Not thread-safe. All mutations happen on the game loop thread.
 */
public class GameSession {

    private final List<PlayerCharacter> party;
    private final PartyLedger           ledger;
    private WorldPosition               position;
    private long                        questFlags;
    private final WorldClock            worldClock;

    // Runtime-only state (not serialized in the save file)
    private DungeonFloor currentDungeonFloor;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Primary constructor — used by the static factory methods and
     * {@link SaveManager#newGame(List, WorldPosition)}.
     * World clock starts at the default time (07:00).
     */
    public GameSession(List<PlayerCharacter> party,
                       PartyLedger           ledger,
                       WorldPosition         position,
                       long                  questFlags) {
        this.party      = new ArrayList<>(party);
        this.ledger     = ledger;
        this.position   = position;
        this.questFlags = questFlags;
        this.worldClock = new WorldClock();  // default: 07:00 (just after dawn)
    }

    /**
     * Restore constructor — used exclusively by {@link SaveManager#load(String)}.
     * Restores the world clock to the time at which the game was saved.
     *
     * @param savedClockTime normalised time-of-day (0.0–1.0) from the save file
     */
    GameSession(List<PlayerCharacter> party,
                PartyLedger           ledger,
                WorldPosition         position,
                long                  questFlags,
                float                 savedClockTime) {
        this.party      = new ArrayList<>(party);
        this.ledger     = ledger;
        this.position   = position;
        this.questFlags = questFlags;
        this.worldClock = new WorldClock(savedClockTime);
    }

    // -------------------------------------------------------------------------
    // Static factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a brand-new session using {@link EncounterFactory}'s default party.
     *
     * <p>Used by the JME and libGDX frontends, and any context that needs a
     * working session without going through a character creation screen.</p>
     */
    public static GameSession freshStart() {
        List<PlayerCharacter> newParty = EncounterFactory.generateParty();
        return new GameSession(newParty, PartyLedger.newGame(),
                new WorldPosition(0, 0), 0L);
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

    /** Alias for {@link #getWorldPosition()} — preserved for older callers. */
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
    // World clock
    // -------------------------------------------------------------------------

    /**
     * Returns the world clock.
     * Update it each game-loop tick; read it for sky colour and weather biasing.
     */
    public WorldClock getWorldClock() { return worldClock; }

    // -------------------------------------------------------------------------
    // Dungeon state (runtime only — not serialized)
    // -------------------------------------------------------------------------

    /** Returns the current dungeon floor, or {@code null} if not in a dungeon. */
    public DungeonFloor getDungeonFloor() { return currentDungeonFloor; }

    /** Sets the current dungeon floor ({@code null} when leaving a dungeon). */
    public void setCurrentDungeonFloor(DungeonFloor floor) {
        this.currentDungeonFloor = floor;
    }
}
