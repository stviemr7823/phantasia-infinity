// phantasia-editor/src/main/java/com/phantasia/editor/PhantasiaEditorConfig.java
package com.phantasia.editor;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * Bootstrap configuration for the Phantasia Editor Suite.
 *
 * Uses a custom FlatDarkLaf subclass so the theme overrides are baked
 * into the L&F itself — not applied after the fact via UIManager.put().
 * This is the officially supported FlatLaf customization mechanism.
 */
public final class PhantasiaEditorConfig {

    private PhantasiaEditorConfig() {}

    // ── Palette — intentionally HIGH CONTRAST for visibility ─────────────
    // Deep midnight blue (not gray — distinctly blue-tinted)
    static final Color MIDNIGHT     = new Color(16, 14, 28);
    static final Color PANEL        = new Color(22, 20, 36);
    static final Color SURFACE      = new Color(28, 26, 44);
    static final Color FIELD_BG     = new Color(14, 12, 24);

    // Gold — bright and unmistakable
    static final Color GOLD         = new Color(218, 175, 62);
    static final Color GOLD_DIM     = new Color(160, 128, 48);
    static final Color GOLD_GLOW    = new Color(218, 175, 62, 50);
    static final Color GOLD_SELECT  = new Color(218, 175, 62, 45);

    // Borders — visible purple-tinted
    static final Color BORDER_COLOR = new Color(60, 50, 80);
    static final Color BORDER_FOCUS = new Color(218, 175, 62);

    // Text
    static final Color TEXT_BRIGHT  = new Color(225, 215, 195);
    static final Color TEXT_NORMAL  = new Color(195, 185, 165);
    static final Color TEXT_DIM     = new Color(120, 110, 95);

    // Accents
    static final Color RED          = new Color(192, 69, 69);
    static final Color GREEN        = new Color(92, 160, 92);
    static final Color BLUE         = new Color(80, 120, 176);

    public static void bootstrapEnvironment() {
        System.setProperty("awt.useSystemAAFontSettings", "lcd_hrgb");
        System.setProperty("sun.java2d.opengl", "true");

        try {
            UIManager.setLookAndFeel(new PhantasiaDarkLaf());
            System.out.println("[EditorConfig] Phantasia Dark Fantasy theme installed.");
        } catch (Exception e) {
            System.err.println("[EditorConfig] Theme failed: " + e.getMessage());
            e.printStackTrace();
            try { UIManager.setLookAndFeel(new FlatDarkLaf()); }
            catch (Exception ignored) {}
        }
    }

    /**
     * Custom FlatLaf subclass — overrides getDefaults() so every Swing
     * component picks up the dark fantasy palette at creation time.
     */
    public static class PhantasiaDarkLaf extends FlatDarkLaf {

        public static boolean setup() {
            return setup(new PhantasiaDarkLaf());
        }

        @Override
        public String getName() { return "Phantasia Dark"; }

