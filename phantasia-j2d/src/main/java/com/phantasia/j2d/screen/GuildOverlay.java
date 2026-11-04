// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/GuildOverlay.java
package com.phantasia.j2d.screen;

import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.town.GuildService;
import com.phantasia.core.logic.ExperienceTable;
import com.phantasia.core.model.Job;
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
 * Guild overlay — pushed over WorldRoamScreen when the player interacts
 * with a TRAINER NPC.
 *
 * <h3>Service</h3>
 * Lets the player train one party member at a time to their next level,
 * provided they have accumulated sufficient XP and can pay the gold cost.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Left panel shows the party roster with XP progress and eligibility.</li>
 *   <li>Player navigates UP/DOWN to select a member.</li>
 *   <li>Right panel shows the selected member's full stat block and training cost.</li>
 *   <li>ENTER trains the selected member (if eligible and affordable).</li>
 *   <li>A training result message appears briefly; the panel refreshes.</li>
 * </ol>
 *
 * <h3>Eligibility rules (enforced by GuildService)</h3>
 * <ul>
 *   <li>Character must have enough XP to reach the next level.</li>
 *   <li>Character must not be undead.</li>
 *   <li>Character must not already be at level cap (20).</li>
 * </ul>
 *
 * <h3>Keys</h3>
 * UP/DOWN — select member · ENTER — train · ESC — leave
 */
public class GuildOverlay implements Screen {

    // ─────────────────────────────────────────────────────────────────────
    // Transition data
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @param guildName  display name for the panel title
     * @param session    the live game session
     */
    public record GuildData(String guildName, GameSession session) {}

    // ─────────────────────────────────────────────────────────────────────
    // Layout (1920×1080)
    // ─────────────────────────────────────────────────────────────────────

    private static final int OX = 180, OY = 140, OW = 1560, OH = 800;

    // Left roster panel
    private static final int LEFT_X = OX + 24;
    private static final int LEFT_W = 600;

    // Right detail panel
    private static final int RIGHT_X = OX + 668;
    private static final int RIGHT_W = OW - 668 - 24;

    private static final int CONTENT_Y  = OY + 90;
    private static final int ROW_H      = 56;

    private static final float RESULT_DISPLAY = 2.0f;
    private static final float ERROR_DURATION = 1.8f;

    // ─────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────

    private final ScreenManager screenManager;

    private GuildData   data;
    private int         selectedIndex;
    private String      resultMessage;
    private float       resultTimer;
    private String      errorMessage;
    private float       errorTimer;

    // ─────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────

