// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/VictoryScreen.java
package com.phantasia.j2d.screen;

import com.phantasia.core.data.GameSession;
import com.phantasia.core.logic.ExperienceTable;
import com.phantasia.core.model.*;
import com.phantasia.j2d.engine.GameCanvas;
import com.phantasia.j2d.engine.GameState;
import com.phantasia.j2d.engine.Screen;
import com.phantasia.j2d.engine.ScreenManager;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.phantasia.j2d.render.ParchmentRenderer.*;

/**
 * Post-combat victory screen.
 *
 * <p>Displays XP earned, gold found, and per-character HP status. Characters
 * who have accumulated enough XP to train at the guild show a gold
 * {@code ★ READY} badge — a clear call-to-action to visit the guild.</p>
 *
 * <p>A shared battle receipt awards XP and gold once, including repeated
 * entry into {@link #onEnter(Object)}.</p>
 *
 * <p>Transition data: {@link CombatExecutionScreen.ExecutionData}<br>
 * Exit: any key after 0.5 s → fade to the retained battle return route</p>
 */
public class VictoryScreen implements Screen {

    private final ScreenManager screenManager;

    private List<PlayerCharacter> party;
    private List<Monster>         enemies;
    private int   totalXP;
    private int   totalGold;
    private float elapsed;
    private com.phantasia.j2d.engine.BattleContinuation continuation;
    private boolean leaving, goldPending;

    public VictoryScreen(ScreenManager screenManager) {
        this.screenManager = screenManager;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onEnter(Object transitionData) {
        var data = (CombatExecutionScreen.ExecutionData) transitionData;
        this.party   = data.party();
        this.enemies = data.enemies();
        this.elapsed = 0f;

        continuation = data.continuation(); leaving = false;
        var receipt = continuation.victory();
        totalXP = receipt.experience(); totalGold = receipt.gold(); goldPending = !receipt.goldClaimed();
    }

    @Override
    public void update(float dt) {
        elapsed += dt;
    }

    @Override
    public void onExit() {}

    // ─────────────────────────────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void render(Graphics2D g) {
        int W = GameCanvas.DESIGN_WIDTH;
        int H = GameCanvas.DESIGN_HEIGHT;

        g.setColor(VOID);
        g.fillRect(0, 0, W, H);

        // ── Main panel ────────────────────────────────────────────────────
        int pw = 720, ph = 80 + party.size() * 44 + 120;
        ph = Math.max(ph, 480);
        int px = (W - pw) / 2, py = (H - ph) / 2;
        drawPanel(g, px, py, pw, ph);

        // ── Title ─────────────────────────────────────────────────────────
        drawTextCentered(g, "VICTORY", px, py + 55, pw, TextStyle.HEADER, GOLD_BRIGHT);
        drawDivider(g, px + 60, py + 72, pw - 120);

        // ── XP and Gold summary ───────────────────────────────────────────
        drawTextCentered(g, "Experience earned:  " + totalXP + " XP",
                px, py + 108, pw, TextStyle.BODY, TEXT);
        drawTextCentered(g, "Gold found:  " + totalGold + (goldPending ? " (pending: purse limit)" : "") + " gp",
                px, py + 138, pw, TextStyle.BODY, GOLD);

        drawDivider(g, px + 60, py + 160, pw - 120);

        // ── Party status ──────────────────────────────────────────────────
        drawText(g, "PARTY STATUS", px + 30, py + 196, TextStyle.LABEL, GOLD);

        int rowY = py + 212;
        for (PlayerCharacter pc : party) {
            renderCharacterRow(g, pc, px, rowY, pw);
            rowY += 44;
        }

        // ── Continue prompt ───────────────────────────────────────────────
        if (elapsed > 1f) {
            float alpha = Math.min(1f, (elapsed - 1f) * 2f);
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            drawTextCentered(g, "Press any key to continue",
                    px, py + ph - 36, pw, TextStyle.DATA, BORDER_STR);
            g.setComposite(old);
        }
    }

    private void renderCharacterRow(Graphics2D g, PlayerCharacter pc,
                                    int px, int rowY, int pw) {
        boolean alive = pc.isAlive();
        boolean ready = alive && ExperienceTable.isReadyToLevelUp(pc);

        Color nameColor = alive ? TEXT : new Color(0x8A7F6E);

        // Name
        drawText(g, pc.getName(), px + 40, rowY + 20, TextStyle.BODY, nameColor);

        // Job + level
        String jobLabel = "Lv" + pc.getLevel() + " " + Job.fromValue(pc.getJob()).name();
        drawText(g, jobLabel, px + 200, rowY + 20, TextStyle.DATA, BORDER_STR);

        if (alive) {
            // HP bar
            int hp = pc.getHp(), maxHp = pc.getStat(Stat.MAX_HP);
            drawHealthBar(g, hp, maxHp, px + 370, rowY + 9, 160, 10);
            drawText(g, hp + "/" + maxHp + " HP",
                    px + 542, rowY + 20, TextStyle.DATA, TEXT);

            // ★ READY badge — the most important piece of new information
            if (ready) {
                int bx = px + pw - 120, by = rowY + 4;
                g.setColor(new Color(0x3A, 0x28, 0x00));
                g.fillRoundRect(bx, by, 108, 26, 4, 4);
                g.setColor(GOLD_BRIGHT);
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(bx, by, 108, 26, 4, 4);
                drawTextCentered(g, "\u2605 READY", bx, by + 18, 108,
                        TextStyle.DATA, GOLD_BRIGHT);
            }
        } else {
            drawText(g, "FALLEN",
                    px + 370, rowY + 20, TextStyle.DATA, DANGER);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onKeyPressed(KeyEvent e) {
        if (elapsed > 0.5f && !leaving) { leaving = true; continuation.returnVictory(screenManager); }
    }

    @Override
    public void onMouseClicked(MouseEvent e) { onKeyPressed(null); }
}