        @Override
        public UIDefaults getDefaults() {
            UIDefaults d = super.getDefaults();

            // ── Core geometry ────────────────────────────────────────────
            d.put("Component.focusWidth",         0);
            d.put("Component.innerFocusWidth",     1);
            d.put("Component.focusColor",          GOLD_GLOW);
            d.put("Component.arc",                 6);
            d.put("Component.borderColor",         BORDER_COLOR);
            d.put("Component.disabledBorderColor", new Color(40, 35, 55));

            // ── Panel ────────────────────────────────────────────────────
            d.put("Panel.background",              PANEL);

            // ── Buttons ──────────────────────────────────────────────────
            d.put("Button.arc",                    6);
            d.put("Button.background",             SURFACE);
            d.put("Button.foreground",             TEXT_NORMAL);
            d.put("Button.hoverBackground",        new Color(40, 36, 60));
            d.put("Button.pressedBackground",      new Color(50, 45, 70));
            d.put("Button.borderColor",            BORDER_COLOR);
            d.put("Button.hoverBorderColor",       GOLD_DIM);
            d.put("Button.focusedBorderColor",     GOLD);

            d.put("Button.default.background",     new Color(55, 45, 20));
            d.put("Button.default.foreground",     GOLD);
            d.put("Button.default.hoverBackground", new Color(70, 58, 25));
            d.put("Button.default.pressedBackground", new Color(85, 70, 30));
            d.put("Button.default.borderColor",    GOLD_DIM);
            d.put("Button.default.hoverBorderColor", GOLD);
            d.put("Button.default.focusedBorderColor", GOLD);

            d.put("ToggleButton.selectedBackground", GOLD_SELECT);
            d.put("ToggleButton.selectedForeground", GOLD);

            // ── Text fields ──────────────────────────────────────────────
            d.put("TextField.background",          FIELD_BG);
            d.put("TextField.foreground",          TEXT_BRIGHT);
            d.put("TextField.caretColor",          GOLD);
            d.put("TextField.selectionBackground", new Color(218, 175, 62, 60));
            d.put("TextField.selectionForeground", TEXT_BRIGHT);
            d.put("TextField.placeholderForeground", TEXT_DIM);
            d.put("TextField.borderColor",         BORDER_COLOR);
            d.put("TextField.focusedBorderColor",  GOLD);
            d.put("TextArea.background",           FIELD_BG);
            d.put("TextArea.foreground",           TEXT_BRIGHT);
            d.put("TextArea.caretColor",           GOLD);
            d.put("FormattedTextField.background",  FIELD_BG);
            d.put("PasswordField.background",       FIELD_BG);

            // ── Combo box / Spinner ──────────────────────────────────────
            d.put("ComboBox.background",           FIELD_BG);
            d.put("ComboBox.foreground",           TEXT_BRIGHT);
            d.put("ComboBox.buttonBackground",     SURFACE);
            d.put("ComboBox.borderColor",          BORDER_COLOR);
            d.put("ComboBox.focusedBorderColor",   GOLD);
            d.put("Spinner.background",            FIELD_BG);
            d.put("Spinner.buttonBackground",      SURFACE);
            d.put("Spinner.borderColor",           BORDER_COLOR);
            d.put("Spinner.focusedBorderColor",    GOLD);

            // ── Checkboxes / Radio ───────────────────────────────────────
            d.put("CheckBox.foreground",           TEXT_NORMAL);
            d.put("CheckBox.icon.borderColor",     BORDER_COLOR);
            d.put("CheckBox.icon.selectedBorderColor", GOLD);
            d.put("CheckBox.icon.background",      FIELD_BG);
            d.put("CheckBox.icon.selectedBackground", GOLD);
            d.put("CheckBox.icon.checkmarkColor",  MIDNIGHT);
            d.put("CheckBox.icon.focusedBorderColor", GOLD);
            d.put("RadioButton.foreground",        TEXT_NORMAL);
            d.put("RadioButton.icon.borderColor",  BORDER_COLOR);
            d.put("RadioButton.icon.selectedBorderColor", GOLD);
            d.put("RadioButton.icon.centerDiameter", 8);

            // ── Tree ─────────────────────────────────────────────────────
            d.put("Tree.background",               FIELD_BG);
            d.put("Tree.foreground",               TEXT_NORMAL);
            d.put("Tree.textForeground",           TEXT_NORMAL);
            d.put("Tree.selectionBackground",      GOLD_SELECT);
            d.put("Tree.selectionForeground",      GOLD);
            d.put("Tree.selectionInactiveBackground", new Color(218, 175, 62, 20));
            d.put("Tree.hash",                     new Color(60, 50, 80, 80));

            // ── List ─────────────────────────────────────────────────────
            d.put("List.background",               FIELD_BG);
            d.put("List.foreground",               TEXT_NORMAL);
            d.put("List.selectionBackground",      GOLD_SELECT);
            d.put("List.selectionForeground",      GOLD);
            d.put("List.selectionInactiveBackground", new Color(218, 175, 62, 20));

            // ── Table ────────────────────────────────────────────────────
            d.put("Table.background",              FIELD_BG);
            d.put("Table.foreground",              TEXT_NORMAL);
            d.put("Table.selectionBackground",     GOLD_SELECT);
            d.put("Table.selectionForeground",     GOLD);
            d.put("Table.gridColor",               new Color(60, 50, 80, 50));

            // ── Tabbed pane ──────────────────────────────────────────────
            d.put("TabbedPane.background",         MIDNIGHT);
            d.put("TabbedPane.foreground",         TEXT_DIM);
            d.put("TabbedPane.selectedBackground", PANEL);
            d.put("TabbedPane.selectedForeground", TEXT_BRIGHT);
            d.put("TabbedPane.underlineColor",     GOLD);
            d.put("TabbedPane.inactiveUnderlineColor", BORDER_COLOR);
            d.put("TabbedPane.hoverColor",         SURFACE);
            d.put("TabbedPane.hoverForeground",    TEXT_NORMAL);
            d.put("TabbedPane.contentAreaColor",   PANEL);
            d.put("TabbedPane.tabSeparatorColor",  new Color(0, 0, 0, 0));

            // ── Menu bar ─────────────────────────────────────────────────
            d.put("MenuBar.background",            MIDNIGHT);
            d.put("MenuBar.foreground",            TEXT_NORMAL);
            d.put("MenuBar.hoverBackground",       GOLD_SELECT);
            d.put("MenuBar.borderColor",           BORDER_COLOR);
            d.put("Menu.foreground",               TEXT_NORMAL);
            d.put("Menu.selectionBackground",      GOLD_SELECT);
            d.put("Menu.selectionForeground",      GOLD);
            d.put("MenuItem.foreground",           TEXT_NORMAL);
            d.put("MenuItem.selectionBackground",  GOLD_SELECT);
            d.put("MenuItem.selectionForeground",  GOLD);
            d.put("MenuItem.acceleratorForeground", TEXT_DIM);
            d.put("PopupMenu.background",          SURFACE);
            d.put("PopupMenu.borderColor",         BORDER_COLOR);

            // ── Scroll bars ──────────────────────────────────────────────
            d.put("ScrollBar.track",               MIDNIGHT);
            d.put("ScrollBar.thumb",               BORDER_COLOR);
            d.put("ScrollBar.hoverThumbColor",     new Color(80, 70, 110));
            d.put("ScrollBar.pressedThumbColor",   GOLD_DIM);
            d.put("ScrollBar.width",               10);
            d.put("ScrollBar.thumbArc",            999);
            d.put("ScrollBar.thumbInsets",         new Insets(2, 2, 2, 2));
            d.put("ScrollBar.showButtons",         false);
            d.put("ScrollPane.background",         FIELD_BG);
            d.put("ScrollPane.borderColor",        BORDER_COLOR);

            // ── Split pane ───────────────────────────────────────────────
            d.put("SplitPane.background",          MIDNIGHT);
            d.put("SplitPane.dividerSize",         5);
            d.put("SplitPaneDivider.draggingColor", GOLD_GLOW);

            // ── Titled borders ───────────────────────────────────────────
            d.put("TitledBorder.titleColor",       GOLD);

            // ── Tooltips ─────────────────────────────────────────────────
            d.put("ToolTip.background",            SURFACE);
            d.put("ToolTip.foreground",            TEXT_BRIGHT);
            d.put("ToolTip.borderColor",           GOLD_DIM);

            // ── Separator ────────────────────────────────────────────────
            d.put("Separator.foreground",          BORDER_COLOR);

            // ── Progress bar ─────────────────────────────────────────────
            d.put("ProgressBar.background",        SURFACE);
            d.put("ProgressBar.foreground",        GOLD);

            // ── Option pane ──────────────────────────────────────────────
            d.put("OptionPane.background",         PANEL);

            // ── Fonts ────────────────────────────────────────────────────
            Font sans  = new Font("SansSerif",  Font.PLAIN, 12);
            Font mono  = new Font("Monospaced", Font.PLAIN, 12);
            Font serif = new Font("Serif",      Font.BOLD,  14);

            d.put("defaultFont",         sans);
            d.put("Label.font",          sans);
            d.put("Label.foreground",    TEXT_NORMAL);
            d.put("Button.font",         sans);
            d.put("TextField.font",      mono);
            d.put("TextArea.font",       mono);
            d.put("List.font",           mono);
            d.put("Tree.font",           sans);
            d.put("Table.font",          mono);
            d.put("ComboBox.font",       mono);
            d.put("TabbedPane.font",     sans);
            d.put("MenuBar.font",        sans);
            d.put("Menu.font",           sans);
            d.put("MenuItem.font",       sans);
            d.put("TitledBorder.font",   serif);

            return d;
        }
    }
}