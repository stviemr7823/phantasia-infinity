// phantasia-j2d/src/main/java/com/phantasia/j2d/tour/TourSettingsDialog.java
package com.phantasia.j2d.tour;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeEvent;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 * A polished, dark-themed settings dialog for the Touring Engine.
 *
 * Visual design:
 *   - Dark parchment palette matching TourFrame's fantasy aesthetic
 *   - Custom-painted toggle switches (gold track, smooth knob)
 *   - Custom-painted sliders with gold fill and engraved tick marks
 *   - Grouped sections with decorative gold dividers
 *   - Hover effects and smooth visual feedback
 *
 * All changes apply immediately to TourSettings.get() so the effect
 * is visible in real time without closing the dialog.
 */
public class TourSettingsDialog extends JDialog {

    // =====================================================================
    // Theme — extends TourFrame palette with dialog-specific colours
    // =====================================================================

    private static final Color BG_DEEP       = new Color(18, 15, 24);
    private static final Color BG_SECTION    = new Color(26, 22, 34);
    private static final Color BG_ROW_HOVER  = new Color(34, 29, 44);
    private static final Color GOLD          = new Color(190, 145, 55);
    private static final Color GOLD_DIM      = new Color(130, 100, 45);
    private static final Color GOLD_BRIGHT   = new Color(225, 185, 80);
    private static final Color TEXT_PRIMARY   = new Color(215, 205, 185);
    private static final Color TEXT_SECONDARY = new Color(140, 132, 115);
    private static final Color TEXT_HINT      = new Color(90, 84, 72);
    private static final Color BORDER_SUBTLE  = new Color(55, 48, 68);
    private static final Color DIVIDER        = new Color(70, 58, 40);
    private static final Color TOGGLE_OFF_BG  = new Color(50, 44, 60);
    private static final Color TOGGLE_ON_BG   = new Color(160, 120, 40);
    private static final Color KNOB_COLOR     = new Color(240, 230, 210);
    private static final Color SLIDER_TRACK   = new Color(50, 44, 60);
    private static final Color SLIDER_FILL    = new Color(170, 130, 50);
    private static final Color RED_ACCENT     = new Color(200, 75, 65);
    private static final Color GREEN_ACCENT   = new Color(75, 185, 90);

    private static final Font FONT_SECTION  = new Font("Serif", Font.BOLD, 15);
    private static final Font FONT_LABEL    = new Font("SansSerif", Font.PLAIN, 13);
    private static final Font FONT_HINT     = new Font("SansSerif", Font.ITALIC, 11);
    private static final Font FONT_VALUE    = new Font("Monospaced", Font.BOLD, 12);
    private static final Font FONT_HEADER   = new Font("Serif", Font.BOLD, 18);

    // =====================================================================
    // Construction
    // =====================================================================

    public TourSettingsDialog(Frame owner) {
        super(owner, "Touring Engine Settings", false);  // non-modal
        setSize(480, 680);
        setLocationRelativeTo(owner);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DEEP);
        root.setBorder(new CompoundBorder(
                new LineBorder(BORDER_SUBTLE, 1),
                new EmptyBorder(0, 0, 0, 0)));

        root.add(buildHeader(),  BorderLayout.NORTH);
        root.add(buildBody(),    BorderLayout.CENTER);
        root.add(buildFooter(),  BorderLayout.SOUTH);

