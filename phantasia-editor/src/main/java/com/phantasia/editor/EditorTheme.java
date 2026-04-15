// phantasia-editor/src/main/java/com/phantasia/editor/EditorTheme.java
package com.phantasia.editor;

import java.awt.*;

/**
 * The Phantasia Editor dark fantasy palette and typography constants.
 * Delegates to {@link PhantasiaEditorConfig}'s palette so there's one
 * source of truth for all colors.
 */
public final class EditorTheme {

    private EditorTheme() {}

    // ── Core palette — delegates to PhantasiaEditorConfig ─────────────────
    public static final Color BG_BASE       = PhantasiaEditorConfig.MIDNIGHT;
    public static final Color BG_BASE_SOLID = PhantasiaEditorConfig.MIDNIGHT;
    public static final Color BG_PANEL      = PhantasiaEditorConfig.PANEL;
    public static final Color BG_LIST       = PhantasiaEditorConfig.FIELD_BG;
    public static final Color BORDER        = PhantasiaEditorConfig.BORDER_COLOR;
    public static final Color ACCENT        = PhantasiaEditorConfig.GOLD;
    public static final Color ACCENT_DIM    = PhantasiaEditorConfig.GOLD_DIM;
    public static final Color TEXT          = PhantasiaEditorConfig.TEXT_BRIGHT;
    public static final Color TEXT_DIM      = PhantasiaEditorConfig.TEXT_DIM;
    public static final Color RED           = PhantasiaEditorConfig.RED;
    public static final Color GREEN         = PhantasiaEditorConfig.GREEN;
    public static final Color BLUE          = PhantasiaEditorConfig.BLUE;

    // ── Typography ────────────────────────────────────────────────────────
    public static final Font FONT_TITLE    = new Font("Serif",      Font.BOLD,  16);
    public static final Font FONT_HEADER   = new Font("Serif",      Font.BOLD,  14);
    public static final Font FONT_LABEL    = new Font("SansSerif",  Font.PLAIN, 12);
    public static final Font FONT_LABEL_B  = new Font("SansSerif",  Font.BOLD,  12);
    public static final Font FONT_FIELD    = new Font("Monospaced", Font.PLAIN, 12);
    public static final Font FONT_SMALL    = new Font("Monospaced", Font.PLAIN, 11);

    // ── Geometry ──────────────────────────────────────────────────────────
    public static final int ARC_PANEL      = 6;
    public static final int ARC_COMPONENT  = 6;

    // ── Splitter defaults ─────────────────────────────────────────────────
    public static final int DEFAULT_LEFT_SPLIT  = 220;
    public static final int DEFAULT_RIGHT_SPLIT = 280;
}