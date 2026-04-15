// phantasia-editor/src/main/java/com/phantasia/editor/EditorMain.java
package com.phantasia.editor;

import com.phantasia.core.data.DataPaths;

import javax.swing.*;
import java.nio.file.Path;

/**
 * Entry point for the Phantasia Editor Suite.
 *
 * <p>Bootstrap sequence:</p>
 * <ol>
 *   <li>Configure system properties and FlatLaf (before any Swing work).</li>
 *   <li>Initialize {@link EditorState} singleton.</li>
 *   <li>Auto-load existing {@code .dat} files from the data directory.</li>
 *   <li>Build and show the {@link EditorFrame} workbench.</li>
 * </ol>
 *
 * <p>Replaces the old placeholder {@code Main.java} and the legacy
 * {@code PhantasiaEditor} in {@code core.tools}.</p>
 */
public class EditorMain {

    public static void main(String[] args) {
        // Step 1: Bootstrap environment BEFORE any Swing classes load
        PhantasiaEditorConfig.bootstrapEnvironment();

        // Step 2: Build the UI on the EDT
        SwingUtilities.invokeLater(() -> {
            // Step 3: Initialize EditorState
            EditorState state = EditorState.initialize();

            // Step 4: Auto-load existing data files (Section 5.5)
            Path datDir = Path.of(DataPaths.DAT_DIR);
            state.loadFromDirectory(datDir);

            System.out.println("[EditorMain] " + state);

            // Step 5: Build and show the workbench
            EditorFrame frame = new EditorFrame();
            frame.getAssetExplorer().rebuildTree();
            frame.setVisible(true);

            frame.getStatusBar().setMessage(
                    "Loaded from " + datDir + "  —  " + state);
        });
    }
}