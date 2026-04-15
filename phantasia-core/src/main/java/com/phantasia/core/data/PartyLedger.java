// phantasia-core/src/main/java/com/phantasia/core/data/PartyLedger.java
package com.phantasia.core.data;

import com.phantasia.core.logic.GameEvent;
import com.phantasia.core.logic.GameEventBus;

/**
 * Runtime owner of pooled party gold and bank balance.
 *
 * In Phantasie III, gold belongs to the party as a whole — not to
 * any individual character.  The bank lets the party deposit gold
 * for safe-keeping between expeditions.
 *
 * This class is the in-memory ledger that the game loop talks to.
 * It reads its initial values from GlobalLedger and writes back
 * through it on save, but it never touches the file system itself
 * during normal play — all arithmetic stays in RAM.
 *
 * EVENT BUS INTEGRATION:
 *   Every mutation that changes partyGold or bankGold fires a
 *   GameEvent onto GameEventBus.get() so that subscribers (HUDState,
 *   TownState, future shop/bank screens) update without polling.
 *
 *   addGold       → GameEvent.PartyGoldChanged  (positive delta)
 *   spendGold     → GameEvent.PartyGoldChanged  (negative delta, only on success)
 *   deposit       → GameEvent.PartyGoldChanged  (negative) + BankBalanceChanged (positive)
 *   withdraw      → GameEvent.PartyGoldChanged  (positive) + BankBalanceChanged (negative)
 *
 *   Events are not fired by fromRaw() or newGame() — those are construction
 *   paths, not in-game mutations, and firing at startup would pollute the
 *   log with spurious gold-change entries.
 *
 * THREAD SAFETY: not thread-safe — intended for single-threaded game loop use.
 */
public class PartyLedger {

    private int partyGold;
    private int bankGold;

    // -------------------------------------------------------------------------
    // Construction (private — use factory methods)
    // -------------------------------------------------------------------------

    private PartyLedger(int partyGold, int bankGold) {
        this.partyGold = partyGold;
        this.bankGold  = bankGold;
    }

    /** Creates a fresh ledger for a brand-new game (no file I/O, no bus events). */
    public static PartyLedger newGame() {
        return new PartyLedger(400, 0);   // 400 gp starting purse — faithful to original
    }

    /** Loads ledger values from an already-opened GlobalLedger (no bus events). */
    public static PartyLedger load(GlobalLedger gl) {
        return new PartyLedger(gl.getPartyGold(), gl.getBankGold());
    }

    /** Package-private construction for SaveManager.load() (no bus events). */
    static PartyLedger fromRaw(int partyGold, int bankGold) {
        return new PartyLedger(partyGold, bankGold);
    }

    // -------------------------------------------------------------------------
    // Persistence bridge
    // -------------------------------------------------------------------------

    /**
     * Pushes in-memory values back into the GlobalLedger so it can write them
     * to disk.  Does NOT call gl.saveGold() — the caller decides when to flush.
     */
    public void save(GlobalLedger gl) {
        int glGold = gl.getPartyGold();
        gl.addPartyGold(partyGold - glGold);

        int glBank = gl.getBankGold();
        gl.addBankGold(bankGold - glBank);
    }

    // -------------------------------------------------------------------------
    // Field gold — earned and spent during an expedition
    // -------------------------------------------------------------------------

    /** Returns the gold the party is currently carrying in the field. */
    public int getPartyGold() { return partyGold; }

    /**
     * Adds gold to the party purse and fires GameEvent.PartyGoldChanged.
     * Negative or zero amounts are silently ignored.
     */
    public void addGold(int amount) {
        if (amount <= 0) return;
        partyGold += amount;
        GameEventBus.get().fire(new GameEvent.PartyGoldChanged(partyGold, amount));
    }

    /**
     * Deducts gold from the party purse — shop purchases, inn fees, etc.
     * Fires GameEvent.PartyGoldChanged only if the spend succeeds.
     *
     * @return true if the party had enough gold and the spend succeeded;
     *         false if funds were insufficient (gold unchanged, no event fired)
     */
    public boolean spendGold(int cost) {
        if (cost < 0) throw new IllegalArgumentException("Cost cannot be negative.");
        if (partyGold < cost) return false;
        partyGold -= cost;
        GameEventBus.get().fire(new GameEvent.PartyGoldChanged(partyGold, -cost));
        return true;
    }

    // -------------------------------------------------------------------------
    // Bank — deposit and withdraw at town
    // -------------------------------------------------------------------------

    /** Returns the gold currently held in the bank. */
    public int getBankGold() { return bankGold; }

    /**
     * Moves gold from the field purse into the bank.
     * Fires PartyGoldChanged (negative delta) and BankBalanceChanged (positive).
     *
     * @return true if the deposit succeeded; false if insufficient field gold
     */
    public boolean deposit(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Deposit must be positive.");
        if (partyGold < amount) return false;
        partyGold -= amount;
        bankGold  += amount;
        GameEventBus.get().fire(new GameEvent.PartyGoldChanged(partyGold, -amount));
        GameEventBus.get().fire(new GameEvent.BankBalanceChanged(bankGold, amount));
        return true;
    }

    /**
     * Moves gold from the bank into the field purse.
     * Fires BankBalanceChanged (negative delta) and PartyGoldChanged (positive).
     *
     * @return true if the withdrawal succeeded; false if insufficient bank balance
     */
    public boolean withdraw(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Withdrawal must be positive.");
        if (bankGold < amount) return false;
        bankGold  -= amount;
        partyGold += amount;
        GameEventBus.get().fire(new GameEvent.BankBalanceChanged(bankGold, -amount));
        GameEventBus.get().fire(new GameEvent.PartyGoldChanged(partyGold, amount));
        return true;
    }

    // -------------------------------------------------------------------------
    // Display
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format("Party Gold: %d gp  |  Bank: %d gp  |  Total: %d gp",
                partyGold, bankGold, partyGold + bankGold);
    }
}