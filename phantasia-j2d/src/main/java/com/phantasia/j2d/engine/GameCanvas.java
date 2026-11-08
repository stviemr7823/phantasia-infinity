package com.phantasia.j2d.engine;

import java.awt.*;
import java.awt.image.BufferStrategy;

/**
 * The single AWT rendering surface for the j2d game frontend.
 */
public class GameCanvas extends Canvas {

    public static final int DESIGN_WIDTH  = 1920;
    public static final int DESIGN_HEIGHT = 1080;

    private Frame frame;

    public Graphics2D beginFrame() {
        if (getBufferStrategy() == null) {
            createBufferStrategy(3);
        }

        BufferStrategy bs = getBufferStrategy();
        Graphics2D g = (Graphics2D) bs.getDrawGraphics();

        // High-performance hints for 1:1 bit-blitting
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Letterboxing and Scaling Logic
        double scaleX = (double) getWidth() / DESIGN_WIDTH;
        double scaleY = (double) getHeight() / DESIGN_HEIGHT;
        double scale = Math.min(scaleX, scaleY);

        int xOffset = (int) ((getWidth() - (DESIGN_WIDTH * scale)) / 2);
        int yOffset = (int) ((getHeight() - (DESIGN_HEIGHT * scale)) / 2);

        // Clear the physical screen
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());

        // Setup the design canvas
        g.translate(xOffset, yOffset);
        g.scale(scale, scale);
        g.setClip(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);

        return g; // Return the 'brush' to the GameLoop
    }

    public void endFrame(Graphics2D g) {
        if (g != null) {
            g.dispose();
            if (getBufferStrategy() != null) {
                getBufferStrategy().show();
            }
        }
    }

    /**
     * Converts a raw screen X coordinate to design-space (1920×1080).
     */
    public int toDesignX(int screenX) {
        double scaleX = (double) getWidth()  / DESIGN_WIDTH;
        double scaleY = (double) getHeight() / DESIGN_HEIGHT;
        double scale  = Math.min(scaleX, scaleY);
        int    xOff   = (int)((getWidth() - DESIGN_WIDTH  * scale) / 2);
        return (int)((screenX - xOff) / scale);
    }

    /**
     * Converts a raw screen Y coordinate to design-space (1920×1080).
     */
    public int toDesignY(int screenY)
    {
        double scaleX = (double) getWidth() / DESIGN_WIDTH;
        double scaleY = (double) getHeight() / DESIGN_HEIGHT;
        double scale = Math.min(scaleX, scaleY);
        int yOff = (int) ((getHeight() - DESIGN_HEIGHT * scale) / 2);
        return (int) ((screenY - yOff) / scale);
    }

    public void enterFullscreen()
    {
        frame = new Frame("Phantasia: Infinity");
        frame.setUndecorated(true);
        frame.add(this);
        frame.setVisible(true);

        GraphicsDevice gd = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();

        if (gd.isFullScreenSupported())
        {
            gd.setFullScreenWindow(frame);
        } else
        {
            // Fallback: maximized window
            frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        }

        // Canvas needs to be displayable before createBufferStrategy
        createBufferStrategy(3);
    }

    /** Resizable diagnostic window. Call on the AWT event thread. */
    public void enterWindowed(String title, int width, int height, Runnable onClose) {
        if (!EventQueue.isDispatchThread()) throw new IllegalStateException("Window creation must run on the AWT event thread");
        if (frame != null) throw new IllegalStateException("Canvas already has a window");
        frame = new Frame(title);
        setPreferredSize(new Dimension(width, height)); frame.add(this); frame.pack();
        frame.setMinimumSize(new Dimension(640, 360)); frame.setLocationRelativeTo(null);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent event) { onClose.run(); }
        });
        frame.setVisible(true); createBufferStrategy(3); requestFocusInWindow();
    }

    public void shutdown() {
        GraphicsDevice gd = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();
        if (gd.getFullScreenWindow() == frame) {
            gd.setFullScreenWindow(null);
        }
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }
}
