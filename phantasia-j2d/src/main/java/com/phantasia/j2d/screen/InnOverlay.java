// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/InnOverlay.java
package com.phantasia.j2d.screen;

import com.phantasia.core.data.GameSession;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.model.Stat;
import com.phantasia.j2d.engine.Screen;
import com.phantasia.j2d.engine.ScreenManager;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.phantasia.j2d.render.ParchmentRenderer.*;

/**
 * Inn overlay — pushed over WorldRoamScreen when the player interacts
 * with an INNKEEPER NPC.
 *
 * <h3>Service</h3>
 * Restores every living party member to full HP and MP for a flat gold fee.
 * Dead members are excluded from both the cost and the restoration (resurrection
 * is a separate, not-yet-implemented service).
 *
 * <h3>Cost formula</h3>
 * <pre>
 *   cost = max(10,  sum over living members of
 *                   (MAX_HP - HP) * 2  +  (MAX_MAGIC - MP))
 * </pre>
 * A fully-healthy party still pays the minimum 10 gp — the innkeeper
 * provides a bed and a meal regardless.
 *
 * <h3>Keys</h3>
 * ENTER — pay and rest (if affordable) · ESC — leave
 */
public class InnOverlay implements Screen {

    // ─────────────────────────────────────────────────────────────────────
    // Transition data
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @param innName  display name shown as the panel title
     * @param session  the live game session (ledger + party)
     */
    public record InnData(String innName, GameSession session) {}

    // ─────────────────────────────────────────────────────────────────────
    // Layout constants (1920×1080)
    // ─────────────────────────────────────────────────────────────────────

    private static final int PW = 860, PH = 600;
    private static final int PX = (1920 - PW) / 2;
    private static final int PY = (1080 - PH) / 2;

    private static final float ERROR_DURATION    = 1.8f;
    private static final float AUTO_CLOSE_DELAY  = 2.2f;

    // ─────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────

    private final ScreenManager screenManager;

    private InnData data;
    private int     restCost;
    private boolean justRested;
    private float   elapsed;
    private String  errorMessage;
    private float   errorTimer;

    // ─────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────

