// phantasia-j2d/src/main/java/com/phantasia/j2d/engine/GameCanvas.java
package com.phantasia.j2d.engine;

import java.awt.*;
import java.awt.image.BufferStrategy;

/**
 * The single AWT rendering surface for the j2d game frontend.
 *
 * <p>Replaces all Swing JPanels, CardLayouts, and JFrames from the prototype.
 * This Canvas goes fullscreen via {@link GraphicsDevice#setFullScreenWindow(Window)}
 * and uses triple buffering for tear-free rendering.</p>
 *
 * <h3>Design Resolution</h3>
 * All screens are designed at 1920×1080. On displays with different native
 * resolutions, the Canvas applies a uniform scale via {@link Graphics2D#scale(double, double)}
 * with letterboxing to maintain aspect ratio. Screens always paint in 1080p
 * coordinates regardless of physical resolution.
 *
 * <h3>Rendering Hints</h3>
 * The Graphics2D context is pre-configured with LCD sub-pixel antialiasing
 * and bicubic interpolation for high-quality text and image scaling.
 */
public class GameCanvas extends Canvas {

    // -------------------------------------------------------------------------
    // Design resolution
    // -------------------------------------------------------------------------

    public static final int DESIGN_WIDTH  = 1920;
    public static final int DESIGN_HEIGHT = 1080;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final Frame          frame;
    private final GraphicsDevice device;
    private BufferStrategy       bufferStrategy;

    // Scaling from design space to physical pixels
    private double scaleX = 1.0;
    private double scaleY = 1.0;
    private int    offsetX = 0;
    private int    offsetY = 0;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates the fullscreen game canvas.
     *
     * @param title  window title (visible in taskbar if the OS shows it)
     */
    public GameCanvas(String title) {
        this.device = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();
        this.frame = new Frame(title, device.getDefaultConfiguration());
        frame.setUndecorated(true);
        frame.setIgnoreRepaint(true);

        setIgnoreRepaint(true);
        setBackground(new Color(0x0D, 0x0D, 0x12)); // void color

        frame.add(this);
    }

    // -------------------------------------------------------------------------
    // Fullscreen lifecycle
    // -------------------------------------------------------------------------

    /**
     * Enters exclusive fullscreen mode and creates the buffer strategy.
     */
    public void enterFullscreen() {
        if (!device.isFullScreenSupported()) {
            System.err.println("[GameCanvas] Fullscreen not supported — using maximised window.");
            frame.setExtendedState(Frame.MAXIMIZED_BOTH);
            frame.setVisible(true);
        } else {
            device.setFullScreenWindow(frame);
        }

        // Triple buffering
        createBufferStrategy(3);
        bufferStrategy = getBufferStrategy();

        computeScaling();
    }

    /**
     * Exits fullscreen and disposes the frame.
     */
    public void shutdown() {
        device.setFullScreenWindow(null);
        frame.dispose();
    }

    /**
     * Returns the AWT Frame (needed for InputRouter registration).
     */
    public Frame getFrame() {
        return frame;
    }

    // -------------------------------------------------------------------------
    // Render cycle
    // -------------------------------------------------------------------------

    /**
     * Begins a render frame. Returns a Graphics2D pre-configured with:
     * <ul>
     *   <li>LCD sub-pixel antialiasing</li>
     *   <li>Bicubic interpolation</li>
     *   <li>Uniform scale + letterbox offset for design resolution</li>
     * </ul>
     *
     * The caller must call {@link #endFrame()} after painting.
     *
     * @return configured Graphics2D, or null if the buffer was lost
     */
    public Graphics2D beginFrame() {
        if (bufferStrategy == null) return null;

        Graphics2D g = (Graphics2D) bufferStrategy.getDrawGraphics();

        // Rendering hints
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        // Clear to void
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());

        // Apply letterbox offset and uniform scale
        g.translate(offsetX, offsetY);
        g.scale(scaleX, scaleY);

        return g;
    }

    /**
     * Completes the render frame — disposes the graphics and flips the buffer.
     */
    public void endFrame() {
        if (bufferStrategy == null) return;
        bufferStrategy.show();
        Toolkit.getDefaultToolkit().sync();
    }

    // -------------------------------------------------------------------------
    // Coordinate mapping (physical → design space, for input)
    // -------------------------------------------------------------------------

    /**
     * Converts a physical screen X coordinate to design-space X.
     */
    public int toDesignX(int physicalX) {
        return (int) ((physicalX - offsetX) / scaleX);
    }

    /**
     * Converts a physical screen Y coordinate to design-space Y.
     */
    public int toDesignY(int physicalY) {
        return (int) ((physicalY - offsetY) / scaleY);
    }

    // -------------------------------------------------------------------------
    // Scaling computation
    // -------------------------------------------------------------------------

    private void computeScaling() {
        int pw = getWidth();
        int ph = getHeight();
        if (pw <= 0 || ph <= 0) {
            pw = device.getDisplayMode().getWidth();
            ph = device.getDisplayMode().getHeight();
        }

        double scaleW = (double) pw / DESIGN_WIDTH;
        double scaleH = (double) ph / DESIGN_HEIGHT;
        double scale  = Math.min(scaleW, scaleH); // fit with letterbox

        scaleX  = scale;
        scaleY  = scale;
        offsetX = (int) ((pw - DESIGN_WIDTH * scale) / 2.0);
        offsetY = (int) ((ph - DESIGN_HEIGHT * scale) / 2.0);

        System.out.printf("[GameCanvas] Physical: %dx%d  Scale: %.3f  Offset: (%d, %d)%n",
                pw, ph, scale, offsetX, offsetY);
    }
}