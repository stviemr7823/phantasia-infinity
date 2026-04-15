// phantasia-j2d/src/main/java/com/phantasia/j2d/render/ParchmentRenderer.java
package com.phantasia.j2d.render;

import com.phantasia.j2d.engine.ResourceCache;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Shared rendering toolkit for the parchment visual language.
 *
 * <p>Every UI surface in the game is built from these primitives.
 * No screen creates its own panels, text styles, or health bars —
 * everything goes through ParchmentRenderer for visual consistency.</p>
 *
 * <h3>Design Palette (from Section 5.3)</h3>
 * <pre>
 * VOID         #0D0D12   Background
 * PARCHMENT    #2A2118   Panel surface
 * TEXT         #D7CDB9   Body text
 * GOLD         #BFA34C   Borders, accents
 * GOLD_BRIGHT  #E8C96A   Hover/active
 * BORDER_SUB   #3D3226   Subtle edges
 * BORDER_STR   #6B5A3E   Dividers
 * DANGER       #C0392B   Low HP, enemy
 * MAGIC        #4A7FA5   Spells, MP
 * </pre>
 */
public final class ParchmentRenderer {

    private ParchmentRenderer() {} // utility class

    // ─────────────────────────────────────────────────────────────────────────
    // Palette constants
    // ─────────────────────────────────────────────────────────────────────────

    public static final Color VOID         = new Color(0x0D0D12);
    public static final Color PARCHMENT    = new Color(0x2A2118);
    public static final Color TEXT         = new Color(0xD7CDB9);
    public static final Color GOLD         = new Color(0xBFA34C);
    public static final Color GOLD_BRIGHT  = new Color(0xE8C96A);
    public static final Color BORDER_SUB   = new Color(0x3D3226);
    public static final Color BORDER_STR   = new Color(0x6B5A3E);
    public static final Color DANGER       = new Color(0xC0392B);
    public static final Color MAGIC        = new Color(0x4A7FA5);

    // HP bar specific
    public static final Color HP_GREEN     = new Color(0x4A8C3F);
    public static final Color HP_YELLOW    = new Color(0xC9A926);
    public static final Color HP_RED       = DANGER;
    public static final Color BAR_BG       = new Color(0x1A1510);

    /** Default corner arc for parchment panels. */
    private static final int ARC = 12;

    /** Gold border stroke width. */
    private static final float BORDER_WIDTH = 2f;

    // ─────────────────────────────────────────────────────────────────────────
    // Panel drawing
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Draws a parchment panel — the core visual building block.
     *
     * <p>Dark warm-brown rounded rectangle with gold border, subtle inner
     * vignette, and noise texture overlay.</p>
     *
     * @param g  graphics context
     * @param x  left edge (design coords)
     * @param y  top edge
     * @param w  width
     * @param h  height
     */
    public static void drawPanel(Graphics2D g, int x, int y, int w, int h) {
        drawPanel(g, x, y, w, h, GOLD);
    }

