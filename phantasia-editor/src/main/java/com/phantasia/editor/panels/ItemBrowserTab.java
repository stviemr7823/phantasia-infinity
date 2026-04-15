// phantasia-editor/src/main/java/com/phantasia/editor/panels/ItemBrowserTab.java
package com.phantasia.editor.panels;

import com.phantasia.core.tools.ItemEditorPanel;
import com.phantasia.editor.EditorFrame;

import javax.swing.*;
import java.awt.*;

/**
 * Workspace tab adapter for the existing {@link ItemEditorPanel}.
 * Items are currently read-only from ItemTable — this gives the designer
 * a browsable catalog and the Bake button for items.dat.
 */
public class ItemBrowserTab extends JPanel implements EditorFrame.WorkspaceTab {

    public ItemBrowserTab() {
        setLayout(new BorderLayout());
        add(new ItemEditorPanel(), BorderLayout.CENTER);
    }

    @Override
    public String getTabKey() { return "items:all"; }
}