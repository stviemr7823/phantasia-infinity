// phantasia-editor/src/main/java/com/phantasia/editor/EditorStatusBar.java
package com.phantasia.editor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Status bar at the bottom of the editor workbench.
 *
 * <p>Shows a message on the left (set by any panel via {@link #setMessage})
 * and the dirty record count on the right (updated automatically via
 * {@link EditorStateListener}).</p>
 */
public class EditorStatusBar extends JPanel implements EditorStateListener {

    private final JLabel messageLabel;
    private final JLabel dirtyLabel;

    public EditorStatusBar() {
        setLayout(new BorderLayout());
        setBackground(EditorTheme.BG_BASE_SOLID);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, EditorTheme.BORDER),
                new EmptyBorder(4, 12, 4, 12)));

        messageLabel = new JLabel("Ready.");
        messageLabel.setFont(EditorTheme.FONT_SMALL);
        messageLabel.setForeground(EditorTheme.TEXT_DIM);

        dirtyLabel = new JLabel("");
        dirtyLabel.setFont(EditorTheme.FONT_SMALL);
        dirtyLabel.setForeground(EditorTheme.TEXT_DIM);
        dirtyLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        add(messageLabel, BorderLayout.WEST);
        add(dirtyLabel,   BorderLayout.EAST);

        EditorState.get().addListener(this);
    }

    /** Updates the status message. Safe from any thread. */
    public void setMessage(String message) {
        if (SwingUtilities.isEventDispatchThread()) {
            messageLabel.setText(message);
        } else {
            SwingUtilities.invokeLater(() -> messageLabel.setText(message));
        }
    }

    @Override
    public void onDirtyCountChanged(int dirtyCount) {
        if (dirtyCount == 0) {
            dirtyLabel.setText("");
        } else {
            dirtyLabel.setText("[dirty: " + dirtyCount + "]");
            dirtyLabel.setForeground(EditorTheme.ACCENT);
        }
    }

    @Override
    public void onProjectLoaded() {
        setMessage("Project loaded.");
        dirtyLabel.setText("");
    }
}
