// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/CombatExecutionScreen.java
package com.phantasia.j2d.screen;

import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;

import com.phantasia.j2d.engine.GameCanvas;
import com.phantasia.j2d.engine.GameState;
import com.phantasia.j2d.engine.Screen;
import com.phantasia.j2d.engine.ScreenManager;
import com.phantasia.j2d.render.ParchmentRenderer;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static com.phantasia.j2d.render.ParchmentRenderer.*;

/**
 * Phase C of the two-screen combat system — cinematic round resolution.
 *
 * No player input during execution. CombatEvents from the RoundResult
 * play sequentially with sprite animations and a narration bar:
 *
 *   Top area:    Enemy sprites in a horizontal rank
 *   Middle area: Party sprites in a horizontal rank
 *   Bottom bar:  Narration bar (single line, updates per event)
 *
 * After all events play, a 1-second pause, then:
 *   - Loop back to CombatPlanningScreen (next round)
 *   - Victory screen (all enemies defeated)
 *   - Defeat screen (party wipe)
 */
public class CombatExecutionScreen implements Screen {

    /**
     * Data received from CombatPlanningScreen after engaging.
     */
    public record ExecutionData(
            List<PlayerCharacter> party,
            List<Monster>         enemies,
            RoundResult           result,
            CombatNarrator        narrator,
            CombatManager         manager,
            int                   roundNumber
    ) {}

    // ─────────────────────────────────────────────────────────────────────
    // Layout constants
    // ─────────────────────────────────────────────────────────────────────

    private static final int NARRATION_H = 120;
    private static final int NARRATION_Y = 1080 - NARRATION_H;

    // Sprite placeholder sizes
    private static final int SPRITE_W = 80;
    private static final int SPRITE_H = 96;

    // Enemy row vertical center
    private static final int ENEMY_ROW_Y = 200;
    // Party row vertical center
    private static final int PARTY_ROW_Y = 600;

    // ─────────────────────────────────────────────────────────────────────
    // Timing
    // ─────────────────────────────────────────────────────────────────────

    private static final float EVENT_DURATION   = 1.2f;  // seconds per event
    private static final float HIT_MOMENT       = 0.4f;  // when damage resolves within event
    private static final float POST_ROUND_PAUSE = 1.5f;

    // ─────────────────────────────────────────────────────────────────────
    // Dependencies
    // ─────────────────────────────────────────────────────────────────────

    private final ScreenManager screenManager;

    // ─────────────────────────────────────────────────────────────────────
    // Per-execution state
    // ─────────────────────────────────────────────────────────────────────

    private ExecutionData data;
    private List<String>  narrationLines;

    // Event playback
    private int   currentEventIndex;
    private float eventTimer;
    private boolean postRoundPause;
    private float pauseTimer;
    private String currentNarration;

    // Floating damage numbers
    private final List<FloatingNumber> floatingNumbers = new ArrayList<>();

