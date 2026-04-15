// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/PlaceholderScreen.java
package com.phantasia.j2d.screen;

import com.phantasia.j2d.engine.GameCanvas;
import com.phantasia.j2d.engine.GameLoop;
import com.phantasia.j2d.engine.Screen;
import com.phantasia.j2d.render.ParchmentRenderer;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Phase 2 validation screen.
 *
 * <p>Draws a centered parchment panel with the game title and a live
 * FPS/UPS counter. If this renders correctly at fullscreen with smooth
 * updates, the entire j2d stage pipeline is verified:</p>
 *
 * <ul>
 *   <li>GameCanvas fullscreen + BufferStrategy ✓</li>
 *   <li>GameLoop fixed timestep + render ✓</li>
 *   <li>ScreenManager routing ✓</li>
 *   <li>InputRouter delivery ✓</li>
 *   <li>ParchmentRenderer visual language ✓</li>
 *   <li>ResourceCache font loading ✓</li>
 * </ul>
 *
 * <p>Press ESC to quit. Press any other key to see the input
 * confirmation flash.</p>
 */
public class PlaceholderScreen implements Screen {

    private final GameLoop gameLoop;

    // Input flash state
    private String lastKeyName = "";
    private float  flashTimer  = 0f;

    // Subtle animation
    private float elapsed = 0f;

    public PlaceholderScreen(GameLoop gameLoop) {
        this.gameLoop = gameLoop;
    }

    @Override
    public void onEnter(Object transitionData) {
        System.out.println("[PlaceholderScreen] Active — Phase 2 stage verified.");
    }

    @Override
    public void update(float deltaSeconds) {
        elapsed += deltaSeconds;
        if (flashTimer > 0) flashTimer -= deltaSeconds;
    }

    @Override
    public void render(Graphics2D g) {
        int W = GameCanvas.DESIGN_WIDTH;
        int H = GameCanvas.DESIGN_HEIGHT;

        // Void background
        g.setColor(ParchmentRenderer.VOID);
        g.fillRect(0, 0, W, H);

        // Central parchment panel
        int pw = 700, ph = 400;
        int px = (W - pw) / 2, py = (H - ph) / 2;
        ParchmentRenderer.drawPanel(g, px, py, pw, ph);

        // Title
        ParchmentRenderer.drawTextCentered(g,
                "PHANTASIA: INFINITY",
                px, py + 80, pw,
                ParchmentRenderer.TextStyle.HEADER, ParchmentRenderer.GOLD);

        // Subtitle
        ParchmentRenderer.drawTextCentered(g,
                "j2d Stage — Phase 2 Verified",
                px, py + 130, pw,
                ParchmentRenderer.TextStyle.BODY, ParchmentRenderer.TEXT);

        // Divider
        ParchmentRenderer.drawDivider(g, px + 40, py + 160, pw - 80);

        // FPS / UPS counter
        String perf = String.format("FPS: %d   UPS: %d", gameLoop.getFps(), gameLoop.getUps());
        ParchmentRenderer.drawTextCentered(g,
                perf, px, py + 210, pw,
                ParchmentRenderer.TextStyle.DATA, ParchmentRenderer.GOLD_BRIGHT);

        // Elapsed time (proves update loop is ticking)
        String time = String.format("Elapsed: %.1f s", elapsed);
        ParchmentRenderer.drawTextCentered(g,
                time, px, py + 250, pw,
                ParchmentRenderer.TextStyle.DATA, ParchmentRenderer.TEXT);

        // Input flash
        if (flashTimer > 0 && !lastKeyName.isEmpty()) {
            float alpha = Math.min(1f, flashTimer / 0.3f);
            ParchmentRenderer.drawTextCentered(g,
                    "Key: " + lastKeyName,
                    px, py + 310, pw,
                    ParchmentRenderer.TextStyle.BODY,
                    new Color(0xE8, 0xC9, 0x6A, (int)(255 * alpha)));
        }

        // Instructions
        ParchmentRenderer.drawTextCentered(g,
                "Press ESC to quit  •  Press any key to test input",
                px, py + 370, pw,
                ParchmentRenderer.TextStyle.LABEL, ParchmentRenderer.BORDER_STR);
    }

    @Override
    public void onExit() {
        System.out.println("[PlaceholderScreen] Exiting.");
    }

    @Override
    public void onKeyPressed(KeyEvent e) {
        lastKeyName = KeyEvent.getKeyText(e.getKeyCode());
        flashTimer  = 1.0f;
    }

    @Override
    public void onMouseClicked(MouseEvent e) {
        lastKeyName = "Mouse(" + e.getX() + "," + e.getY() + ")";
        flashTimer  = 1.0f;
    }
}