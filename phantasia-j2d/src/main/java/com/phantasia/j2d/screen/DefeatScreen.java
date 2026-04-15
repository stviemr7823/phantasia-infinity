// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/DefeatScreen.java
package com.phantasia.j2d.screen;

import com.phantasia.j2d.engine.GameCanvas;
import com.phantasia.j2d.engine.Screen;
import com.phantasia.j2d.render.ParchmentRenderer;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import static com.phantasia.j2d.render.ParchmentRenderer.*;

/**
 * Game over screen — displayed when all party members are killed.
 *
 * A solemn parchment panel with the defeat message. The player can
 * press any key to exit the game (or in the future, reload a save).
 *
 * The border uses DANGER red instead of gold to signal finality.
 */
public class DefeatScreen implements Screen {

    private final Runnable onQuit;
    private float elapsed;

    /**
     * @param onQuit called when the player acknowledges defeat (e.g. gameLoop.stop())
     */
    public DefeatScreen(Runnable onQuit) {
        this.onQuit = onQuit;
    }

    @Override
    public void onEnter(Object transitionData) {
        elapsed = 0f;
    }

    @Override
    public void update(float dt) {
        elapsed += dt;
    }

    @Override
    public void render(Graphics2D g) {
        int W = GameCanvas.DESIGN_WIDTH;
        int H = GameCanvas.DESIGN_HEIGHT;

        // Deep void background
        g.setColor(VOID);
        g.fillRect(0, 0, W, H);

        // Parchment panel with danger-red border
        int pw = 580, ph = 300;
        int px = (W - pw) / 2, py = (H - ph) / 2;
        drawPanel(g, px, py, pw, ph, DANGER);

        // Skull decoration
        drawTextCentered(g, "\u2620",
                px, py + 60, pw, TextStyle.HEADER, DANGER);

        // Title
        drawTextCentered(g, "YOUR PARTY HAS FALLEN",
                px, py + 110, pw, TextStyle.HEADER, DANGER);

        drawDivider(g, px + 80, py + 130, pw - 160);

        // Epitaph
        drawTextCentered(g, "The darkness claims another band of adventurers.",
                px, py + 170, pw, TextStyle.BODY, TEXT);
        drawTextCentered(g, "Their names will be forgotten by all but the wind.",
                px, py + 200, pw, TextStyle.BODY, new Color(0x8A7F6E));

        // Prompt (appears after 2 seconds)
        if (elapsed > 2f) {
            float alpha = Math.min(1f, (elapsed - 2f) * 1.5f);
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            drawTextCentered(g, "Press any key to return to the void",
                    px, py + ph - 35, pw, TextStyle.DATA, BORDER_STR);
            g.setComposite(old);
        }
    }

    @Override
    public void onKeyPressed(KeyEvent e) {
        if (elapsed > 1.5f && onQuit != null) {
            onQuit.run();
        }
    }

    @Override public void onMouseClicked(MouseEvent e) { onKeyPressed(null); }
    @Override public void onExit() {}
}