    private record FloatingNumber(
            float x, float y, String text, Color color,
            float lifetime, float maxLife
    ) {
        FloatingNumber withLife(float newLife) {
            return new FloatingNumber(x, y, text, color, newLife, maxLife);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────

    public CombatExecutionScreen(ScreenManager screenManager) {
        this.screenManager = screenManager;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Screen lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onEnter(Object transitionData) {
        this.data = (ExecutionData) transitionData;
        this.narrationLines = data.narrator().narrateRound(data.result());

        currentEventIndex = 0;
        eventTimer        = 0f;
        postRoundPause    = false;
        pauseTimer        = 0f;
        currentNarration  = narrationLines.isEmpty() ? "" : narrationLines.get(0);
        floatingNumbers.clear();
    }

    @Override
    public void update(float dt) {
        // Update floating numbers
        List<FloatingNumber> alive = new ArrayList<>();
        for (FloatingNumber fn : floatingNumbers) {
            FloatingNumber updated = fn.withLife(fn.lifetime + dt);
            if (updated.lifetime < updated.maxLife) alive.add(updated);
        }
        floatingNumbers.clear();
        floatingNumbers.addAll(alive);

        if (postRoundPause) {
            pauseTimer += dt;
            if (pauseTimer >= POST_ROUND_PAUSE) {
                transitionOut();
            }
            return;
        }

        // Advance event playback
        eventTimer += dt;

        // Hit moment — spawn damage number
        if (eventTimer >= HIT_MOMENT && eventTimer - dt < HIT_MOMENT) {
            onHitMoment();
        }

        if (eventTimer >= EVENT_DURATION) {
            currentEventIndex++;
            eventTimer = 0f;

            if (currentEventIndex < data.result().events().size()) {
                // Update narration for next event
                if (currentEventIndex < narrationLines.size()) {
                    currentNarration = narrationLines.get(currentEventIndex);
                }
            } else {
                // All events played — pause then transition
                postRoundPause = true;
                pauseTimer     = 0f;
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        int W = GameCanvas.DESIGN_WIDTH;
        int H = GameCanvas.DESIGN_HEIGHT;

        // Void background with subtle gradient
        g.setColor(VOID);
        g.fillRect(0, 0, W, H);

        // Round indicator
        drawTextCentered(g, "\u2014 Round " + data.roundNumber() + " \u2014",
                0, 40, W, TextStyle.HEADER, GOLD);

        // Enemy row
        renderEntityRow(g, data.enemies().stream()
                        .filter(Monster::isAlive)
                        .map(m -> new EntityDisplay(m.getName(), m.getHp(), -1, true))
                        .toList(),
                ENEMY_ROW_Y, true);

        // Party row
        renderEntityRow(g, data.party().stream()
                        .filter(PlayerCharacter::isAlive)
                        .map(pc -> new EntityDisplay(pc.getName(), pc.getHp(),
                                pc.getStat(Stat.MAX_HP), false))
                        .toList(),
                PARTY_ROW_Y, false);

        // Floating damage numbers
        for (FloatingNumber fn : floatingNumbers) {
            float progress = fn.lifetime / fn.maxLife;
            float alpha = 1f - progress;
            float yOffset = progress * 40f;
            ParchmentRenderer.drawFloatingNumber(g, fn.text,
                    (int) fn.x, (int) (fn.y - yOffset), alpha, fn.color);
        }

        // Active event highlight (simple pulse on acting entity)
        renderActiveHighlight(g);

        // Narration bar
        renderNarrationBar(g, W);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Entity row rendering
    // ─────────────────────────────────────────────────────────────────────

    private record EntityDisplay(String name, int hp, int maxHp, boolean isEnemy) {}

    private void renderEntityRow(Graphics2D g, List<EntityDisplay> entities,
                                 int centerY, boolean isEnemy) {
        int W = GameCanvas.DESIGN_WIDTH;
        int totalW = entities.size() * (SPRITE_W + 60);
        int startX = (W - totalW) / 2;

        for (int i = 0; i < entities.size(); i++) {
            EntityDisplay e = entities.get(i);
            int cx = startX + i * (SPRITE_W + 60) + SPRITE_W / 2;

            // Sprite placeholder box
            Color boxColor = isEnemy
                    ? new Color(192, 57, 43, 40)
                    : new Color(74, 127, 165, 40);
            Color borderColor = isEnemy
                    ? new Color(192, 57, 43, 80)
                    : new Color(74, 127, 165, 80);

            g.setColor(boxColor);
            g.fillRoundRect(cx - SPRITE_W / 2, centerY - SPRITE_H / 2, SPRITE_W, SPRITE_H, 6, 6);
            g.setColor(borderColor);
            g.drawRoundRect(cx - SPRITE_W / 2, centerY - SPRITE_H / 2, SPRITE_W, SPRITE_H, 6, 6);

            // Placeholder icon
            g.setColor(new Color(255, 255, 255, 100));
            drawTextCentered(g, isEnemy ? "\u2620" : "\u2694",
                    cx - SPRITE_W / 2, centerY, SPRITE_W, TextStyle.HEADER,
                    new Color(255, 255, 255, 100));

            // Name
            Color nameColor = isEnemy ? DANGER : GOLD;
            drawTextCentered(g, e.name, cx - SPRITE_W / 2 - 10, centerY + SPRITE_H / 2 + 20,
                    SPRITE_W + 20, TextStyle.LABEL, nameColor);

            // Mini HP bar
            if (e.maxHp > 0) {
                drawHealthBar(g, e.hp, e.maxHp,
                        cx - 35, centerY + SPRITE_H / 2 + 28, 70, 5);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Active event highlight
    // ─────────────────────────────────────────────────────────────────────

    private void renderActiveHighlight(Graphics2D g) {
        if (currentEventIndex >= data.result().events().size()) return;
        // Could add a glow/pulse around the acting entity — deferred to sprite system
    }

    // ─────────────────────────────────────────────────────────────────────
    // Hit moment — spawn floating numbers
    // ─────────────────────────────────────────────────────────────────────

    private void onHitMoment() {
        if (currentEventIndex >= data.result().events().size()) return;
        CombatEvent event = data.result().events().get(currentEventIndex);

        switch (event) {
            case CombatEvent.Hit h -> {
                float tx = findEntityX(h.targetName());
                float ty = findEntityY(h.targetName());
                String text = (h.wasCritical() ? "\u2605 " : "") + "-" + h.damage();
                floatingNumbers.add(new FloatingNumber(tx, ty, text, DANGER, 0f, 1.5f));
            }
            case CombatEvent.SpellCast sc -> {
                if (sc.succeeded() && sc.magnitude() != 0) {
                    float tx = findEntityX(sc.targetName());
                    float ty = findEntityY(sc.targetName());
                    boolean isHeal = sc.magnitude() < 0;
                    String text = isHeal
                            ? "+" + Math.abs(sc.magnitude())
                            : "-" + sc.magnitude();
                    Color color = isHeal ? HP_GREEN : MAGIC;
                    floatingNumbers.add(new FloatingNumber(tx, ty, text, color, 0f, 1.5f));
                }
            }
            case CombatEvent.Death d -> {
                float tx = findEntityX(d.entityName());
                float ty = findEntityY(d.entityName());
                floatingNumbers.add(new FloatingNumber(tx, ty - 20, "DEFEATED", DANGER, 0f, 2.0f));
            }
            default -> { /* Miss, StatusChange, FleeAttempt, RoundHeader — no number */ }
        }
    }

    private float findEntityX(String name) {
        // Approximate — center of screen + offset by entity index
        int W = GameCanvas.DESIGN_WIDTH;
        return W / 2f + (name.hashCode() % 200) - 100;
    }

    private float findEntityY(String name) {
        // Check if enemy or party member
        boolean isEnemy = data.enemies().stream().anyMatch(m -> m.getName().equals(name));
        return isEnemy ? ENEMY_ROW_Y : PARTY_ROW_Y;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Narration bar
    // ─────────────────────────────────────────────────────────────────────

    private void renderNarrationBar(Graphics2D g, int W) {
        drawPanel(g, 0, NARRATION_Y, W, NARRATION_H);

        // Sword icon
        drawText(g, "\u2694", 50, NARRATION_Y + 68, TextStyle.HEADER, GOLD);

        // Narration text
        drawText(g, currentNarration, 90, NARRATION_Y + 68, TextStyle.BODY, TEXT);

        // Event counter
        String counter = "Event " + (currentEventIndex + 1) + " / "
                + data.result().events().size();
        drawText(g, counter, W - 200, NARRATION_Y + 68, TextStyle.DATA,
                new Color(0x8A7F6E));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Transition out
    // ─────────────────────────────────────────────────────────────────────

    private void transitionOut() {
        RoundResult result = data.result();

        if (result.isVictory()) {
            screenManager.fadeTo(GameState.VICTORY, data);
        } else if (result.isDefeat()) {
            screenManager.fadeTo(GameState.DEFEAT, data);
        } else if (result.isEscape()) {
            screenManager.fadeTo(GameState.WORLD_ROAM, null);
        } else {
            // Ongoing — back to planning for next round
            CombatPlanningScreen.PlanningResult nextRound =
                    new CombatPlanningScreen.PlanningResult(
                            data.party(), data.enemies(),
                            data.manager(), data.narrator(),
                            data.roundNumber() + 1
                    );
            screenManager.fadeTo(GameState.COMBAT_PLANNING, nextRound);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Input (minimal — skip to end)
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onKeyPressed(KeyEvent e) {
        // Space/Enter skips to post-round pause
        if (e != null && (e.getKeyCode() == KeyEvent.VK_SPACE
                || e.getKeyCode() == KeyEvent.VK_ENTER)) {
            if (!postRoundPause) {
                currentEventIndex = data.result().events().size();
                eventTimer = 0;
                postRoundPause = true;
                pauseTimer = 0;
                if (!narrationLines.isEmpty()) {
                    currentNarration = narrationLines.get(narrationLines.size() - 1);
                }
            } else {
                transitionOut();
            }
        }
    }

    @Override
    public void onMouseClicked(MouseEvent e) {}

    @Override
    public void onExit() {
        floatingNumbers.clear();
    }
}
