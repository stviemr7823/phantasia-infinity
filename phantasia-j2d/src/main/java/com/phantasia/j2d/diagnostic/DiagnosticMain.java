package com.phantasia.j2d.diagnostic;

import com.phantasia.j2d.engine.*;
import javax.imageio.ImageIO;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Resizable test launcher with a capped, single-threaded AWT update/render loop. */
public final class DiagnosticMain {
    private DiagnosticMain() {}
    public static void main(String[] args) throws Exception {
        boolean check = false, smoke = false; Path render = null, source = null, export = null, nativeSave = null; Integer entry = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--check" -> check = true;
                case "--window-smoke" -> smoke = true;
                case "--render-dir" -> { if (++i == args.length) throw new IllegalArgumentException("--render-dir needs a new directory"); render = Path.of(args[i]); }
                case "--content-dir" -> { if (++i == args.length) throw new IllegalArgumentException("--content-dir needs a directory"); source = Path.of(args[i]); }
                case "--export-dir" -> { if (++i == args.length) throw new IllegalArgumentException("--export-dir needs a new directory"); export = Path.of(args[i]); }
                case "--native-save" -> { if (++i == args.length) throw new IllegalArgumentException("--native-save needs a path"); nativeSave = Path.of(args[i]); }
                case "--map" -> { if (++i == args.length) throw new IllegalArgumentException("--map needs an ID"); entry = Integer.valueOf(args[i]); }
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        if (source != null && (export != null || entry == null)) throw new IllegalArgumentException("--content-dir requires --map and cannot be combined with --export-dir");
        if (smoke && (check || render != null)) throw new IllegalArgumentException("--window-smoke cannot be combined with headless modes");
        if (nativeSave != null && (check || render != null || export != null)) throw new IllegalArgumentException("--native-save is a window option");
        Path temporary = source == null && export == null ? Files.createTempDirectory("phantasia_mockup_") : null;
        try {
            DiagnosticContent.Bundle content = source != null ? DiagnosticContent.load(source)
                    : DiagnosticContent.exportAndLoad(DiagnosticContent.create(), export != null ? export : temporary.resolve("content"));
            int mapId = entry == null ? DiagnosticContent.SETTLEMENT : entry;
            if (content.maps().get(mapId) == null) throw new IllegalArgumentException("Entry map #" + mapId + " does not exist");
            var app = new DiagnosticApplication(content, mapId, source == null, () -> {});
            // Resolve the requested start before constructing any window.
            app.enter(mapId);
            if (render != null) render(content, mapId, source == null, render);
            if (check || render != null) {
                System.out.println("DIAGNOSTIC_CHECK maps=" + content.maps().size() + " npcs=" + content.npcs().size() + " shops=" + content.shops().size() + " PASS"); return;
            }
            if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("No graphical display. Use --check or --render-dir, or run from a desktop session.");
            window(content, mapId, source == null, smoke, nativeSave);
        } finally {
            if (temporary != null) try (var files = Files.walk(temporary)) { for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); }
        }
    }
    private static void render(DiagnosticContent.Bundle content, int entryMap, boolean fixture, Path directory) throws Exception {
        Files.createDirectory(directory);
        var app = new DiagnosticApplication(content, entryMap, fixture, () -> {}); image(app, directory.resolve("menu.png"));
        for (var map : content.maps().getAll()) { app.enter(map.getId()); image(app, directory.resolve("map-" + map.getId() + ".png")); }
    }
    private static void image(DiagnosticApplication app, Path path) throws Exception {
        var image = new BufferedImage(GameCanvas.DESIGN_WIDTH, GameCanvas.DESIGN_HEIGHT, BufferedImage.TYPE_INT_RGB); var g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            app.manager().render(g, image.getWidth(), image.getHeight());
        } finally { g.dispose(); }
        if (!ImageIO.write(image, "png", path.toFile())) throw new IllegalStateException("PNG encoder unavailable");
    }
    private static void window(DiagnosticContent.Bundle content, int mapId, boolean fixture, boolean smoke, Path nativeSave) throws Exception {
        var closed = new CountDownLatch(1); var failure = new AtomicReference<Throwable>();
        var active = new AtomicReference<WindowHost>();
        EventQueue.invokeAndWait(() -> {
            var host = new WindowHost(content, mapId, fixture, smoke, closed, failure, nativeSave);
            active.set(host);
            try { host.open(); }
            catch (Throwable error) { failure.set(error); host.close(); }
        });
        if (smoke) {
            if (!closed.await(15, TimeUnit.SECONDS)) {
                EventQueue.invokeLater(() -> active.get().close());
                throw new IllegalStateException("Window smoke test timed out");
            }
        } else closed.await();
        if (failure.get() != null) throw new IllegalStateException("Diagnostic window failed", failure.get());
        if (smoke) System.out.println("DIAGNOSTIC_WINDOW frames=30 PASS");
    }
    private static final class WindowHost {
        private final GameCanvas canvas = new GameCanvas();
        private final DiagnosticApplication app;
        private final boolean smoke;
        private final int mapId;
        private final CountDownLatch closed;
        private final AtomicReference<Throwable> failure;
        private final Set<Integer> held = new HashSet<>();
        private Timer timer;
        private int frames;
        private boolean closing;
        WindowHost(DiagnosticContent.Bundle content, int mapId, boolean fixture, boolean smoke, CountDownLatch closed, AtomicReference<Throwable> failure, Path nativeSave) {
            this.smoke = smoke; this.mapId = mapId; this.closed = closed; this.failure = failure;
            app = new DiagnosticApplication(content, mapId, fixture, this::close); app.setNativeSavePath(nativeSave);
        }
        void open() {
            canvas.enterWindowed("Phantasia Infinity — diagnostic mockup", 1280, 720, this::close);
            canvas.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) { if (!app.manager().isFading()) { held.add(e.getKeyCode()); var screen = app.manager().getActiveScreen(); if (screen != null) screen.onKeyPressed(e); } }
                @Override public void keyReleased(KeyEvent e) { held.remove(e.getKeyCode()); var screen = app.manager().getActiveScreen(); if (screen != null) screen.onKeyReleased(e); }
            });
            canvas.addFocusListener(new FocusAdapter() {
                @Override public void focusLost(FocusEvent e) {
                    for (int key : Set.copyOf(held)) { var screen = app.manager().getActiveScreen(); if (screen != null) screen.onKeyReleased(new KeyEvent(canvas, KeyEvent.KEY_RELEASED, 0, 0, key, KeyEvent.CHAR_UNDEFINED)); }
                    held.clear();
                }
            });
            canvas.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (app.manager().isFading()) return;
                    var screen = app.manager().getActiveScreen();
                    if (screen != null) screen.onMouseClicked(new MouseEvent(canvas, e.getID(), e.getWhen(), e.getModifiersEx(), canvas.toDesignX(e.getX()), canvas.toDesignY(e.getY()), e.getClickCount(), e.isPopupTrigger(), e.getButton()));
                }
            });
            if (smoke) app.enter(mapId);
            timer = new Timer(1000 / 30, event -> tick()); timer.setCoalesce(true); timer.start(); canvas.requestFocusInWindow();
        }
        void tick() {
            if (closing) return;
            try {
                app.manager().update(1f / 30);
                if (closing) return;
                var g = canvas.beginFrame(); try { app.manager().render(g, GameCanvas.DESIGN_WIDTH, GameCanvas.DESIGN_HEIGHT); } finally { canvas.endFrame(g); }
                if (smoke && ++frames >= 30) { if (!canvas.isDisplayable()) throw new IllegalStateException("Window not displayable"); close(); }
            } catch (Throwable error) { failure.set(error); close(); }
        }
        void close() {
            if (closing) return; closing = true;
            if (timer != null) timer.stop();
            try { canvas.shutdown(); } catch (Throwable error) { failure.compareAndSet(null, error); }
            finally { closed.countDown(); }
        }
    }
}
