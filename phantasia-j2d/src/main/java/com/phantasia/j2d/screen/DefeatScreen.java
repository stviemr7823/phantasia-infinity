// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/DefeatScreen.java
package com.phantasia.j2d.screen;

import com.phantasia.core.model.Job;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.j2d.engine.GameCanvas;
import com.phantasia.j2d.engine.Screen;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.stream.Collectors;

import static com.phantasia.j2d.render.ParchmentRenderer.*;

/**
 * Defeat screen — displayed when every party member is dead.
 *
 * <p>Shows each character by name with their job class so the player
 * has a moment of acknowledgement before the game ends. The enemies
 * responsible are also listed briefly — "slain by X and Y" adds
 * narrative weight to the loss.</p>
 *
 * <p>Transition data: {@link CombatExecutionScreen.ExecutionData}<br>
 * Exit: any key after 2 s returns a local battle to its retained area;
 * other battles call the {@code onQuit} runnable.</p>
 */
public class DefeatScreen implements Screen {

    private final Runnable onQuit;
    private final com.phantasia.j2d.engine.ScreenManager screenManager;

    private List<PlayerCharacter> party;
    private String                slainBy;
    private float                 elapsed;
    private com.phantasia.j2d.engine.BattleContinuation continuation;
    private boolean leaving;

    /**
     * @param onQuit called when the player acknowledges defeat
     *               (e.g. {@code gameLoop::stop})
     */
    public DefeatScreen(Runnable onQuit) { this(null, onQuit); }
    public DefeatScreen(com.phantasia.j2d.engine.ScreenManager screenManager, Runnable onQuit) {
        this.screenManager = screenManager; this.onQuit = onQuit;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onEnter(Object transitionData) {
        elapsed = 0f; continuation = null; leaving = false;

        if (transitionData instanceof CombatExecutionScreen.ExecutionData data) {
            this.party = data.party();
            this.continuation = data.continuation();

            // Build a short "slain by" line from the distinct enemy types
            List<String> names = data.enemies().stream()
                    .map(Monster::getName)
                    .distinct()
                    .limit(3)
                    .collect(Collectors.toList());
            slainBy = names.isEmpty() ? "unknown foes"
                    : String.join(", ", names);
        } else {
            this.party   = List.of();
            this.slainBy = "unknown foes";
        }
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
        int pw = 620;
        int ph = 200 + Math.max(1, party.size()) * 36 + 80;
        int px = (W - pw) / 2, py = (H - ph) / 2;
        drawPanel(g, px, py, pw, ph, DANGER);

        // ── Skull + title ─────────────────────────────────────────────────
        drawTextCentered(g, "\u2620",
                px, py + 56, pw, TextStyle.HEADER, DANGER);
        drawTextCentered(g, "YOUR PARTY HAS FALLEN",
                px, py + 106, pw, TextStyle.HEADER, DANGER);

        drawDivider(g, px + 80, py + 124, pw - 160);

        // ── Slain-by line ─────────────────────────────────────────────────
        drawTextCentered(g, "Slain by: " + slainBy,
                px, py + 158, pw, TextStyle.BODY, new Color(0xCC, 0x55, 0x55));

        drawDivider(g, px + 80, py + 174, pw - 160);

        // ── Fallen party members ──────────────────────────────────────────
        int rowY = py + 192;
        for (PlayerCharacter pc : party) {
            String jobStr = "Lv" + pc.getLevel() + " "
                    + Job.fromValue(pc.getJob()).name();

            // Name
            drawText(g, pc.getName(), px + 50, rowY + 18,
                    TextStyle.BODY, new Color(0x8A, 0x7F, 0x6E));

            // Job
            drawText(g, jobStr, px + 220, rowY + 18,
                    TextStyle.DATA, BORDER_STR);

            // Epitaph
            drawText(g, "— fallen",
                    px + 370, rowY + 18, TextStyle.DATA, DANGER);

            rowY += 36;
        }

        // ── Continue prompt ───────────────────────────────────────────────
        if (elapsed > 2f) {
            float alpha = Math.min(1f, (elapsed - 2f) * 1.5f);
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            drawTextCentered(g, continuation != null && continuation.isLocal() ? "Press any key to return to the area; Esc then opens pause" : "Press any key to return to the void",
                    px, py + ph - 32, pw, TextStyle.DATA, BORDER_STR);
            g.setComposite(old);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onKeyPressed(KeyEvent e) {
        if (elapsed > 1.5f && !leaving) {
            leaving = true;
            if (continuation != null && continuation.isLocal() && screenManager != null) continuation.returnDefeat(screenManager);
            else if (onQuit != null) onQuit.run();
        }
    }

    @Override
    public void onMouseClicked(MouseEvent e) { onKeyPressed(null); }
}
