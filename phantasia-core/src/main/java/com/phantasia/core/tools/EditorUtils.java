// phantasia-core/src/main/java/com/phantasia/core/tools/EditorUtils.java
package com.phantasia.core.tools;

import com.phantasia.core.data.DataPaths;
import com.phantasia.core.model.DataCore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared utilities for all editor panels.
 * Field factories, validation helpers, file choosers, layout helpers,
 * and the shared binary block I/O used by Monster and Spell panels.
 */
public final class EditorUtils {

    private EditorUtils() {}

    // -------------------------------------------------------------------------
    // Field factories
    // -------------------------------------------------------------------------

    public static JTextField field(int columns) {
        JTextField f = new JTextField(columns);
        f.setBackground(PhantasiaEditor.BG_LIST);
        f.setForeground(PhantasiaEditor.TEXT_MAIN);
        f.setCaretColor(PhantasiaEditor.ACCENT);
        f.setFont(PhantasiaEditor.FONT_FIELD);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PhantasiaEditor.BORDER_COL),
                new EmptyBorder(2, 6, 2, 6)));
        return f;
    }

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(PhantasiaEditor.TEXT_DIM);
        l.setFont(PhantasiaEditor.FONT_LABEL);
        return l;
    }

    public static JButton button(String text) {
        JButton b = new JButton(text);
        b.setBackground(PhantasiaEditor.BG_PANEL);
        b.setForeground(PhantasiaEditor.TEXT_MAIN);
        b.setFont(PhantasiaEditor.FONT_LABEL);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PhantasiaEditor.BORDER_COL),
                new EmptyBorder(4, 12, 4, 12)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static JButton accentButton(String text) {
        JButton b = button(text);
        b.setBackground(new Color(80, 65, 35));
        b.setForeground(PhantasiaEditor.ACCENT);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PhantasiaEditor.ACCENT),
                new EmptyBorder(4, 14, 4, 14)));
        return b;
    }

    public static JCheckBox checkbox(String text) {
        JCheckBox cb = new JCheckBox(text);
        cb.setBackground(PhantasiaEditor.BG_PANEL);
        cb.setForeground(PhantasiaEditor.TEXT_MAIN);
        cb.setFont(PhantasiaEditor.FONT_LABEL);
        return cb;
    }

    public static JPanel titledPanel(String title, LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(PhantasiaEditor.BG_PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(PhantasiaEditor.BORDER_COL),
                        " " + title + " ",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        PhantasiaEditor.FONT_LABEL,
                        PhantasiaEditor.ACCENT),
                new EmptyBorder(8, 8, 8, 8)));
        return p;
    }

    public static JList<String> styledList() {
        JList<String> list = new JList<>(new DefaultListModel<>());
        list.setBackground(PhantasiaEditor.BG_LIST);
        list.setForeground(PhantasiaEditor.TEXT_MAIN);
        list.setFont(PhantasiaEditor.FONT_FIELD);
        list.setSelectionBackground(new Color(80, 70, 45));
        list.setSelectionForeground(PhantasiaEditor.ACCENT);
        list.setBorder(new EmptyBorder(4, 4, 4, 4));
        return list;
    }

    public static JScrollPane scrollPane(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBackground(PhantasiaEditor.BG_LIST);
        sp.setBorder(BorderFactory.createLineBorder(PhantasiaEditor.BORDER_COL));
        sp.getVerticalScrollBar().setBackground(PhantasiaEditor.BG_PANEL);
        return sp;
    }

    // -------------------------------------------------------------------------
    // Validation helpers
    // -------------------------------------------------------------------------

    /**
     * Parses an integer from a text field.
     * On failure: flashes the field red, returns the fallback, then immediately
     * clears the error state so the red never persists.
     */
    public static int parseInt(JTextField field, int fallback) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            field.setBackground(PhantasiaEditor.BG_LIST);  // ensure clean state
            return value;
        } catch (NumberFormatException e) {
            field.setBackground(new Color(80, 30, 30));
            // Schedule a reset so the red flash is visible but doesn't stick
            Timer t = new Timer(800, evt -> field.setBackground(PhantasiaEditor.BG_LIST));
            t.setRepeats(false);
            t.start();
            return fallback;
        }
    }

    // -------------------------------------------------------------------------
    // File choosers — all default to DataPaths directories
    // -------------------------------------------------------------------------

    public static File chooseDatFile(Component parent, String title) {
        JFileChooser fc = new JFileChooser(DataPaths.DAT_DIR);
        fc.setDialogTitle(title);
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Binary Data Files (*.dat)", "dat"));
        return fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
                ? fc.getSelectedFile() : null;
    }

    public static File chooseMapFile(Component parent, String title) {
        JFileChooser fc = new JFileChooser(DataPaths.DAT_DIR);
        fc.setDialogTitle(title);
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Map Files (*.map)", "map"));
        return fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
                ? fc.getSelectedFile() : null;
    }

    /**
     * Opens a save dialog defaulting to DataPaths.DAT_DIR,
     * auto-appending the extension if absent.
     */
    public static File chooseSaveFile(Component parent,
                                      String title, String extension) {
        JFileChooser fc = new JFileChooser(DataPaths.DAT_DIR);
        fc.setDialogTitle(title);
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
        File f = fc.getSelectedFile();
        if (!f.getName().endsWith("." + extension))
            f = new File(f.getPath() + "." + extension);
        return f;
    }

    // -------------------------------------------------------------------------
    // Layout helpers
    // -------------------------------------------------------------------------

    public static void addFormRow(JPanel form, String labelText, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(PhantasiaEditor.BG_PANEL);
        row.setBorder(new EmptyBorder(3, 0, 3, 0));
        JLabel lbl = label(labelText);
        lbl.setPreferredSize(new Dimension(120, 24));
        row.add(lbl,   BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        form.add(row);
    }

    public static void confirm(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message,
                "Phantasia Editor", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message,
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static boolean confirm(Component parent, String message, String title) {
        return JOptionPane.showConfirmDialog(parent, message, title,
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    // -------------------------------------------------------------------------
    // Shared binary block I/O — used by MonsterEditorPanel and SpellEditorPanel
    // -------------------------------------------------------------------------

    /**
     * Loads 48-byte records from a file into the supplied list and list model.
     * Skips zero-filled gap slots (sparse .dat layout).
     * Clears existing content first. Returns true if at least one record loaded.
     *
     * @param labelFn  Produces the display string for each block (e.g. "[ID] Name")
     */
    public static boolean loadBlocks(File f,
                                     List<byte[]> blocks,
                                     DefaultListModel<String> model,
                                     java.util.function.Function<byte[], String> labelFn)
            throws IOException {
        byte[] all = Files.readAllBytes(f.toPath());
        blocks.clear();
        model.clear();

        for (int i = 0; i + 48 <= all.length; i += 48) {
            byte[] block = new byte[48];
            System.arraycopy(all, i, block, 0, 48);
            if (isEmptyBlock(block)) continue;
            blocks.add(block);
            model.addElement(labelFn.apply(block));
        }
        return !blocks.isEmpty();
    }

    /**
     * Writes modified field values back into the raw byte array of a block.
     * Uses DataCore as the write intermediary — the caller supplies a lambda
     * that performs the actual setStat/setName calls on the DataCore.
     *
     * This is the fix for the MonsterEditorPanel saveToBlock bug: previously
     * a new DataCore was created from the block bytes but the modified bytes
     * were never written back. This method ensures the same byte array is
     * mutated in place.
     */
    public static void writeBlock(byte[] block,
                                  java.util.function.Consumer<DataCore> writer) {
        DataCore core = new DataCore(block);
        writer.accept(core);
        // DataCore operates directly on the byte[] reference — no copy needed.
        // The block in the list is already updated.
    }

    /**
     * Returns true if every byte in the block is 0x00 or ASCII space.
     * Used to skip gap slots in sparse .dat files.
     */
    public static boolean isEmptyBlock(byte[] block) {
        for (byte b : block)
            if (b != 0x00 && b != 0x20) return false;
        return true;
    }

    /**
     * Silently auto-loads a .dat file on panel startup.
     * Does nothing if the file doesn't exist — no error dialogs.
     *
     * @param path     Path to the .dat file (from DataPaths)
     * @param blocks   The panel's block list to populate
     * @param model    The panel's list model to populate
     * @param list     The JList to select the first item after loading
     * @param labelFn  Produces the display label for each block
     */
    public static void autoLoad(String path,
                                List<byte[]> blocks,
                                DefaultListModel<String> model,
                                JList<String> list,
                                java.util.function.Function<byte[], String> labelFn) {
        File f = new File(path);
        if (!f.exists()) return;
        try {
            if (loadBlocks(f, blocks, model, labelFn) && list != null)
                list.setSelectedIndex(0);
        } catch (IOException ex) {
            // Silently ignore on startup — not a user-initiated action
            System.err.println("[EditorUtils] autoLoad failed for " + path
                    + ": " + ex.getMessage());
        }
    }
}