    public GuildOverlay(ScreenManager screenManager) {
        this.screenManager = screenManager;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Screen lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onEnter(Object transitionData) {
        this.data          = (GuildData) transitionData;
        this.selectedIndex = 0;
        this.resultMessage = null;
        this.resultTimer   = 0f;
        this.errorMessage  = null;
        this.errorTimer    = 0f;
    }

    @Override
    public void update(float dt) {
        if (resultTimer > 0f) {
            resultTimer -= dt;
            if (resultTimer <= 0f) resultMessage = null;
        }
        if (errorTimer > 0f) {
            errorTimer -= dt;
            if (errorTimer <= 0f) errorMessage = null;
        }
    }

    @Override
    public void render(Graphics2D g) {
        g.setColor(new Color(0x0D, 0x0D, 0x12, 200));
        g.fillRect(0, 0, 1920, 1080);

        drawPanel(g, OX, OY, OW, OH);

        // Title
        drawTextCentered(g, data.guildName().toUpperCase(),
                OX, OY + 52, OW, TextStyle.HEADER, GOLD_BRIGHT);
        drawDivider(g, OX + 24, OY + 66, OW - 48);

        // Vertical divider
        g.setColor(BORDER_STR);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(OX + LEFT_W + 44, CONTENT_Y,
                OX + LEFT_W + 44, OY + OH - 24);

        renderRoster(g);
        renderDetail(g);
        renderBottomBar(g);

        if (resultMessage != null && resultTimer > 0f) renderResultFlash(g);
        if (errorMessage  != null && errorTimer  > 0f) renderErrorFlash(g);
    }

    @Override public void onExit() {}

    // ─────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onKeyPressed(KeyEvent e) {
        List<PlayerCharacter> party = data.session().getParty();
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE -> screenManager.pop();
            case KeyEvent.VK_UP    -> {
                if (selectedIndex > 0) selectedIndex--;
            }
            case KeyEvent.VK_DOWN  -> {
                if (selectedIndex < party.size() - 1) selectedIndex++;
            }
            case KeyEvent.VK_ENTER -> attemptTrain();
        }
    }

    @Override public void onMouseClicked(MouseEvent e) {}

    // ─────────────────────────────────────────────────────────────────────
    // Train logic
    // ─────────────────────────────────────────────────────────────────────

    private void attemptTrain() {
        List<PlayerCharacter> party = data.session().getParty();
        if (party.isEmpty()) return;

        PlayerCharacter pc = party.get(
                Math.min(selectedIndex, party.size() - 1));

        if (!GuildService.INSTANCE.canTrain(pc)) {
            if (pc.isUndead()) {
                showError("The undead cannot be trained here.");
            } else if (pc.getStat(Stat.LEVEL) >= ExperienceTable.LEVEL_MAX) {
                showError(pc.getName() + " has reached the level cap.");
            } else {
                showError(pc.getName() + " needs more experience first.");
            }
            return;
        }

        int cost = GuildService.INSTANCE.trainingCost(pc);
        if (!data.session().getLedger().spendGold(cost)) {
            showError("Not enough gold!  Need " + cost + " gp.");
            return;
        }

        GuildService.TrainingResult result = GuildService.INSTANCE.train(pc);

        // Social-class bonus gold
        if (result.bonusGold() > 0) {
            data.session().getLedger().addGold(result.bonusGold());
        }

        showResult(result.describe());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Rendering helpers
    // ─────────────────────────────────────────────────────────────────────

    private void renderRoster(Graphics2D g) {
        List<PlayerCharacter> party = data.session().getParty();
        int headerY = CONTENT_Y + 24;

        drawText(g, "PARTY", LEFT_X, headerY, TextStyle.LABEL, GOLD);
        drawDivider(g, LEFT_X, headerY + 8, LEFT_W);

        int ry = headerY + 32;
        for (int i = 0; i < party.size(); i++) {
            PlayerCharacter pc      = party.get(i);
            boolean         sel     = (i == selectedIndex);
            boolean         canTrain = GuildService.INSTANCE.canTrain(pc);

            if (sel) {
                g.setColor(new Color(0xBF, 0xA3, 0x4C, 35));
                g.fillRoundRect(LEFT_X - 4, ry - 22, LEFT_W + 4, ROW_H - 4, 6, 6);
                g.setColor(new Color(0xBF, 0xA3, 0x4C, 90));
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(LEFT_X - 4, ry - 22, LEFT_W + 4, ROW_H - 4, 6, 6);
            }

            Color nameColor = sel ? GOLD_BRIGHT : (canTrain ? GOLD : TEXT);
            drawText(g, pc.getName(), LEFT_X + 4, ry, TextStyle.BODY, nameColor);

            String jobLabel = "Lv" + pc.getLevel() + " "
                    + Job.fromValue(pc.getJob()).name();
            drawText(g, jobLabel, LEFT_X + 4, ry + 18,
                    TextStyle.DATA, BORDER_STR);

            // Ready-to-train badge
            if (canTrain) {
                drawText(g, "★ READY", LEFT_X + LEFT_W - 90, ry,
                        TextStyle.DATA, GOLD_BRIGHT);
            } else if (pc.isUndead()) {
                drawText(g, "UNDEAD", LEFT_X + LEFT_W - 80, ry,
                        TextStyle.DATA, BORDER_STR);
            } else if (pc.getStat(Stat.LEVEL) >= ExperienceTable.LEVEL_MAX) {
                drawText(g, "MAX LVL", LEFT_X + LEFT_W - 86, ry,
                        TextStyle.DATA, BORDER_STR);
            } else {
                int xpNeeded = ExperienceTable.xpToNextLevel(pc);
                drawText(g, xpNeeded + " XP", LEFT_X + LEFT_W - 70, ry,
                        TextStyle.DATA, BORDER_STR);
            }
            ry += ROW_H;
        }
    }

    private void renderDetail(Graphics2D g) {
        List<PlayerCharacter> party = data.session().getParty();
        if (party.isEmpty()) return;

        PlayerCharacter pc = party.get(Math.min(selectedIndex, party.size() - 1));

        int headerY = CONTENT_Y + 24;
        drawText(g, pc.getName().toUpperCase(), RIGHT_X, headerY,
                TextStyle.LABEL, GOLD);
        drawDivider(g, RIGHT_X, headerY + 8, RIGHT_W);

        int sy = headerY + 32;
        int col2 = RIGHT_X + 200;

        // Vitals
        drawText(g, "Level",   RIGHT_X, sy,      TextStyle.DATA, BORDER_STR);
        drawText(g, String.valueOf(pc.getLevel()), col2, sy, TextStyle.DATA, TEXT);
        drawText(g, "Job",     RIGHT_X, sy + 26, TextStyle.DATA, BORDER_STR);
        drawText(g, Job.fromValue(pc.getJob()).name(), col2, sy + 26,
                TextStyle.DATA, TEXT);

        sy += 64;
        drawDivider(g, RIGHT_X, sy, RIGHT_W);
        sy += 20;

        // HP / MP
        int hp  = pc.getStat(Stat.HP),    maxHp  = pc.getStat(Stat.MAX_HP);
        int mp  = pc.getStat(Stat.MAGIC_POWER), maxMp  = pc.getStat(Stat.MAX_MAGIC);
        drawText(g, "HP", RIGHT_X, sy, TextStyle.DATA, BORDER_STR);
        drawText(g, hp + " / " + maxHp, col2, sy, TextStyle.DATA, TEXT);
        if (maxMp > 0) {
            drawText(g, "MP", RIGHT_X, sy + 26, TextStyle.DATA, BORDER_STR);
            drawText(g, mp + " / " + maxMp, col2, sy + 26, TextStyle.DATA, MAGIC);
        }

        sy += 64;
        drawDivider(g, RIGHT_X, sy, RIGHT_W);
        sy += 20;

        // XP progress
        int xpCurrent = pc.getStat(Stat.XP);
        int xpNeeded  = ExperienceTable.xpToNextLevel(pc);
        drawText(g, "XP",      RIGHT_X, sy,      TextStyle.DATA, BORDER_STR);
        drawText(g, String.valueOf(xpCurrent), col2, sy, TextStyle.DATA, TEXT);
        drawText(g, "To next", RIGHT_X, sy + 26, TextStyle.DATA, BORDER_STR);
        String nextLabel = xpNeeded == 0 ? "Ready!" : String.valueOf(xpNeeded);
        Color  nextColor = xpNeeded == 0 ? GOLD_BRIGHT : TEXT;
        drawText(g, nextLabel, col2, sy + 26, TextStyle.DATA, nextColor);

        sy += 64;
        drawDivider(g, RIGHT_X, sy, RIGHT_W);
        sy += 20;

        // Training cost
        boolean canTrain = GuildService.INSTANCE.canTrain(pc);
        if (canTrain) {
            int cost = GuildService.INSTANCE.trainingCost(pc);
            int gold = data.session().getLedger().getPartyGold();
            Color costColor = gold >= cost ? GOLD : DANGER;
            drawText(g, "Train cost", RIGHT_X, sy, TextStyle.DATA, BORDER_STR);
            drawText(g, cost + " gp", col2, sy, TextStyle.DATA, costColor);
            drawText(g, "Gold",       RIGHT_X, sy + 26, TextStyle.DATA, BORDER_STR);
            drawText(g, gold + " gp", col2, sy + 26, TextStyle.DATA, GOLD);
        } else {
            String reason = pc.isUndead() ? "Cannot train (undead)"
                    : pc.getStat(Stat.LEVEL) >= ExperienceTable.LEVEL_MAX
                      ? "Level cap reached"
                      : "Insufficient XP";
            drawText(g, reason, RIGHT_X, sy, TextStyle.DATA, BORDER_STR);
        }
    }

    private void renderBottomBar(Graphics2D g) {
        int barY = OY + OH - 46;
        drawDivider(g, OX + 24, barY - 10, OW - 48);
        drawTextCentered(g,
                "[↑/↓] Select Member    [ENTER] Train    [ESC] Leave",
                OX, barY + 8, OW, TextStyle.DATA, BORDER_STR);
    }

    private void renderResultFlash(Graphics2D g) {
        float alpha = Math.max(0.2f, Math.min(1f,
                resultTimer > RESULT_DISPLAY * 0.75f
                        ? (RESULT_DISPLAY - resultTimer) / (RESULT_DISPLAY * 0.25f)
                        : resultTimer / (RESULT_DISPLAY * 0.25f)));
        int rw = 700, rh = 60;
        int rx = (1920 - rw) / 2, ry = OY + OH / 2 - 30;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        drawPanel(g, rx, ry, rw, rh, GOLD);
        drawTextCentered(g, resultMessage, rx, ry + 38, rw, TextStyle.BODY, GOLD_BRIGHT);
        g.setComposite(old);
    }

    private void renderErrorFlash(Graphics2D g) {
        float alpha = Math.max(0.1f, Math.min(1f,
                errorTimer / (ERROR_DURATION * 0.25f)));
        int ew = 560, eh = 52;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        drawPanel(g, (1920 - ew) / 2, OY + OH / 2 - 26, ew, eh, DANGER);
        drawTextCentered(g, errorMessage,
                (1920 - ew) / 2, OY + OH / 2 + 10, ew, TextStyle.LABEL, DANGER);
        g.setComposite(old);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private void showResult(String msg) {
        resultMessage = msg;
        resultTimer   = RESULT_DISPLAY;
    }

    private void showError(String msg) {
        errorMessage = msg;
        errorTimer   = ERROR_DURATION;
    }
}