        setContentPane(root);
    }

    // =====================================================================
    // Header
    // =====================================================================

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                // Subtle gradient: slightly lighter at top
                g2.setPaint(new GradientPaint(0, 0, new Color(30, 26, 40),
                        0, getHeight(), BG_DEEP));
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Gold bottom border line
                g2.setColor(DIVIDER);
                g2.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        };
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(16, 20, 14, 20));

        JLabel title = new JLabel("\u2699  SETTINGS");
        title.setFont(FONT_HEADER);
        title.setForeground(GOLD);

        JLabel subtitle = new JLabel("Touring Engine  ·  Designer Controls");
        subtitle.setFont(FONT_HINT);
        subtitle.setForeground(TEXT_SECONDARY);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(title);
        text.add(Box.createVerticalStrut(2));
        text.add(subtitle);

        header.add(text, BorderLayout.WEST);
        return header;
    }

    // =====================================================================
    // Scrollable body
    // =====================================================================

    private JScrollPane buildBody() {
        TourSettings s = TourSettings.get();

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(BG_DEEP);
        body.setBorder(new EmptyBorder(6, 16, 6, 16));

        // ---- Encounters ----
        body.add(sectionHeader("ENCOUNTERS"));

        body.add(toggleRow("Random Encounters",
                "Enable or disable random battles on the overworld and in dungeons",
                s.isEncountersEnabled(),
                s::setEncountersEnabled));

        body.add(sliderRow("Encounter Rate",
                "Multiplier for encounter frequency (100% = normal)",
                s.getEncounterRatePercent(), 0, 300, "%",
                s::setEncounterRatePercent));

        body.add(sectionSpacer());

        // ---- Combat ----
        body.add(sectionHeader("COMBAT"));

        body.add(toggleRow("Auto-Win Battles",
                "Instantly win all encounters — skip combat entirely",
                s.isAutoWin(),
                s::setAutoWin));

        body.add(toggleRow("God Mode",
                "Party takes no damage during combat",
                s.isGodMode(),
                s::setGodMode));

        body.add(sectionSpacer());

        // ---- Dungeons ----
        body.add(sectionHeader("DUNGEONS"));

        body.add(toggleRow("Fog of War",
                "Hide unexplored dungeon tiles (disable to reveal entire floor)",
                s.isFogOfWarEnabled(),
                s::setFogOfWarEnabled));

        body.add(sliderRow("Torch Radius",
                "Visibility radius in tiles around the party",
                s.getTorchRadius(), 1, 10, " tiles",
                s::setTorchRadius));

        body.add(sectionSpacer());

        // ---- Map Overlays ----
        body.add(sectionHeader("MAP OVERLAYS"));

        body.add(toggleRow("Grid Coordinates",
                "Show (x, y) coordinates on each visible tile",
                s.isShowGridCoords(),
                s::setShowGridCoords));

        body.add(toggleRow("Feature Labels",
                "Show town and dungeon names above feature markers",
                s.isShowFeatureLabels(),
                s::setShowFeatureLabels));

        body.add(sectionSpacer());

        // ---- Display ----
        body.add(sectionHeader("DISPLAY"));

        body.add(sliderRow("Game Speed",
                "Tick speed multiplier (100% = normal, 400% = fast-forward)",
                s.getGameSpeedPercent(), 50, 400, "%",
                s::setGameSpeedPercent));

        body.add(Box.createVerticalStrut(16));

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(BG_DEEP);

        // Style the scroll bar
        scroll.getVerticalScrollBar().setBackground(BG_DEEP);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));

        return scroll;
    }

    // =====================================================================
    // Footer
    // =====================================================================

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(DIVIDER);
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        footer.setBackground(BG_DEEP);
        footer.setBorder(new EmptyBorder(10, 20, 12, 20));

        JButton reset = styledButton("Reset Defaults", TEXT_SECONDARY, BORDER_SUBTLE);
        reset.addActionListener(e -> resetDefaults());

        JButton close = styledButton("Close", GOLD, DIVIDER);
        close.addActionListener(e -> dispose());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(reset);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(close);

        footer.add(left,  BorderLayout.WEST);
        footer.add(right, BorderLayout.EAST);

        return footer;
    }

    // =====================================================================
    // Section header — gold text with decorative line
    // =====================================================================

    private JPanel sectionHeader(String text) {
        JPanel row = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                // Decorative line to the right of the label
                FontMetrics fm = g2.getFontMetrics(FONT_SECTION);
                int textW = fm.stringWidth(text) + 14;
                int lineY = getHeight() / 2;
                g2.setColor(DIVIDER);
                g2.setStroke(new BasicStroke(1));
                g2.drawLine(textW, lineY, getWidth() - 4, lineY);
            }
        };
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(14, 2, 6, 2));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_SECTION);
        lbl.setForeground(GOLD);
        row.add(lbl, BorderLayout.WEST);

        return row;
    }

    private Component sectionSpacer() {
        return Box.createVerticalStrut(4);
    }

    // =====================================================================
    // Toggle row — label + hint + custom-painted toggle switch
    // =====================================================================

    private JPanel toggleRow(String label, String hint,
                             boolean initial, BooleanSetter setter) {
        JPanel row = hoverRow();

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel lbl = new JLabel(label);
        lbl.setFont(FONT_LABEL);
        lbl.setForeground(TEXT_PRIMARY);
        lbl.setAlignmentX(LEFT_ALIGNMENT);

        JLabel hnt = new JLabel(hint);
        hnt.setFont(FONT_HINT);
        hnt.setForeground(TEXT_HINT);
        hnt.setAlignmentX(LEFT_ALIGNMENT);

        text.add(lbl);
        text.add(Box.createVerticalStrut(1));
        text.add(hnt);

        ToggleSwitch toggle = new ToggleSwitch(initial);
        toggle.addActionListener(e -> setter.set(toggle.isOn()));

        row.add(text,   BorderLayout.CENTER);
        row.add(toggle, BorderLayout.EAST);
        return row;
    }

    // =====================================================================
    // Slider row — label + hint + custom slider + value display
    // =====================================================================

    private JPanel sliderRow(String label, String hint,
                             int initial, int min, int max, String suffix,
                             IntSetter setter) {
        JPanel row = hoverRow();

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel lbl = new JLabel(label);
        lbl.setFont(FONT_LABEL);
        lbl.setForeground(TEXT_PRIMARY);
        lbl.setAlignmentX(LEFT_ALIGNMENT);

        JLabel hnt = new JLabel(hint);
        hnt.setFont(FONT_HINT);
        hnt.setForeground(TEXT_HINT);
        hnt.setAlignmentX(LEFT_ALIGNMENT);

        text.add(lbl);
        text.add(Box.createVerticalStrut(1));
        text.add(hnt);

        // Slider + value label side by side
        JLabel valLabel = new JLabel(initial + suffix);
        valLabel.setFont(FONT_VALUE);
        valLabel.setForeground(GOLD_BRIGHT);
        valLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        valLabel.setPreferredSize(new Dimension(60, 20));

        JSlider slider = new JSlider(min, max, initial);
        slider.setOpaque(false);
        slider.setPreferredSize(new Dimension(160, 28));
        slider.setUI(new PhantasiaSliderUI(slider));
        slider.addChangeListener((ChangeEvent e) -> {
            int v = slider.getValue();
            valLabel.setText(v + suffix);
            setter.set(v);
        });

        JPanel control = new JPanel(new BorderLayout(6, 0));
        control.setOpaque(false);
        control.add(slider,   BorderLayout.CENTER);
        control.add(valLabel, BorderLayout.EAST);

        JPanel full = new JPanel(new BorderLayout(0, 4));
        full.setOpaque(false);
        full.add(text,    BorderLayout.NORTH);
        full.add(control, BorderLayout.SOUTH);

        row.add(full, BorderLayout.CENTER);
        return row;
    }

    // =====================================================================
    // Hoverable row container
    // =====================================================================

    private JPanel hoverRow() {
        JPanel row = new JPanel(new BorderLayout(12, 0)) {
            private boolean hovered = false;

            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) {
                        hovered = true; repaint();
                    }
                    public void mouseExited(MouseEvent e) {
                        hovered = false; repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                if (hovered) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(BG_ROW_HOVER);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                }
            }
        };
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(8, 10, 8, 10));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        return row;
    }

    // =====================================================================
    // Custom toggle switch component
    // =====================================================================

    private static class ToggleSwitch extends JComponent {

        private boolean on;
        private float   animPos;          // 0.0 = off, 1.0 = on
        private Timer   animator;

        ToggleSwitch(boolean initial) {
            this.on      = initial;
            this.animPos = initial ? 1f : 0f;

            setPreferredSize(new Dimension(48, 26));
            setMinimumSize(getPreferredSize());
            setMaximumSize(getPreferredSize());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    on = !on;
                    animateToggle();
                    fireActionEvent();
                    repaint();
                }
            });
        }

        boolean isOn() { return on; }

        void addActionListener(ActionListener l) {
            listenerList.add(ActionListener.class, l);
        }

        private void fireActionEvent() {
            for (ActionListener l : listenerList.getListeners(ActionListener.class))
                l.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "toggle"));
        }

        private void animateToggle() {
            if (animator != null && animator.isRunning()) animator.stop();
            float target = on ? 1f : 0f;
            animator = new Timer(12, e -> {
                animPos += (target - animPos) * 0.35f;
                if (Math.abs(animPos - target) < 0.02f) {
                    animPos = target;
                    ((Timer) e.getSource()).stop();
                }
                repaint();
            });
            animator.start();
        }

        @Override
        protected void paintComponent(Graphics gx) {
            Graphics2D g = (Graphics2D) gx;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int trackH = 18;
            int trackY = (h - trackH) / 2;
            int arc     = trackH;

            // Track — blend between off and on colors
            Color trackColor = blend(TOGGLE_OFF_BG, TOGGLE_ON_BG, animPos);
            g.setColor(trackColor);
            g.fillRoundRect(2, trackY, w - 4, trackH, arc, arc);

            // Inner shadow at top of track
            g.setColor(new Color(0, 0, 0, (int)(40 * (1 - animPos))));
            g.fillRoundRect(2, trackY, w - 4, trackH / 2, arc, arc);

            // Knob
            int knobD  = trackH + 4;
            int knobY  = trackY - 2;
            int knobMinX = 0;
            int knobMaxX = w - knobD;
            int knobX = (int)(knobMinX + (knobMaxX - knobMinX) * animPos);

            // Knob shadow
            g.setColor(new Color(0, 0, 0, 60));
            g.fillOval(knobX + 1, knobY + 2, knobD, knobD);

            // Knob body
            g.setColor(KNOB_COLOR);
            g.fillOval(knobX, knobY, knobD, knobD);

            // Knob highlight
            g.setColor(new Color(255, 255, 255, 80));
            g.fillOval(knobX + 4, knobY + 3, knobD / 2, knobD / 3);
        }

        private static Color blend(Color a, Color b, float t) {
            float s = 1 - t;
            return new Color(
                    clamp((int)(a.getRed()   * s + b.getRed()   * t)),
                    clamp((int)(a.getGreen() * s + b.getGreen() * t)),
                    clamp((int)(a.getBlue()  * s + b.getBlue()  * t)));
        }

        private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
    }

    // =====================================================================
    // Custom slider UI — gold-filled track, rounded thumb
    // =====================================================================

    private static class PhantasiaSliderUI extends BasicSliderUI {

        PhantasiaSliderUI(JSlider slider) { super(slider); }

        @Override public void paintTrack(Graphics gx) {
            Graphics2D g = (Graphics2D) gx;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            Rectangle tr = trackRect;
            int trackH = 6;
            int y = tr.y + (tr.height - trackH) / 2;

            // Full track background
            g.setColor(SLIDER_TRACK);
            g.fillRoundRect(tr.x, y, tr.width, trackH, trackH, trackH);

            // Filled portion
            int fillW = thumbRect.x - tr.x + thumbRect.width / 2;
            if (fillW > 0) {
                g.setColor(SLIDER_FILL);
                g.fillRoundRect(tr.x, y, fillW, trackH, trackH, trackH);
            }
        }

        @Override public void paintThumb(Graphics gx) {
            Graphics2D g = (Graphics2D) gx;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = thumbRect.x + thumbRect.width / 2;
            int cy = thumbRect.y + thumbRect.height / 2;
            int r  = 7;

            // Shadow
            g.setColor(new Color(0, 0, 0, 50));
            g.fillOval(cx - r + 1, cy - r + 2, r * 2, r * 2);

            // Body
            g.setColor(KNOB_COLOR);
            g.fillOval(cx - r, cy - r, r * 2, r * 2);

            // Gold ring
            g.setColor(GOLD_DIM);
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(cx - r, cy - r, r * 2, r * 2);

            // Highlight
            g.setColor(new Color(255, 255, 255, 70));
            g.fillOval(cx - r + 2, cy - r + 2, r, r / 2 + 1);
        }

        @Override public void paintFocus(Graphics g) { /* suppress */ }
    }

    // =====================================================================
    // Styled button factory
    // =====================================================================

    private JButton styledButton(String text, Color fg, Color border) {
        JButton btn = new JButton(text) {
            private boolean hovered = false;

            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                    public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
                });
            }

            @Override
            protected void paintComponent(Graphics gx) {
                Graphics2D g = (Graphics2D) gx;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                Color bg = hovered ? new Color(45, 40, 56) : new Color(35, 30, 44);
                g.setColor(bg);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

                g.setColor(border);
                g.setStroke(new BasicStroke(1));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

                g.setColor(getForeground());
                g.setFont(getFont());
                FontMetrics fm = g.getFontMetrics();
                int tx = (getWidth()  - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g.drawString(getText(), tx, ty);
            }
        };
        btn.setFont(FONT_LABEL);
        btn.setForeground(fg);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(130, 34));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // =====================================================================
    // Reset
    // =====================================================================

    private void resetDefaults() {
        TourSettings s = TourSettings.get();
        s.setEncountersEnabled(true);
        s.setEncounterRatePercent(100);
        s.setAutoWin(false);
        s.setGodMode(false);
        s.setFogOfWarEnabled(true);
        s.setTorchRadius(3);
        s.setShowGridCoords(false);
        s.setShowFeatureLabels(false);
        s.setGameSpeedPercent(100);

        // Rebuild the body to reflect reset values
        Container root = getContentPane();
        root.removeAll();
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(),   BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        root.revalidate();
        root.repaint();
    }

    // =====================================================================
    // Functional interfaces
    // =====================================================================

    @FunctionalInterface
    private interface BooleanSetter { void set(boolean v); }

    @FunctionalInterface
    private interface IntSetter { void set(int v); }
}