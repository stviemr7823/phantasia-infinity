// phantasia-j2d/src/main/java/com/phantasia/j2d/engine/InputRouter.java
package com.phantasia.j2d.engine;

import java.awt.event.*;

/**
 * Single input listener registered on {@link GameCanvas}.
 *
 * <p>Routes keyboard and mouse events to the active {@link Screen} via
 * the {@link ScreenManager}. Coordinates are translated from physical
 * screen space to design-resolution space (1920×1080) before delivery.</p>
 *
 * <p>During fade transitions, input is suppressed — no events reach the
 * screen while the ScreenManager is fading.</p>
 *
 * <p>ESC key is intercepted here for global shutdown. All other keys
 * are forwarded to the active screen.</p>
 */
public class InputRouter implements KeyListener, MouseListener, MouseMotionListener {

    private final GameCanvas    canvas;
    private final ScreenManager screenManager;
    private final GameLoop      gameLoop;

    /** If true, ESC triggers clean shutdown instead of being forwarded. */
    private boolean escQuitsGame = true;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public InputRouter(GameCanvas canvas, ScreenManager screenManager, GameLoop gameLoop) {
        this.canvas        = canvas;
        this.screenManager = screenManager;
        this.gameLoop      = gameLoop;
    }

    /**
     * Registers this router as the listener on the canvas.
     * Call once after GameCanvas.enterFullscreen().
     */
    public void install() {
        canvas.addKeyListener(this);
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
        canvas.requestFocusInWindow();
    }

    /**
     * Controls whether ESC triggers shutdown or is forwarded to the screen.
     * Set to false when the pause screen handles ESC itself.
     */
    public void setEscQuitsGame(boolean escQuits) {
        this.escQuitsGame = escQuits;
    }

    // -------------------------------------------------------------------------
    // KeyListener
    // -------------------------------------------------------------------------

    @Override
    public void keyPressed(KeyEvent e) {
        if (escQuitsGame && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            gameLoop.stop();
            return;
        }
        if (screenManager.isFading()) return;

        Screen screen = screenManager.getActiveScreen();
        if (screen != null) {
            screen.onKeyPressed(e);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (screenManager.isFading()) return;
        Screen screen = screenManager.getActiveScreen();
        if (screen != null) {
            screen.onKeyReleased(e);
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Not used — keyPressed is sufficient
    }

    // -------------------------------------------------------------------------
    // MouseListener
    // -------------------------------------------------------------------------

    @Override
    public void mouseClicked(MouseEvent e) {
        if (screenManager.isFading()) return;
        Screen screen = screenManager.getActiveScreen();
        if (screen != null) {
            screen.onMouseClicked(toDesignSpace(e));
        }
    }

    @Override public void mousePressed(MouseEvent e)  {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e)  {}
    @Override public void mouseExited(MouseEvent e)   {}

    // -------------------------------------------------------------------------
    // MouseMotionListener
    // -------------------------------------------------------------------------

    @Override
    public void mouseMoved(MouseEvent e) {
        if (screenManager.isFading()) return;
        Screen screen = screenManager.getActiveScreen();
        if (screen != null) {
            screen.onMouseMoved(toDesignSpace(e));
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {}

    // -------------------------------------------------------------------------
    // Coordinate translation
    // -------------------------------------------------------------------------

    /**
     * Creates a new MouseEvent with coordinates mapped to design resolution.
     */
    private MouseEvent toDesignSpace(MouseEvent e) {
        int dx = canvas.toDesignX(e.getX());
        int dy = canvas.toDesignY(e.getY());
        return new MouseEvent(
                e.getComponent(), e.getID(), e.getWhen(), e.getModifiersEx(),
                dx, dy, e.getClickCount(), e.isPopupTrigger(), e.getButton()
        );
    }
}