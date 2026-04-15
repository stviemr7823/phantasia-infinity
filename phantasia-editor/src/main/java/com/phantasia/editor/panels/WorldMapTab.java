// phantasia-editor/src/main/java/com/phantasia/editor/panels/WorldMapTab.java
package com.phantasia.editor.panels;

import com.phantasia.core.tools.WorldEditorPanel;
import com.phantasia.editor.EditorFrame;

import javax.swing.*;
import java.awt.*;

/**
 * Workspace tab adapter for the existing {@link WorldEditorPanel}.
 * Hosts the fully-functional world map tile painter from core.tools
 * inside the new workbench layout. Full migration to EditorState
 * will happen in a later phase — this gets the functionality working now.
 */
public class WorldMapTab extends JPanel implements EditorFrame.WorkspaceTab {

    public WorldMapTab() {
        setLayout(new BorderLayout());
        add(new WorldEditorPanel(), BorderLayout.CENTER);
    }

    @Override
    public String getTabKey() { return "worldMap:null"; }
}