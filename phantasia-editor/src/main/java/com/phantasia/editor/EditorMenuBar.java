// phantasia-editor/src/main/java/com/phantasia/editor/EditorMenuBar.java
package com.phantasia.editor;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Menu bar for the Phantasia Editor workbench (Section 3.1).
 *
 * <p>Menus: File, Edit, Data, Maps, Test, Help.</p>
 *
 * <p>The Edit menu's Undo/Redo items update dynamically via
 * {@link EditorStateListener#onUndoRedoChanged}.</p>
 */
public class EditorMenuBar extends JMenuBar implements EditorStateListener {

    // Edit menu items — updated dynamically
    private JMenuItem undoItem;
    private JMenuItem redoItem;

    // Callbacks — wired by EditorFrame
    private Runnable onNewProject;
    private Runnable onOpenProject;
    private Runnable onSaveProject;
    private Runnable onSaveProjectAs;
    private Runnable onBakeAll;
    private Runnable onBakeAndTour;
    private Runnable onTourLaunch;
    private Runnable onMapGenerator;
    private Runnable onOpenWorldMap;
    private Runnable onNewTownMap;
    private Runnable onNewDungeonMap;
    private Runnable onExit;

    public EditorMenuBar() {
        add(buildFileMenu());
        add(buildEditMenu());
        add(buildDataMenu());
        add(buildMapsMenu());
        add(buildTestMenu());
        add(buildHelpMenu());

        EditorState.get().addListener(this);
    }

    // -------------------------------------------------------------------------
    // File menu
    // -------------------------------------------------------------------------

    private JMenu buildFileMenu() {
        JMenu menu = new JMenu("File");
        menu.setMnemonic(KeyEvent.VK_F);

        JMenuItem newProj = item("New Project", KeyEvent.VK_N,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                e -> { if (onNewProject != null) onNewProject.run(); });
        menu.add(newProj);

        JMenuItem open = item("Open Project...", KeyEvent.VK_O,
                InputEvent.CTRL_DOWN_MASK,
                e -> { if (onOpenProject != null) onOpenProject.run(); });
        menu.add(open);

        menu.addSeparator();

        JMenuItem save = item("Save Project", KeyEvent.VK_S,
                InputEvent.CTRL_DOWN_MASK,
                e -> { if (onSaveProject != null) onSaveProject.run(); });
        menu.add(save);

        JMenuItem saveAs = item("Save Project As...", KeyEvent.VK_S,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                e -> { if (onSaveProjectAs != null) onSaveProjectAs.run(); });
        menu.add(saveAs);

        menu.addSeparator();

        JMenuItem exit = item("Exit", KeyEvent.VK_Q,
                InputEvent.CTRL_DOWN_MASK,
                e -> { if (onExit != null) onExit.run(); });
        menu.add(exit);

        return menu;
    }

    // -------------------------------------------------------------------------
    // Edit menu
    // -------------------------------------------------------------------------

    private JMenu buildEditMenu() {
        JMenu menu = new JMenu("Edit");
        menu.setMnemonic(KeyEvent.VK_E);

        undoItem = item("Undo", KeyEvent.VK_Z,
                InputEvent.CTRL_DOWN_MASK,
                e -> EditorState.get().undo());
        undoItem.setEnabled(false);
        menu.add(undoItem);

        redoItem = item("Redo", KeyEvent.VK_Z,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                e -> EditorState.get().redo());
        redoItem.setEnabled(false);
        menu.add(redoItem);

        return menu;
    }

    // -------------------------------------------------------------------------
    // Data menu
    // -------------------------------------------------------------------------

    private JMenu buildDataMenu() {
        JMenu menu = new JMenu("Data");
        menu.setMnemonic(KeyEvent.VK_D);

        JMenuItem bakeAll = item("Bake All", KeyEvent.VK_B,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                e -> { if (onBakeAll != null) onBakeAll.run(); });
        menu.add(bakeAll);

        menu.addSeparator();

        // Per-category bake items
        menu.add(new JMenuItem("Bake Monsters"));
        menu.add(new JMenuItem("Bake Items"));
        menu.add(new JMenuItem("Bake Spells"));
        menu.add(new JMenuItem("Bake World Map"));

        return menu;
    }

    // -------------------------------------------------------------------------
    // Maps menu
    // -------------------------------------------------------------------------

    private JMenu buildMapsMenu() {
        JMenu menu = new JMenu("Maps");
        menu.setMnemonic(KeyEvent.VK_M);

        JMenuItem newTown = item("New Town Map...", KeyEvent.VK_T,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                e -> { if (onNewTownMap != null) onNewTownMap.run(); });
        JMenuItem newDungeon = item("New Dungeon Map...", KeyEvent.VK_D,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                e -> { if (onNewDungeonMap != null) onNewDungeonMap.run(); });
        JMenuItem genItem = item("Map Generator...", KeyEvent.VK_G,
                InputEvent.CTRL_DOWN_MASK,
                e -> { if (onMapGenerator != null) onMapGenerator.run(); });
        JMenuItem worldItem = new JMenuItem("Edit World Map");
        worldItem.addActionListener(e -> { if (onOpenWorldMap != null) onOpenWorldMap.run(); });

        menu.add(newTown);
        menu.add(newDungeon);
        menu.addSeparator();
        menu.add(genItem);
        menu.addSeparator();
        menu.add(worldItem);

        return menu;
    }

    // -------------------------------------------------------------------------
    // Test menu
    // -------------------------------------------------------------------------

    private JMenu buildTestMenu() {
        JMenu menu = new JMenu("Test");
        menu.setMnemonic(KeyEvent.VK_T);

        JMenuItem tour = item("Bake & Tour", KeyEvent.VK_F5, 0,
                e -> { if (onBakeAndTour != null) onBakeAndTour.run(); });
        menu.add(tour);

        JMenuItem tourOnly = new JMenuItem("Tour (no bake)");
        tourOnly.addActionListener(
                e -> { if (onTourLaunch != null) onTourLaunch.run(); });
        menu.add(tourOnly);

        menu.addSeparator();
        menu.add(new JMenuItem("Tour Settings..."));

        return menu;
    }

    // -------------------------------------------------------------------------
    // Help menu
    // -------------------------------------------------------------------------

    private JMenu buildHelpMenu() {
        JMenu menu = new JMenu("Help");
        menu.setMnemonic(KeyEvent.VK_H);

        JMenuItem about = new JMenuItem("About Phantasia Editor");
        about.addActionListener(e -> JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                "Phantasia: Infinity — Editor Suite\n"
                        + "Version 2.0\n\n"
                        + "The world gets designed here.",
                "About",
                JOptionPane.INFORMATION_MESSAGE));
        menu.add(about);

        return menu;
    }

    // -------------------------------------------------------------------------
    // EditorStateListener — dynamic Undo/Redo labels
    // -------------------------------------------------------------------------

    @Override
    public void onUndoRedoChanged(String undoDescription, String redoDescription) {
        if (undoDescription != null) {
            undoItem.setText("Undo: " + undoDescription);
            undoItem.setEnabled(true);
        } else {
            undoItem.setText("Undo");
            undoItem.setEnabled(false);
        }

        if (redoDescription != null) {
            redoItem.setText("Redo: " + redoDescription);
            redoItem.setEnabled(true);
        } else {
            redoItem.setText("Redo");
            redoItem.setEnabled(false);
        }
    }

    // -------------------------------------------------------------------------
    // Callback setters — called by EditorFrame during wiring
    // -------------------------------------------------------------------------

    public void setOnNewProject(Runnable r)    { this.onNewProject = r; }
    public void setOnOpenProject(Runnable r)   { this.onOpenProject = r; }
    public void setOnSaveProject(Runnable r)   { this.onSaveProject = r; }
    public void setOnSaveProjectAs(Runnable r) { this.onSaveProjectAs = r; }
    public void setOnBakeAll(Runnable r)       { this.onBakeAll = r; }
    public void setOnBakeAndTour(Runnable r)   { this.onBakeAndTour = r; }
    public void setOnTourLaunch(Runnable r)    { this.onTourLaunch = r; }
    public void setOnMapGenerator(Runnable r)  { this.onMapGenerator = r; }
    public void setOnOpenWorldMap(Runnable r)  { this.onOpenWorldMap = r; }
    public void setOnNewTownMap(Runnable r)    { this.onNewTownMap = r; }
    public void setOnNewDungeonMap(Runnable r) { this.onNewDungeonMap = r; }
    public void setOnExit(Runnable r)          { this.onExit = r; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static JMenuItem item(String text, int key, int modifiers,
                                  java.awt.event.ActionListener action) {
        JMenuItem mi = new JMenuItem(text);
        if (key != 0) {
            mi.setAccelerator(KeyStroke.getKeyStroke(key, modifiers));
        }
        mi.addActionListener(action);
        return mi;
    }
}