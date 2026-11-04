// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/BankOverlay.java
package com.phantasia.j2d.screen;

import com.phantasia.core.data.GameSession;
import com.phantasia.j2d.engine.Screen;
import com.phantasia.j2d.engine.ScreenManager;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import static com.phantasia.j2d.render.ParchmentRenderer.*;

/**
 * Bank overlay — pushed over WorldRoamScreen when the player interacts
 * with a BANKER NPC.
 *
 * <h3>Service</h3>
 * Deposits gold from the party's field purse into the bank (safe storage),
 * or withdraws gold from the bank back into the purse.
 *
 * <h3>Why a bank?</h3>
 * Faithful to Phantasie III: gold carried in the field can be lost on death
 * (future mechanic). The bank keeps gold permanently safe between sessions.
 *
 * <h3>Interaction model</h3>
 * <ul>
 *   <li>D — switch to Deposit mode</li>
 *   <li>W — switch to Withdraw mode</li>
 *   <li>0-9 — append digit to amount input</li>
 *   <li>BACKSPACE — delete last digit</li>
 *   <li>ENTER — confirm the transaction</li>
 *   <li>ESC — leave</li>
 * </ul>
 *
 * <h3>Safety</h3>
 * Amounts are clamped to what's available in each account.
 * {@link com.phantasia.core.data.PartyLedger#deposit} and
 * {@link com.phantasia.core.data.PartyLedger#withdraw} return false on
 * insufficient funds — the overlay checks this and shows an error.
 */
public class BankOverlay implements Screen {

    // ─────────────────────────────────────────────────────────────────────
    // Transition data
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @param bankName  display name for the panel title
     * @param session   the live game session
     */
    public record BankData(String bankName, GameSession session) {}

    // ─────────────────────────────────────────────────────────────────────
    // Layout (1920×1080)
    // ─────────────────────────────────────────────────────────────────────

    private static final int PW = 760, PH = 540;
    private static final int PX = (1920 - PW) / 2;
    private static final int PY = (1080 - PH) / 2;

    private static final int MAX_INPUT_DIGITS = 6;    // up to 999,999 gp
    private static final float ERROR_DURATION = 1.8f;
    private static final float SUCCESS_DELAY  = 1.4f;

    // ─────────────────────────────────────────────────────────────────────
    // Mode
    // ─────────────────────────────────────────────────────────────────────

    private enum Mode { DEPOSIT, WITHDRAW }

    // ─────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────

    private final ScreenManager screenManager;

    private BankData data;
    private Mode     mode;
    private String   amountInput;       // digit string being built
    private String   errorMessage;
    private float    errorTimer;
    private String   successMessage;
    private float    successTimer;

    // ─────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────

