// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/VictoryScreen.java
package com.phantasia.j2d.screen;

import com.phantasia.core.model.*;
import com.phantasia.j2d.engine.GameCanvas;
import com.phantasia.j2d.engine.GameState;
import com.phantasia.j2d.engine.Screen;
import com.phantasia.j2d.engine.ScreenManager;
import com.phantasia.j2d.render.ParchmentRenderer;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.phantasia.j2d.render.ParchmentRenderer.*;

/**
 * Post-combat victory screen — displays XP earned, gold found, and
 * per-character HP status after the battle.
 *
 * Transition data: {@link CombatExecutionScreen.ExecutionData}
 * Exit: any key → fade back to WorldRoamScreen
 */
public class VictoryScreen implements Screen {

    private final ScreenManager screenManager;

    private List<PlayerCharacter> party;
    private List<Monster>         enemies;
    private int totalXP;
    private int totalGold;
    private float elapsed;

    public VictoryScreen(ScreenManager screenManager) {
        this.screenManager = screenManager;
    }

    @Override
    public void onEnter(Object transitionData) {
        var data = (CombatExecutionScreen.ExecutionData) transitionData;
        this.party   = data.party();
        this.enemies = data.enemies();
        this.elapsed = 0f;

        // Tally XP and gold from defeated enemies
        totalXP   = enemies.stream().mapToInt(Entity::getExperience).sum();
        totalGold = enemies.stream().mapToInt(Monster::getTreasure).sum();
    }

    @Override
    public void update(float dt) {
        elapsed += dt;
    }

    @Override
    public void render(Graphics2D g) {
        int W = GameCanvas.DESIGN_WIDTH;
        int H = GameCanvas.DESIGN_HEIGHT;

        g.setColor(VOID);
        g.fillRect(0, 0, W, H);

        // Main panel
        int pw = 700, ph = 520;
        int px = (W - pw) / 2, py = (H - ph) / 2;
        drawPanel(g, px, py, pw, ph);

        // Title
        drawTextCentered(g, "VICTORY", px, py + 55, pw, TextStyle.HEADER, GOLD_BRIGHT);
        drawDivider(g, px + 60, py + 72, pw - 120);

        // XP and Gold summary
        drawTextCentered(g, "Experience earned: " + totalXP + " XP",
                px, py + 110, pw, TextStyle.BODY, TEXT);
        drawTextCentered(g, "Gold found: " + totalGold,
                px, py + 140, pw, TextStyle.BODY, GOLD);

        drawDivider(g, px + 60, py + 165, pw - 120);

        // Party status
        drawText(g, "PARTY STATUS", px + 30, py + 200, TextStyle.LABEL, GOLD);

        int rowY = py + 220;
        for (PlayerCharacter pc : party) {
            boolean alive = pc.isAlive();
            Color nameColor = alive ? TEXT : DANGER;
            String status = alive
                    ? pc.getHp() + " / " + pc.getStat(Stat.MAX_HP) + " HP"
                    : "FALLEN";

            drawText(g, pc.getName(), px + 40, rowY + 18, TextStyle.BODY, nameColor);

            // Job label
            String job = Job.fromValue(pc.getJob()).name();
            drawText(g, "Lv" + pc.getLevel() + " " + job,
                    px + 200, rowY + 18, TextStyle.DATA, new Color(0x8A7F6E));

            // HP bar
            if (alive) {
                drawHealthBar(g, pc.getHp(), pc.getStat(Stat.MAX_HP),
                        px + 380, rowY + 8, 180, 10);
                drawText(g, status, px + 575, rowY + 18, TextStyle.DATA, TEXT);
            } else {
                drawText(g, status, px + 380, rowY + 18, TextStyle.DATA, DANGER);
            }

            rowY += 36;
        }

        // Continue prompt (appears after 1 second)
        if (elapsed > 1f) {
            float alpha = Math.min(1f, (elapsed - 1f) * 2f);
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            drawTextCentered(g, "Press any key to continue",
                    px, py + ph - 40, pw, TextStyle.DATA, BORDER_STR);
            g.setComposite(old);
        }
    }

    @Override
    public void onKeyPressed(KeyEvent e) {
        if (elapsed > 0.5f) {
            screenManager.fadeTo(GameState.WORLD_ROAM, null);
        }
    }

    @Override public void onMouseClicked(MouseEvent e) { onKeyPressed(null); }
    @Override public void onExit() {}
}
