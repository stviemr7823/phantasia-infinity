// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/EncounterSplashScreen.java
package com.phantasia.j2d.screen;

import com.phantasia.core.logic.EncounterCondition;
import com.phantasia.core.model.Monster;
import com.phantasia.j2d.engine.GameCanvas;
import com.phantasia.j2d.engine.GameState;
import com.phantasia.j2d.engine.Screen;
import com.phantasia.j2d.engine.ScreenManager;
import com.phantasia.j2d.render.ParchmentRenderer;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phase A of the two-screen combat system.
 *
 * Triggered when a boundary crossing produces an encounter roll.
 * A centered parchment panel expands from nothing (ease-out) displaying
 * the encounter announcement. After a configurable hold duration,
 * the screen fades out and transitions to CombatPlanningScreen.
 *
 * Transition data (onEnter): {@link EncounterData}
 */
public class EncounterSplashScreen implements Screen {

    /**
     * Data passed from the world roam screen when an encounter triggers.
     */
    public record EncounterData(
            List<Monster>      enemies,
            EncounterCondition condition,
            Object             combatContext  // passed through to CombatPlanningScreen
    ) {}

    // ─────────────────────────────────────────────────────────────────────
    // Timing
    // ─────────────────────────────────────────────────────────────────────

    private static final float EXPAND_DURATION = 0.35f;  // panel scale-in
    private static final float HOLD_DURATION   = 2.5f;   // readable pause
    private static final float FADE_DURATION   = 0.3f;   // fade to black

    // ─────────────────────────────────────────────────────────────────────
    // Dependencies
    // ─────────────────────────────────────────────────────────────────────

    private final ScreenManager screenManager;

    // ─────────────────────────────────────────────────────────────────────
    // Per-encounter state
    // ─────────────────────────────────────────────────────────────────────

    private EncounterData data;
    private float elapsed;
    private String announcementText;
    private String enemySummary;

    // Animation phases
    private enum Phase { EXPAND, HOLD, DONE }
    private Phase phase;

    // ─────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────

    public EncounterSplashScreen(ScreenManager screenManager) {
        this.screenManager = screenManager;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Screen lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onEnter(Object transitionData) {
        this.data    = (EncounterData) transitionData;
        this.elapsed = 0f;
        this.phase   = Phase.EXPAND;

        this.announcementText = data.condition().announcement();
        this.enemySummary     = buildEnemySummary(data.enemies());
    }

    @Override
    public void update(float dt) {
        elapsed += dt;

        switch (phase) {
            case EXPAND -> {
                if (elapsed >= EXPAND_DURATION) {
                    elapsed = 0f;
                    phase = Phase.HOLD;
                }
            }
            case HOLD -> {
                if (elapsed >= HOLD_DURATION) {
                    phase = Phase.DONE;
                    screenManager.fadeTo(GameState.COMBAT_PLANNING, data);
                }
            }
            case DONE -> { /* waiting for fade */ }
        }
    }

    @Override
    public void render(Graphics2D g) {
        int W = GameCanvas.DESIGN_WIDTH;
        int H = GameCanvas.DESIGN_HEIGHT;

        // Void background
        g.setColor(ParchmentRenderer.VOID);
        g.fillRect(0, 0, W, H);

        // Panel dimensions
        int pw = 620, ph = 220;
        int px = (W - pw) / 2;
        int py = (H - ph) / 2;

        // Scale animation during EXPAND phase
        float scale = 1f;
        float alpha = 1f;
        if (phase == Phase.EXPAND) {
            float t = Math.min(1f, elapsed / EXPAND_DURATION);
            scale = easeOutBack(t);
            alpha = t;
        }

        // Apply transform
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                Math.max(0f, Math.min(1f, alpha))));
        int cx = px + pw / 2;
        int cy = py + ph / 2;
        g2.translate(cx, cy);
        g2.scale(scale, scale);
        g2.translate(-cx, -cy);

        // Draw parchment panel
        ParchmentRenderer.drawPanel(g2, px, py, pw, ph);

        // Swords decoration
        ParchmentRenderer.drawTextCentered(g2, "\u2694",
                px, py + 50, pw,
                ParchmentRenderer.TextStyle.HEADER, ParchmentRenderer.GOLD);

        // Main announcement
        ParchmentRenderer.drawTextCentered(g2, announcementText,
                px, py + 95, pw,
                ParchmentRenderer.TextStyle.HEADER, ParchmentRenderer.GOLD_BRIGHT);

        // Divider
        ParchmentRenderer.drawDivider(g2, px + 80, py + 115, pw - 160);

        // Enemy summary
        ParchmentRenderer.drawTextCentered(g2, enemySummary,
                px, py + 150, pw,
                ParchmentRenderer.TextStyle.BODY, ParchmentRenderer.TEXT);

        // Condition subtitle (if not normal)
        if (data.condition() != EncounterCondition.NORMAL) {
            String condText = switch (data.condition()) {
                case MONSTERS_SURPRISE -> "The enemy has the initiative!";
                case PARTY_ASLEEP      -> "Your party was caught sleeping!";
                default                -> "";
            };
            ParchmentRenderer.drawTextCentered(g2, condText,
                    px, py + 185, pw,
                    ParchmentRenderer.TextStyle.LABEL, ParchmentRenderer.DANGER);
        }

        g2.dispose();
    }

    @Override
    public void onExit() {
        this.data = null;
    }

    @Override
    public void onKeyPressed(KeyEvent e) {
        // Any key skips the hold phase
        if (phase == Phase.HOLD) {
            phase = Phase.DONE;
            screenManager.fadeTo(GameState.COMBAT_PLANNING, data);
        }
    }

    @Override
    public void onMouseClicked(MouseEvent e) {
        onKeyPressed(null);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private String buildEnemySummary(List<Monster> enemies) {
        Map<String, Long> counts = enemies.stream()
                .filter(Monster::isAlive)
                .collect(Collectors.groupingBy(Monster::getName, Collectors.counting()));

        return counts.entrySet().stream()
                .map(e -> e.getValue() + " " + e.getKey() + (e.getValue() > 1 ? "s" : ""))
                .collect(Collectors.joining(", ")) + " approach.";
    }

    /** Ease-out with overshoot — gives the panel a springy "pop" appearance. */
    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }
}