    /**
     * Draws a parchment panel with a custom border color.
     */
    public static void drawPanel(Graphics2D g, int x, int y, int w, int h, Color borderColor) {
        Shape rrect = new RoundRectangle2D.Float(x, y, w, h, ARC, ARC);

        // 1. Parchment fill
        TexturePaint tex = ResourceCache.get().getParchmentPaint();
        if (tex != null) {
            g.setPaint(tex);
        } else {
            g.setColor(PARCHMENT);
        }
        g.fill(rrect);

        // 2. Inner vignette (radial gradient from transparent center to dark edges)
        Paint oldPaint = g.getPaint();
        int cx = x + w / 2, cy = y + h / 2;
        float radius = Math.max(w, h) * 0.6f;
        RadialGradientPaint vignette = new RadialGradientPaint(
                cx, cy, radius,
                new float[]{0.0f, 0.7f, 1.0f},
                new Color[]{
                        new Color(0, 0, 0, 0),
                        new Color(0, 0, 0, 0),
                        new Color(0, 0, 0, 80)
                }
        );
        g.setPaint(vignette);
        g.fill(rrect);

        // 3. Gold border
        g.setColor(borderColor);
        g.setStroke(new BasicStroke(BORDER_WIDTH));
        g.draw(rrect);

        g.setPaint(oldPaint);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Text drawing
    // ─────────────────────────────────────────────────────────────────────────

    /** Text style presets. */
    public enum TextStyle {
        /** Cinzel, large — panel headers, character names. */
        HEADER,
        /** Crimson Text, medium — dialogue, narration. */
        BODY,
        /** Share Tech Mono — HP/MP numbers, stats. */
        DATA,
        /** Cinzel, small — sub-headers, labels. */
        LABEL
    }

    /**
     * Draws styled text at the given position.
     *
     * @param g      graphics context
     * @param text   the string to draw
     * @param x      left edge
     * @param y      baseline
     * @param style  the text style preset
     */
    public static void drawText(Graphics2D g, String text, int x, int y, TextStyle style) {
        drawText(g, text, x, y, style, TEXT);
    }

    /**
     * Draws styled text with a custom color.
     */
    public static void drawText(Graphics2D g, String text, int x, int y,
                                TextStyle style, Color color) {
        ResourceCache rc = ResourceCache.get();
        Font font = switch (style) {
            case HEADER -> rc.getFont(ResourceCache.FONT_HEADER, Font.BOLD, 28f);
            case BODY   -> rc.getFont(ResourceCache.FONT_BODY, Font.PLAIN, 20f);
            case DATA   -> rc.getFont(ResourceCache.FONT_DATA, Font.PLAIN, 18f);
            case LABEL  -> rc.getFont(ResourceCache.FONT_HEADER, Font.PLAIN, 16f);
        };
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    /**
     * Draws text centered horizontally within a bounding box.
     */
    public static void drawTextCentered(Graphics2D g, String text,
                                        int x, int y, int w,
                                        TextStyle style, Color color) {
        ResourceCache rc = ResourceCache.get();
        Font font = switch (style) {
            case HEADER -> rc.getFont(ResourceCache.FONT_HEADER, Font.BOLD, 28f);
            case BODY   -> rc.getFont(ResourceCache.FONT_BODY, Font.PLAIN, 20f);
            case DATA   -> rc.getFont(ResourceCache.FONT_DATA, Font.PLAIN, 18f);
            case LABEL  -> rc.getFont(ResourceCache.FONT_HEADER, Font.PLAIN, 16f);
        };
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int tx = x + (w - fm.stringWidth(text)) / 2;
        g.setColor(color);
        g.drawString(text, tx, y);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Health / MP bars
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Draws an HP bar with color-coded fill (green → yellow → red).
     */
    public static void drawHealthBar(Graphics2D g, int current, int max,
                                     int x, int y, int w, int h) {
        // Background
        g.setColor(BAR_BG);
        g.fillRoundRect(x, y, w, h, 4, 4);

        if (max <= 0) return;
        float ratio = Math.max(0, Math.min(1, (float) current / max));

        // Fill color based on ratio
        Color fillColor;
        if (ratio > 0.6f)      fillColor = HP_GREEN;
        else if (ratio > 0.25f) fillColor = HP_YELLOW;
        else                     fillColor = HP_RED;

        int fillW = (int) (w * ratio);
        if (fillW > 0) {
            g.setColor(fillColor);
            g.fillRoundRect(x, y, fillW, h, 4, 4);
        }

        // Border
        g.setColor(BORDER_SUB);
        g.drawRoundRect(x, y, w, h, 4, 4);
    }

    /**
     * Draws an MP bar (always magic blue).
     */
    public static void drawManaBar(Graphics2D g, int current, int max,
                                   int x, int y, int w, int h) {
        g.setColor(BAR_BG);
        g.fillRoundRect(x, y, w, h, 4, 4);

        if (max <= 0) return;
        float ratio = Math.max(0, Math.min(1, (float) current / max));

        int fillW = (int) (w * ratio);
        if (fillW > 0) {
            g.setColor(MAGIC);
            g.fillRoundRect(x, y, fillW, h, 4, 4);
        }

        g.setColor(BORDER_SUB);
        g.drawRoundRect(x, y, w, h, 4, 4);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sprite drawing
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Draws a sprite image at the given position with uniform scaling.
     */
    public static void drawSprite(Graphics2D g, Image image,
                                  int x, int y, float scale) {
        int w = (int) (image.getWidth(null) * scale);
        int h = (int) (image.getHeight(null) * scale);
        g.drawImage(image, x, y, w, h, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Floating damage / heal numbers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Draws a floating combat number with fade-out alpha.
     *
     * @param g      graphics context
     * @param value  the number to display (e.g., "-8", "+12")
     * @param x      center X
     * @param y      current Y (caller animates upward)
     * @param alpha  0.0 = invisible, 1.0 = fully opaque
     * @param color  damage (DANGER) or heal (HP_GREEN)
     */
    public static void drawFloatingNumber(Graphics2D g, String value,
                                          int x, int y, float alpha, Color color) {
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, Math.max(0, Math.min(1, alpha))));

        Font font = ResourceCache.get().getFont(ResourceCache.FONT_DATA, Font.BOLD, 24f);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int tx = x - fm.stringWidth(value) / 2;

        // Drop shadow
        g.setColor(new Color(0, 0, 0, (int) (180 * alpha)));
        g.drawString(value, tx + 1, y + 1);

        // Value
        g.setColor(color);
        g.drawString(value, tx, y);

        g.setComposite(old);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dividers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Draws a horizontal gold divider line.
     */
    public static void drawDivider(Graphics2D g, int x, int y, int w) {
        g.setColor(BORDER_STR);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(x, y, x + w, y);
    }
}