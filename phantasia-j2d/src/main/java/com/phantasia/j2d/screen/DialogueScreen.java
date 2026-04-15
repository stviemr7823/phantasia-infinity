// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/DialogueScreen.java
package com.phantasia.j2d.screen;

import com.phantasia.core.data.GameSession;
import com.phantasia.core.model.*;
import com.phantasia.j2d.engine.GameCanvas;
import com.phantasia.j2d.engine.Screen;
import com.phantasia.j2d.engine.ScreenManager;
import com.phantasia.j2d.render.ParchmentRenderer;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.phantasia.j2d.render.ParchmentRenderer.*;

/**
 * Dialogue overlay — renders on top of the underlying screen (world
 * roam or town interior) via ScreenManager.push().
 *
 * Layout: bottom-center parchment box with portrait on the left,
 * dialogue text on the right, and optional response choices.
 *
 * Evaluates the NPC's DialogueScript (first-match-wins, flag checks)
 * and applies token substitution via DialogueTextRenderer.
 */
public class DialogueScreen implements Screen {

    /**
     * Data pushed by the world/town screen when initiating dialogue.
     */
    /**
     * @param npc      the NPC definition (carries DialogueScript)
     * @param session  live game session (quest flags read/written by evaluate)
     * @param context  frozen snapshot for token substitution
     * @param choices  response options (empty = no choices, just continue)
     */
    public record DialogueData(
            NpcDefinition       npc,
            GameSession         session,
            DialogueContext     context,
            List<String>        choices
    ) {}

    // ─────────────────────────────────────────────────────────────────────
    // Layout constants
    // ─────────────────────────────────────────────────────────────────────

    private static final int BOX_W   = 1200;
    private static final int BOX_H   = 260;
    private static final int BOX_Y   = 1080 - BOX_H - 60;

    private static final int PORTRAIT_W = 200;

    // ─────────────────────────────────────────────────────────────────────
    // Dependencies
    // ─────────────────────────────────────────────────────────────────────

    private final ScreenManager screenManager;

    // ─────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────

    private DialogueData data;
    private String       displayText;
    private int          selectedChoice;
    private float        textRevealProgress;  // 0→1, for typewriter effect

    // ─────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────

