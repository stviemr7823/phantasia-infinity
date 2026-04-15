package com.phantasia.jme.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.control.AbstractControl;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;

public class FloatingTextState extends BaseAppState {
    private Node guiNode;
    private BitmapFont guiFont;

    @Override
    protected void initialize(Application app) {
        this.guiNode = ((com.jme3.app.SimpleApplication) app).getGuiNode();
        this.guiFont = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
    }

    public void spawnText(String text, Vector3f worldPos, ColorRGBA color) {
        BitmapText bt = new BitmapText(guiFont);
        bt.setText(text);
        bt.setSize(guiFont.getCharSet().getRenderedSize() * 2);
        bt.setColor(color);

        // Simple math to convert 3D world space to 2D screen space
        Vector3f screenPos = getApplication().getCamera().getScreenCoordinates(worldPos);
        bt.setLocalTranslation(screenPos.x, screenPos.y, 0);

        // Add a Control to handle the "floating" and "fading" logic
        bt.addControl(new FloatingControl());
        guiNode.attachChild(bt);
    }

    private static class FloatingControl extends AbstractControl {
        private float timer = 0;
        private final float duration = 1.5f;

        @Override
        protected void controlUpdate(float tpf) {
            timer += tpf;
            // Move up
            spatial.move(0, tpf * 50f, 0);

            if (timer > duration) {
                spatial.removeFromParent();
            }
        }
        @Override protected void controlRender(RenderManager rm, ViewPort vp) {}
    }

    @Override protected void cleanup(Application app) {}
    @Override protected void onEnable() {}
    @Override protected void onDisable() {}
}