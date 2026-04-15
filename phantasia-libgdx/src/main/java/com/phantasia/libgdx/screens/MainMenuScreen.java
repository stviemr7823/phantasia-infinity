// phantasia-libgdx/src/main/java/com/phantasia/libgdx/screens/MainMenuScreen.java
package com.phantasia.libgdx.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.ScreenUtils;
import com.phantasia.libgdx.PhantasiaGame;

/**
 * Main menu screen.
 *
 * FIXES APPLIED:
 *   1. Input is now handled via InputAdapter.keyDown() / touchDown() registered
 *      in show() rather than polling inside render(). Polling with isKeyJustPressed()
 *      inside render() is unreliable on some LWJGL3 backends because the flag
 *      clears before the next frame is polled.
 *   2. Input processor is cleared in hide() so it doesn't fire on the next screen.
 *   3. dispose() is no longer called immediately after setScreen() — LibGDX calls
 *      hide() then disposes the old screen via Game.setScreen(); calling dispose()
 *      manually beforehand was a double-dispose risk.
 */
public class MainMenuScreen implements Screen {

    private final PhantasiaGame   game;
    private final OrthographicCamera camera;

    public MainMenuScreen(PhantasiaGame game) {
        this.game   = game;
        this.camera = new OrthographicCamera();
        camera.setToOrtho(false, 800, 480);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {

            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.N) {
                    startNewGame();
                    return true;
                }
                if (keycode == Input.Keys.T) {
                    startTour();
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                startNewGame();
                return true;
            }
        });
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0, 0, 0.2f, 1);

        camera.update();
        game.getBatch().setProjectionMatrix(camera.combined);

        game.getBatch().begin();
        game.getFont().draw(game.getBatch(), "PHANTASIA: INFINITY",        100, 200);
        game.getFont().draw(game.getBatch(), "ENTER or click — New Game",  100, 150);
        game.getFont().draw(game.getBatch(), "T — Tour Map (test)",        100, 120);
        game.getBatch().end();
    }

    private void startNewGame() {
        game.setScreen(new LoadingScreen(game, LoadingScreen.Destination.GAME));
    }

    private void startTour() {
        game.setScreen(new LoadingScreen(game, LoadingScreen.Destination.TOUR));
    }

    @Override public void hide()                        { Gdx.input.setInputProcessor(null); }
    @Override public void resize(int width, int height) {}
    @Override public void pause()                       {}
    @Override public void resume()                      {}
    @Override public void dispose()                     {}
}