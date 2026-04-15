// phantasia-editor/src/main/java/com/phantasia/editor/panels/DungeonEditorTab.java
package com.phantasia.editor.panels;

import com.phantasia.core.tools.DungeonEditorPanel;
import com.phantasia.editor.EditorFrame;

import javax.swing.*;
import java.awt.*;

/**
 * Workspace tab adapter for the existing {@link DungeonEditorPanel}.
 * Hosts the dungeon floor generator and viewer from core.tools.
 */
public class DungeonEditorTab extends JPanel implements EditorFrame.WorkspaceTab {

    public DungeonEditorTab() {
        setLayout(new BorderLayout());
        add(new DungeonEditorPanel(), BorderLayout.CENTER);
    }

    @Override
    public String getTabKey() { return "dungeons:all"; }
}