    public DialogueScreen(ScreenManager screenManager) {
        this.screenManager = screenManager;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onEnter(Object transitionData) {
        this.data = (DialogueData) transitionData;

        // Evaluate dialogue script — get the first matching node's text
        DialogueScript script = data.npc().getDialogue();
        if (script != null && !script.getNodes().isEmpty()) {
            DialogueResult result = script.evaluate(data.session(), data.context());
            if (result != null && !result.isEmpty()) {
                this.displayText = result.joinedText();
            } else {
                this.displayText = "...";
            }
        } else {
            this.displayText = data.npc().getName() + " has nothing to say.";
        }

        this.selectedChoice       = 0;
        this.textRevealProgress   = 0f;
    }

    @Override
    public void update(float dt) {
        // Typewriter effect — reveal text over time
        if (textRevealProgress < 1f) {
            textRevealProgress += dt * 2.0f; // ~0.5 seconds for full reveal
            textRevealProgress = Math.min(1f, textRevealProgress);
        }
    }

    @Override
    public void render(Graphics2D g) {
        int W = GameCanvas.DESIGN_WIDTH;

        // Dim overlay (underlying screen still renders via ScreenManager push/pop)
        g.setColor(new Color(13, 13, 18, 115));
        g.fillRect(0, 0, W, 1080);

        // NPC name plate
        int npX = (W - BOX_W) / 2;
        int npY = BOX_Y - 40;
        drawPanel(g, npX + BOX_W / 2 - 100, npY, 200, 34);
        drawTextCentered(g, data.npc().getName(),
                npX + BOX_W / 2 - 100, npY + 24, 200,
                TextStyle.LABEL, GOLD_BRIGHT);

        // Main dialogue box
        int bx = (W - BOX_W) / 2;
        drawPanel(g, bx, BOX_Y, BOX_W, BOX_H);

        // Portrait area
        g.setColor(new Color(0x352B1F));
        g.fillRoundRect(bx + 10, BOX_Y + 10, PORTRAIT_W, BOX_H - 20, 8, 8);
        g.setColor(BORDER_STR);
        g.drawRoundRect(bx + 10, BOX_Y + 10, PORTRAIT_W, BOX_H - 20, 8, 8);

        // Portrait placeholder
        drawTextCentered(g, "\uD83E\uDDD9", bx + 10, BOX_Y + 100, PORTRAIT_W,
                TextStyle.HEADER, new Color(255, 255, 255, 100));

        // NPC name under portrait
        drawTextCentered(g, data.npc().getName(), bx + 10, BOX_Y + 150, PORTRAIT_W,
                TextStyle.LABEL, GOLD);

        // Role
        drawTextCentered(g, data.npc().getRole().name(), bx + 10, BOX_Y + 170,
                PORTRAIT_W, TextStyle.DATA, new Color(0x8A7F6E));

        // Text area
        int textX = bx + PORTRAIT_W + 30;
        int textY = BOX_Y + 40;
        int textW = BOX_W - PORTRAIT_W - 50;

        // Typewriter reveal
        int revealChars = (int) (displayText.length() * textRevealProgress);
        String visibleText = displayText.substring(0, revealChars);

        // Word-wrap and render
        renderWrappedText(g, visibleText, textX, textY, textW);

        // Choices (if any, and text fully revealed)
        if (textRevealProgress >= 1f && data.choices() != null && !data.choices().isEmpty()) {
            renderChoices(g, textX, BOX_Y + BOX_H - 80, textW);
        }

        // Continue prompt
        if (textRevealProgress >= 1f && (data.choices() == null || data.choices().isEmpty())) {
            drawText(g, "Press ENTER to continue  \u2022  ESC to leave",
                    bx + BOX_W - 350, BOX_Y + BOX_H - 20,
                    TextStyle.DATA, new Color(0x8A7F6E));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Text rendering with word wrap
    // ─────────────────────────────────────────────────────────────────────

    private void renderWrappedText(Graphics2D g, String text, int x, int y, int maxW) {
        Font font = com.phantasia.j2d.engine.ResourceCache.get()
                .getFont(com.phantasia.j2d.engine.ResourceCache.FONT_BODY, Font.PLAIN, 20f);
        g.setFont(font);
        g.setColor(TEXT);
        FontMetrics fm = g.getFontMetrics();

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lineY = y;
        int lineH = fm.getHeight() + 4;

        for (String word : words) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(test) > maxW && !line.isEmpty()) {
                g.drawString(line.toString(), x, lineY);
                line = new StringBuilder(word);
                lineY += lineH;
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) {
            g.drawString(line.toString(), x, lineY);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Choice rendering
    // ─────────────────────────────────────────────────────────────────────

    private void renderChoices(Graphics2D g, int x, int y, int maxW) {
        for (int i = 0; i < data.choices().size(); i++) {
            boolean selected = (i == selectedChoice);
            Color textColor = selected ? GOLD_BRIGHT : TEXT;
            String marker = selected ? "\u25B8 " : "  ";
            drawText(g, marker + data.choices().get(i), x, y + i * 28,
                    TextStyle.BODY, textColor);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onKeyPressed(KeyEvent e) {
        if (e == null) return;

        // If text is still revealing, skip to full reveal
        if (textRevealProgress < 1f) {
            textRevealProgress = 1f;
            return;
        }

        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> {
                if (data.choices() != null && !data.choices().isEmpty()) {
                    selectedChoice = Math.max(0, selectedChoice - 1);
                }
            }
            case KeyEvent.VK_DOWN -> {
                if (data.choices() != null && !data.choices().isEmpty()) {
                    selectedChoice = Math.min(data.choices().size() - 1, selectedChoice + 1);
                }
            }
            case KeyEvent.VK_ENTER -> {
                // Dismiss dialogue overlay
                screenManager.pop();
            }
            case KeyEvent.VK_ESCAPE -> {
                screenManager.pop();
            }
        }
    }

    @Override
    public void onMouseClicked(MouseEvent e) {}

    @Override
    public void onExit() {
        this.data = null;
    }
}