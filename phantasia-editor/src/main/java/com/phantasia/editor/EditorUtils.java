// phantasia-editor/src/main/java/com/phantasia/editor/EditorUtils.java
package com.phantasia.editor;

import com.phantasia.core.model.DataCore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Shared UI factories and utilities for the editor suite.
 * Migrated from {@code core.tools.EditorUtils} to use {@link EditorTheme}.
 */
public final class EditorUtils {

    private EditorUtils() {}

    // ── Field factories ──────────────────────────────────────────────────

    public static JTextField field(int columns) {
        JTextField f = new JTextField(columns);
        f.setFont(EditorTheme.FONT_FIELD);
        return f;
    }

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(EditorTheme.TEXT_DIM);
        l.setFont(EditorTheme.FONT_LABEL);
        return l;
    }

    public static JLabel headerLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(EditorTheme.ACCENT);
        l.setFont(EditorTheme.FONT_HEADER);
        return l;
    }

    public static JButton button(String text) {
        JButton b = new JButton(text);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static JButton accentButton(String text) {
        JButton b = new JButton(text);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // FlatLaf treats buttons with clientProperty "JButton.buttonType"
        // as special; setting default makes it use the accent style from properties.
        b.putClientProperty("JButton.buttonType", "default");
        return b;
    }

    public static JCheckBox checkbox(String text) {
        return new JCheckBox(text);
    }

    public static JList<String> styledList() {
        JList<String> list = new JList<>(new DefaultListModel<>());
        list.setFont(EditorTheme.FONT_FIELD);
        list.setFixedCellHeight(20);
        return list;
    }

    public static JScrollPane scrollPane(Component c) {
        JScrollPane sp = new JScrollPane(c);
        return sp;
    }

    // ── Layout helpers ───────────────────────────────────────────────────

    public static JPanel titledPanel(String title, LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(EditorTheme.BORDER),
                        " " + title + " ",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        EditorTheme.FONT_HEADER,
                        EditorTheme.ACCENT),
                new EmptyBorder(8, 8, 8, 8)));
        return p;
    }

    public static void addFormRow(JPanel form, String labelText, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(3, 0, 3, 0));
        JLabel lbl = label(labelText);
        lbl.setPreferredSize(new Dimension(100, 24));
        lbl.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(lbl,   BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        form.add(row);
    }

    // ── Validation ───────────────────────────────────────────────────────

    public static int parseInt(JTextField field, int fallback) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            Color orig = field.getBackground();
            field.setBackground(new Color(80, 30, 30));
            Timer t = new Timer(800, evt -> field.setBackground(orig));
            t.setRepeats(false);
            t.start();
            return fallback;
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────

    public static void info(Component parent, String message) {
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

    // ── Binary block I/O (48-byte DataCore records, no header) ───────────

    public static boolean loadBlocks(File f, List<byte[]> blocks,
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

    public static void writeBlock(byte[] block, java.util.function.Consumer<DataCore> writer) {
        writer.accept(new DataCore(block));
    }

    public static boolean isEmptyBlock(byte[] block) {
        for (byte b : block)
            if (b != 0x00 && b != 0x20) return false;
        return true;
    }

    // ── File choosers ────────────────────────────────────────────────────

    public static File chooseDatFile(Component parent, String title) {
        JFileChooser fc = new JFileChooser(".");
        fc.setDialogTitle(title);
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Binary Data (*.dat)", "dat"));
        return fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
                ? fc.getSelectedFile() : null;
    }

    public static File chooseSaveFile(Component parent, String title, String ext) {
        JFileChooser fc = new JFileChooser(".");
        fc.setDialogTitle(title);
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
        File f = fc.getSelectedFile();
        if (!f.getName().endsWith("." + ext))
            f = new File(f.getPath() + "." + ext);
        return f;
    }
}