    public BankOverlay(ScreenManager screenManager) {
        this.screenManager = screenManager;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Screen lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onEnter(Object transitionData) {
        this.data           = (BankData) transitionData;
        this.mode           = Mode.DEPOSIT;
        this.amountInput    = "";
        this.errorMessage   = null;
        this.errorTimer     = 0f;
        this.successMessage = null;
        this.successTimer   = 0f;
    }

    @Override
    public void update(float dt) {
        if (errorTimer > 0f) {
            errorTimer -= dt;
            if (errorTimer <= 0f) errorMessage = null;
        }
        if (successTimer > 0f) {
            successTimer -= dt;
            if (successTimer <= 0f) successMessage = null;
        }
    }

    @Override
    public void render(Graphics2D g) {
        g.setColor(new Color(0x0D, 0x0D, 0x12, 200));
        g.fillRect(0, 0, 1920, 1080);

        drawPanel(g, PX, PY, PW, PH);

        // Title
        drawTextCentered(g, data.bankName().toUpperCase(),
                PX, PY + 52, PW, TextStyle.HEADER, GOLD_BRIGHT);
        drawDivider(g, PX + 24, PY + 66, PW - 48);

        renderBalances(g);
        renderModeSelector(g);
        renderInputField(g);
        renderBottomBar(g);

        if (successMessage != null && successTimer > 0f) renderSuccessFlash(g);
        if (errorMessage   != null && errorTimer   > 0f) renderErrorFlash(g);
    }

    @Override public void onExit() {}

    // ─────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onKeyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        switch (code) {
            case KeyEvent.VK_ESCAPE -> screenManager.pop();
            case KeyEvent.VK_D      -> { mode = Mode.DEPOSIT;  amountInput = ""; }
            case KeyEvent.VK_W      -> { mode = Mode.WITHDRAW; amountInput = ""; }
            case KeyEvent.VK_ENTER  -> confirmTransaction();
            case KeyEvent.VK_BACK_SPACE -> {
                if (!amountInput.isEmpty())
                    amountInput = amountInput.substring(0, amountInput.length() - 1);
            }
            default -> {
                // Digit input (both numrow and numpad)
                char c = e.getKeyChar();
                if (Character.isDigit(c) && amountInput.length() < MAX_INPUT_DIGITS) {
                    // Suppress leading zeros
                    if (!(amountInput.isEmpty() && c == '0'))
                        amountInput += c;
                }
            }
        }
    }

    @Override public void onMouseClicked(MouseEvent e) {}

    // ─────────────────────────────────────────────────────────────────────
    // Transaction logic
    // ─────────────────────────────────────────────────────────────────────

    private void confirmTransaction() {
        if (amountInput.isEmpty()) {
            showError("Enter an amount first.");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountInput);
        } catch (NumberFormatException ex) {
            showError("Invalid amount.");
            amountInput = "";
            return;
        }

        if (amount <= 0) {
            showError("Amount must be greater than zero.");
            return;
        }

        boolean ok = switch (mode) {
            case DEPOSIT  -> data.session().getLedger().deposit(amount);
            case WITHDRAW -> data.session().getLedger().withdraw(amount);
        };

        if (!ok) {
            showError(mode == Mode.DEPOSIT
                    ? "Not enough gold in your purse!"
                    : "Not enough gold in the bank!");
        } else {
            showSuccess(mode == Mode.DEPOSIT
                    ? "Deposited " + amount + " gp."
                    : "Withdrew " + amount + " gp.");
            amountInput = "";
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Rendering helpers
    // ─────────────────────────────────────────────────────────────────────

    private void renderBalances(Graphics2D g) {
        int bx = PX + 60, by = PY + 104;
        int col2 = PX + 300;

        drawText(g, "Gold on hand:", bx, by,      TextStyle.BODY, TEXT);
        drawText(g, data.session().getLedger().getPartyGold() + " gp",
                col2, by, TextStyle.DATA, GOLD_BRIGHT);

        drawText(g, "Bank balance:", bx, by + 34, TextStyle.BODY, TEXT);
        drawText(g, data.session().getLedger().getBankGold() + " gp",
                col2, by + 34, TextStyle.DATA, GOLD_BRIGHT);

        drawDivider(g, PX + 24, by + 60, PW - 48);
    }

    private void renderModeSelector(Graphics2D g) {
        int mx = PX + 60, my = PY + 210;

        // DEPOSIT tab
        Color depColor  = (mode == Mode.DEPOSIT)  ? GOLD_BRIGHT : BORDER_STR;
        Color withColor = (mode == Mode.WITHDRAW) ? GOLD_BRIGHT : BORDER_STR;

        if (mode == Mode.DEPOSIT) {
            g.setColor(new Color(0xBF, 0xA3, 0x4C, 40));
            g.fillRoundRect(mx - 8, my - 22, 200, 38, 6, 6);
        }
        drawText(g, "[D] DEPOSIT",  mx, my, TextStyle.LABEL, depColor);

        if (mode == Mode.WITHDRAW) {
            g.setColor(new Color(0xBF, 0xA3, 0x4C, 40));
            g.fillRoundRect(mx + 240 - 8, my - 22, 220, 38, 6, 6);
        }
        drawText(g, "[W] WITHDRAW", mx + 240, my, TextStyle.LABEL, withColor);
    }

    private void renderInputField(Graphics2D g) {
        int fx = PX + 60, fy = PY + 290;

        String label = mode == Mode.DEPOSIT
                ? "Amount to deposit:"
                : "Amount to withdraw:";
        drawText(g, label, fx, fy, TextStyle.BODY, TEXT);

        // Input box
        int bx = fx, by = fy + 14, bw = 300, bh = 48;
        g.setColor(new Color(0x1A, 0x15, 0x10));
        g.fillRoundRect(bx, by, bw, bh, 8, 8);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(bx, by, bw, bh, 8, 8);

        // Displayed text with blinking cursor
        String display = amountInput.isEmpty() ? "" : amountInput;
        long blink = System.currentTimeMillis() / 500;
        String cursor = (blink % 2 == 0) ? "|" : " ";
        drawText(g, display + cursor, bx + 12, by + 32,
                TextStyle.DATA, GOLD_BRIGHT);

        // Max available hint
        int maxAvailable = mode == Mode.DEPOSIT
                ? data.session().getLedger().getPartyGold()
                : data.session().getLedger().getBankGold();
        drawText(g, "Available: " + maxAvailable + " gp",
                bx + bw + 20, by + 28, TextStyle.DATA, BORDER_STR);
    }

    private void renderBottomBar(Graphics2D g) {
        int barY = PY + PH - 46;
        drawDivider(g, PX + 24, barY - 10, PW - 48);
        drawTextCentered(g,
                "[0-9] Enter amount    [BKSP] Delete    [ENTER] Confirm    [ESC] Leave",
                PX, barY + 8, PW, TextStyle.DATA, BORDER_STR);
    }

    private void renderSuccessFlash(Graphics2D g) {
        float alpha = Math.max(0.2f, Math.min(1f,
                successTimer < SUCCESS_DELAY * 0.3f
                        ? successTimer / (SUCCESS_DELAY * 0.3f) : 1f));
        int sw = 500, sh = 48;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        drawPanel(g, (1920 - sw) / 2, PY + PH / 2 - 24, sw, sh, GOLD);
        drawTextCentered(g, successMessage,
                (1920 - sw) / 2, PY + PH / 2 + 10, sw, TextStyle.LABEL, GOLD_BRIGHT);
        g.setComposite(old);
    }

    private void renderErrorFlash(Graphics2D g) {
        float alpha = Math.max(0.1f, Math.min(1f,
                errorTimer / (ERROR_DURATION * 0.25f)));
        int ew = 520, eh = 48;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        drawPanel(g, (1920 - ew) / 2, PY + PH / 2 - 24, ew, eh, DANGER);
        drawTextCentered(g, errorMessage,
                (1920 - ew) / 2, PY + PH / 2 + 10, ew, TextStyle.LABEL, DANGER);
        g.setComposite(old);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private void showError(String msg) {
        errorMessage = msg;
        errorTimer   = ERROR_DURATION;
    }

    private void showSuccess(String msg) {
        successMessage = msg;
        successTimer   = SUCCESS_DELAY;
    }
}