    public InnOverlay(ScreenManager screenManager) {
        this.screenManager = screenManager;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Screen lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onEnter(Object transitionData) {
        this.data         = (InnData) transitionData;
        this.restCost     = calculateCost(data.session().getParty());
        this.justRested   = false;
        this.elapsed      = 0f;
        this.errorMessage = null;
        this.errorTimer   = 0f;
    }

    @Override
    public void update(float dt) {
        elapsed += dt;
        if (errorTimer > 0f) {
            errorTimer -= dt;
            if (errorTimer <= 0f) errorMessage = null;
        }
        if (justRested && elapsed > AUTO_CLOSE_DELAY) {
            screenManager.pop();
        }
    }

    @Override
    public void render(Graphics2D g) {
        g.setColor(new Color(0x0D, 0x0D, 0x12, 200));
        g.fillRect(0, 0, 1920, 1080);

        drawPanel(g, PX, PY, PW, PH);

        // Title
        drawTextCentered(g, data.innName().toUpperCase(),
                PX, PY + 52, PW, TextStyle.HEADER, GOLD_BRIGHT);
        drawDivider(g, PX + 24, PY + 66, PW - 48);

        drawTextCentered(g,
                "\"Welcome, weary travellers. Rest your bones.\"",
                PX, PY + 104, PW, TextStyle.BODY, TEXT);

        renderPartyStatus(g);
        renderCostPanel(g);
        if (errorMessage != null && errorTimer > 0f) renderErrorFlash(g);
    }

    @Override public void onExit() {}

    // ─────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onKeyPressed(KeyEvent e) {
        if (justRested) return;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE -> screenManager.pop();
            case KeyEvent.VK_ENTER  -> attemptRest();
        }
    }

    @Override public void onMouseClicked(MouseEvent e) {}

    // ─────────────────────────────────────────────────────────────────────
    // Rest logic
    // ─────────────────────────────────────────────────────────────────────

    private void attemptRest() {
        if (!data.session().getLedger().spendGold(restCost)) {
            showError("Not enough gold!");
            return;
        }
        for (PlayerCharacter pc : data.session().getParty()) {
            if (!pc.isAlive()) continue;
            pc.setHp(pc.getStat(Stat.MAX_HP));
            pc.setStat(Stat.MAGIC_POWER, pc.getStat(Stat.MAX_MAGIC));
        }
        justRested = true;
        elapsed    = 0f;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Rendering helpers
    // ─────────────────────────────────────────────────────────────────────

    private void renderPartyStatus(Graphics2D g) {
        List<PlayerCharacter> party = data.session().getParty();
        int tx = PX + 40, ty = PY + 130;

        drawText(g, "MEMBER",   tx,       ty, TextStyle.LABEL, GOLD);
        drawText(g, "HP",       tx + 240, ty, TextStyle.LABEL, GOLD);
        drawText(g, "MP",       tx + 440, ty, TextStyle.LABEL, GOLD);
        drawText(g, "STATUS",   tx + 620, ty, TextStyle.LABEL, GOLD);
        drawDivider(g, tx, ty + 8, PW - 80);

        int ry = ty + 30;
        for (PlayerCharacter pc : party) {
            Color rowColor = pc.isAlive() ? TEXT : BORDER_STR;

            drawText(g, pc.getName(), tx, ry, TextStyle.BODY, rowColor);

            int hp = pc.getStat(Stat.HP), maxHp = pc.getStat(Stat.MAX_HP);
            int mp = pc.getStat(Stat.MAGIC_POWER), maxMp = pc.getStat(Stat.MAX_MAGIC);

            drawHealthBar(g, hp, maxHp, tx + 240, ry - 14, 150, 10);
            drawText(g, hp + "/" + maxHp, tx + 240, ry + 14,
                    TextStyle.DATA, rowColor);

            if (maxMp > 0) {
                drawManaBar(g, mp, maxMp, tx + 440, ry - 14, 150, 10);
                drawText(g, mp + "/" + maxMp, tx + 440, ry + 14,
                        TextStyle.DATA, rowColor);
            } else {
                drawText(g, "—", tx + 450, ry, TextStyle.DATA, BORDER_STR);
            }

            String status; Color statusColor;
            if (!pc.isAlive()) {
                status = "DEAD"; statusColor = DANGER;
            } else if (justRested) {
                status = "RESTED ✓"; statusColor = GOLD_BRIGHT;
            } else if (hp == maxHp && (maxMp == 0 || mp == maxMp)) {
                status = "Full"; statusColor = BORDER_STR;
            } else {
                status = "Wounded"; statusColor = HP_YELLOW;
            }
            drawText(g, status, tx + 620, ry, TextStyle.DATA, statusColor);
            ry += 44;
        }
    }

    private void renderCostPanel(Graphics2D g) {
        int divY = PY + PH - 140;
        drawDivider(g, PX + 24, divY, PW - 48);

        int gold = data.session().getLedger().getPartyGold();

        if (justRested) {
            drawTextCentered(g, "Rest well, adventurers.",
                    PX, divY + 44, PW, TextStyle.BODY, GOLD_BRIGHT);
            drawTextCentered(g, "Gold remaining:  " + gold,
                    PX, divY + 84, PW, TextStyle.DATA, GOLD);
        } else {
            Color costColor = gold >= restCost ? GOLD : DANGER;
            drawTextCentered(g,
                    "Cost to rest:  " + restCost + " gp     (You have: " + gold + " gp)",
                    PX, divY + 44, PW, TextStyle.BODY, costColor);
            String hint = gold >= restCost
                    ? "[ENTER] Rest    [ESC] Leave"
                    : "[ESC] Leave  (insufficient gold)";
            drawTextCentered(g, hint, PX, divY + 84, PW, TextStyle.DATA, BORDER_STR);
        }
    }

    private void renderErrorFlash(Graphics2D g) {
        float alpha = Math.max(0.1f, Math.min(1f,
                errorTimer / (ERROR_DURATION * 0.25f)));
        int ew = 460, eh = 48;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        drawPanel(g, (1920 - ew) / 2, PY + PH / 2 - 24, ew, eh, DANGER);
        drawTextCentered(g, errorMessage,
                (1920 - ew) / 2, PY + PH / 2 + 8, ew, TextStyle.LABEL, DANGER);
        g.setComposite(old);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Cost formula (package-visible for tests)
    // ─────────────────────────────────────────────────────────────────────

    static int calculateCost(List<PlayerCharacter> party) {
        int total = 0;
        for (PlayerCharacter pc : party) {
            if (!pc.isAlive()) continue;
            total += (pc.getStat(Stat.MAX_HP)    - pc.getStat(Stat.HP))    * 2;
            total +=  pc.getStat(Stat.MAX_MAGIC) - pc.getStat(Stat.MAGIC_POWER);
        }
        return Math.max(10, total);
    }

    private void showError(String msg) {
        errorMessage = msg;
        errorTimer   = ERROR_DURATION;
    }
}
