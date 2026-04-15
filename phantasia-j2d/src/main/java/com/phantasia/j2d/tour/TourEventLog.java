// phantasia-j2d/src/main/java/com/phantasia/j2d/tour/TourEventLog.java
package com.phantasia.j2d.tour;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent scrolling diagnostic log — always visible in the right panel
 * of TourFrame regardless of which card is active.
 *
 * Each entry is stamped with a timestamp and a category tag. Categories
 * are color-coded so the designer can scan for specific event types at a
 * glance without reading every line.
 *
 * CATEGORY COLOR SCHEME:
 *   MOVE      — dim white  (high frequency, low priority)
 *   ENCOUNTER — orange     (primary test signal)
 *   TOWN      — gold       (feature trigger)
 *   DUNGEON   — purple     (feature trigger)
 *   COMBAT    — red        (combat lifecycle)
 *   TILE EVENT— cyan       (interactive tile)
 *   SAVE      — green      (persistence)
 *   SESSION   — blue       (session lifecycle)
 *   BLOCKED   — grey       (movement failure)
 *   ERROR     — bright red (failure)
 *   INFO      — light grey (general)
 *
 * Thread safety:
 *   log() marshals to the EDT so it is safe to call from the game loop
 *   thread or any background thread.
 */
public class TourEventLog extends JScrollPane {

    // -------------------------------------------------------------------------
    // Category colors
    // -------------------------------------------------------------------------

    private static final Map<String, Color> CATEGORY_COLORS = new HashMap<>();
    static {
        CATEGORY_COLORS.put("MOVE",       new Color(160, 155, 140));
        CATEGORY_COLORS.put("ENCOUNTER",  new Color(220, 130,  40));
        CATEGORY_COLORS.put("TOWN",       new Color(200, 180,  60));
        CATEGORY_COLORS.put("DUNGEON",    new Color(160,  90, 200));
        CATEGORY_COLORS.put("COMBAT",     new Color(220,  70,  70));
        CATEGORY_COLORS.put("TILE EVENT", new Color( 80, 200, 200));
        CATEGORY_COLORS.put("SAVE",       new Color( 80, 200, 100));
        CATEGORY_COLORS.put("SESSION",    new Color( 80, 140, 220));
        CATEGORY_COLORS.put("BLOCKED",    new Color(110, 105,  95));
        CATEGORY_COLORS.put("ERROR",      new Color(255,  60,  60));
        CATEGORY_COLORS.put("INFO",       new Color(180, 175, 165));
    }

    private static final Color  BG_COLOR     = new Color(18, 18, 22);
    private static final Color  TIMESTAMP_FG = new Color(90, 85, 75);
    private static final Color  TEXT_FG      = new Color(200, 195, 180);
    private static final Font   LOG_FONT     = new Font(Font.MONOSPACED, Font.PLAIN, 11);

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final int MAX_LINES = 500;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final JTextPane  textPane;
    private final StyledDocument doc;
    private       int        lineCount = 0;

    // Pre-built styles
    private final Style timestampStyle;
    private final Style textStyle;
    private final Map<String, Style> categoryStyles = new HashMap<>();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public TourEventLog() {
        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setBackground(BG_COLOR);
        textPane.setFont(LOG_FONT);
        textPane.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        doc = textPane.getStyledDocument();

        // Build styles
        timestampStyle = doc.addStyle("timestamp", null);
        StyleConstants.setForeground(timestampStyle, TIMESTAMP_FG);
        StyleConstants.setFontFamily(timestampStyle, Font.MONOSPACED);
        StyleConstants.setFontSize(timestampStyle, 11);

        textStyle = doc.addStyle("text", null);
        StyleConstants.setForeground(textStyle, TEXT_FG);
        StyleConstants.setFontFamily(textStyle, Font.MONOSPACED);
        StyleConstants.setFontSize(textStyle, 11);

        for (Map.Entry<String, Color> entry : CATEGORY_COLORS.entrySet()) {
            Style s = doc.addStyle("cat_" + entry.getKey(), null);
            StyleConstants.setForeground(s, entry.getValue());
            StyleConstants.setBold(s, true);
            StyleConstants.setFontFamily(s, Font.MONOSPACED);
            StyleConstants.setFontSize(s, 11);
            categoryStyles.put(entry.getKey(), s);
        }

        setViewportView(textPane);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setBorder(BorderFactory.createEmptyBorder());
        getViewport().setBackground(BG_COLOR);

        // Seed with a startup message
        log("SESSION", "Touring engine started.");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Appends a log entry with a timestamp and colored category tag.
     * Thread-safe — marshals to EDT automatically.
     *
     * @param category  one of the named categories (e.g. "ENCOUNTER", "MOVE")
     * @param message   the diagnostic message
     */
    public void log(String category, String message) {
        if (SwingUtilities.isEventDispatchThread()) {
            appendEntry(category, message);
        } else {
            SwingUtilities.invokeLater(() -> appendEntry(category, message));
        }
    }

    /**
     * Logs at INFO level — convenience overload for general messages.
     */
    public void log(String message) {
        log("INFO", message);
    }

    /**
     * Clears all log entries.
     */
    public void clear() {
        SwingUtilities.invokeLater(() -> {
            try {
                doc.remove(0, doc.getLength());
                lineCount = 0;
                log("SESSION", "Log cleared.");
            } catch (BadLocationException ignored) {}
        });
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private void appendEntry(String category, String message) {
        // Trim oldest entries if we're over the line cap
        if (lineCount >= MAX_LINES) {
            pruneOldestLine();
        }

        try {
            String timestamp = LocalTime.now().format(TIME_FMT);
            String catPadded = String.format("%-10s", category);

            doc.insertString(doc.getLength(), timestamp + " ", timestampStyle);

            Style catStyle = categoryStyles.getOrDefault(category, textStyle);
            doc.insertString(doc.getLength(), catPadded + " ", catStyle);

            doc.insertString(doc.getLength(), message + "\n", textStyle);

            lineCount++;

            // Auto-scroll to bottom
            textPane.setCaretPosition(doc.getLength());

        } catch (BadLocationException ignored) {}
    }

    private void pruneOldestLine() {
        try {
            Element root    = doc.getDefaultRootElement();
            Element first   = root.getElement(0);
            int     end     = first.getEndOffset();
            doc.remove(0, end);
            lineCount--;
        } catch (BadLocationException ignored) {}
    }
}