// phantasia-core/src/main/java/com/phantasia/core/tools/PhantasiaEditor.java
package com.phantasia.core.tools;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Unified authoring suite for Phantasia: Infinity.
 * Each tab is an independent editor panel sharing a common window.
 *
 * The status bar is exposed via setStatus() so any panel can push
 * feedback messages without needing a direct reference to the frame.
 */
public class PhantasiaEditor extends JFrame {

    // Shared dark theme
    public static final Color BG_DARK    = new Color(30,  30,  35);
    public static final Color BG_PANEL   = new Color(40,  40,  48);
    public static final Color BG_LIST    = new Color(25,  25,  30);
    public static final Color ACCENT     = new Color(180, 130, 60);
    public static final Color TEXT_MAIN  = new Color(220, 210, 190);
    public static final Color TEXT_DIM   = new Color(140, 130, 115);
    public static final Color BORDER_COL = new Color(70,  65,  55);

    public static final Font FONT_HEADER = new Font("Serif",     Font.BOLD,  14);
    public static final Font FONT_LABEL  = new Font("SansSerif", Font.PLAIN, 12);
    public static final Font FONT_FIELD  = new Font("Monospaced",Font.PLAIN, 12);

    private final JLabel statusLabel;

    /** Singleton reference — panels can call PhantasiaEditor.instance().setStatus(). */
    private static PhantasiaEditor instance;

    public static PhantasiaEditor instance() { return instance; }

    public PhantasiaEditor() {
        instance = this;

        setTitle("Phantasia: Infinity — Editor Suite v0.1");
        setSize(1100, 780);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_DARK);
        header.setBorder(new EmptyBorder(10, 16, 6, 16));
        JLabel title = new JLabel("PHANTASIA: INFINITY  —  EDITOR SUITE");
        title.setFont(new Font("Serif", Font.BOLD, 16));
        title.setForeground(ACCENT);
        header.add(title, BorderLayout.WEST);
        JLabel version = new JLabel("core v0.2");
        version.setFont(FONT_LABEL);
        version.setForeground(TEXT_DIM);
        header.add(version, BorderLayout.EAST);

        // Tabbed pane — panels constructed here on the EDT
        // I/O happens inside each panel's constructor via autoLoad,
        // which is fast enough for typical file sizes.
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG_DARK);
        tabs.setForeground(TEXT_MAIN);
        tabs.setFont(FONT_LABEL);

        tabs.addTab("Monsters",  new MonsterEditorPanel());
        tabs.addTab("Items",     new ItemEditorPanel());
        tabs.addTab("Spells",    new SpellEditorPanel());
        tabs.addTab("World Map", new WorldEditorPanel());
        tabs.addTab("Dungeons",  new DungeonEditorPanel());

        // Status bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(BG_DARK);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL),
                new EmptyBorder(4, 12, 4, 12)));
        statusLabel = new JLabel("Ready.");
        statusLabel.setFont(FONT_LABEL);
        statusLabel.setForeground(TEXT_DIM);
        statusBar.add(statusLabel, BorderLayout.WEST);

        add(header,    BorderLayout.NORTH);
        add(tabs,      BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
    }

    /**
     * Updates the status bar message.
     * Safe to call from any thread — marshals to the EDT if needed.
     */
    public void setStatus(String message) {
        if (SwingUtilities.isEventDispatchThread()) {
            statusLabel.setText(message);
        } else {
            SwingUtilities.invokeLater(() -> statusLabel.setText(message));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new PhantasiaEditor().setVisible(true);
        });
    }
}