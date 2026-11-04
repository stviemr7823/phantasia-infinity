// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/PauseScreen.java
package com.phantasia.j2d.screen;

import com.phantasia.j2d.engine.GameLoop;
import com.phantasia.j2d.engine.Screen;
import com.phantasia.j2d.engine.ScreenManager;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import static com.phantasia.j2d.render.ParchmentRenderer.*;

/**
 * Pause screen — pushed on top of any active screen via
 * {@code ScreenManager.push(GameState.PAUSE, null)}.
 *
 * The underlying screen continues to render beneath the dim overlay.
 * It does not receive update() or input calls while paused.
 *
 * MENU OPTIONS:
 *   Resume         — pop this overlay, return to the underlying screen
 *   Quit to Desktop — stop the game loop (triggers auto-save via shutdown hook)
 *
 * KEYS:
 *   UP/DOWN — navigate   ENTER — confirm   ESC — resume
 */
public class PauseScreen implements Screen {

    private static final int PW = 440, PH = 320;
    private static final int PX = (1920 - PW) / 2;
    private static final int PY = (1080 - PH) / 2;
    private static final String[] OPTIONS = { "Resume", "Quit to Desktop" };

    private final ScreenManager screenManager;
    private final GameLoop      gameLoop;

    private int     selectedOption;
    private boolean confirmingQuit;
    private float   elapsed;

    public PauseScreen(ScreenManager screenManager, GameLoop gameLoop) {
        this.screenManager = screenManager;
        this.gameLoop      = gameLoop;
    }

    @Override
    public void onEnter(Object transitionData) {
        selectedOption = 0;
        confirmingQuit = false;
        elapsed        = 0f;
    }

    @Override
    public void update(float dt) { elapsed += dt; }

    @Override
    public void render(Graphics2D g) {
        g.setColor(new Color(0x0D, 0x0D, 0x12, 180));
        g.fillRect(0, 0, 1920, 1080);

        drawPanel(g, PX, PY, PW, PH);
        drawTextCentered(g, "PAUSED", PX, PY + 52, PW, TextStyle.HEADER, GOLD_BRIGHT);
        drawDivider(g, PX + 24, PY + 66, PW - 48);

        if (confirmingQuit) renderQuitConfirm(g);
        else                renderMenu(g);
    }

    @Override public void onExit() {}

    @Override
    public void onKeyPressed(KeyEvent e) {
        if (confirmingQuit) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_ENTER, KeyEvent.VK_Y -> gameLoop.stop();
                case KeyEvent.VK_ESCAPE, KeyEvent.VK_N -> confirmingQuit = false;
            }
            return;
        }
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE -> screenManager.pop();
            case KeyEvent.VK_UP    -> { if (selectedOption > 0) selectedOption--; }
            case KeyEvent.VK_DOWN  -> { if (selectedOption < OPTIONS.length - 1) selectedOption++; }
            case KeyEvent.VK_ENTER -> {
                if (selectedOption == 0) screenManager.pop();
                else                     confirmingQuit = true;
            }
        }
    }

    @Override public void onMouseClicked(MouseEvent e) {}

    private void renderMenu(Graphics2D g) {
        int startY = PY + 116, rowH = 60;
        for (int i = 0; i < OPTIONS.length; i++) {
            int ry = startY + i * rowH;
            boolean sel = (i == selectedOption);
            if (sel) {
                g.setColor(new Color(0xBF, 0xA3, 0x4C, 40));
                g.fillRoundRect(PX + 40, ry - 26, PW - 80, 46, 8, 8);
                g.setColor(new Color(0xBF, 0xA3, 0x4C, 100));
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(PX + 40, ry - 26, PW - 80, 46, 8, 8);
            }
            drawTextCentered(g, (sel ? "▶  " : "    ") + OPTIONS[i],
                    PX, ry, PW, TextStyle.BODY, sel ? GOLD_BRIGHT : TEXT);
        }
        drawDivider(g, PX + 24, PY + PH - 56, PW - 48);
        drawTextCentered(g, "[↑/↓] Navigate    [ENTER] Select    [ESC] Resume",
                PX, PY + PH - 28, PW, TextStyle.DATA, BORDER_STR);
    }

    private void renderQuitConfirm(Graphics2D g) {
        int cy = PY + 130;
        drawTextCentered(g, "Quit to desktop?", PX, cy, PW, TextStyle.BODY, TEXT);
        drawTextCentered(g, "(Progress will be auto-saved)",
                PX, cy + 30, PW, TextStyle.DATA, BORDER_STR);
        drawDivider(g, PX + 24, cy + 52, PW - 48);
        drawTextCentered(g, "[ENTER / Y]  Yes, quit",
                PX, cy + 88, PW, TextStyle.BODY, DANGER);
        drawTextCentered(g, "[ESC / N]  No, go back",
                PX, cy + 126, PW, TextStyle.BODY, TEXT);
    }
}
