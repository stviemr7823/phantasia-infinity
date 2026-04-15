package com.phantasia.libgdx;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.AbsoluteFileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.PrefixFileHandleResolver;
import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.Texture;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.world.FeatureRegistry;
import com.phantasia.core.world.WorldMap;
import com.phantasia.libgdx.renderer.KenneyTileSet;
import com.phantasia.libgdx.screens.LoadingScreen;
import com.phantasia.libgdx.screens.MainMenuScreen;

/**
 * Root LibGDX {@link Game} — owns shared GL resources and top-level state.
 *
 * <h3>Shared resources</h3>
 * <ul>
 *   <li>{@code SpriteBatch} — one instance, passed to every screen.
 *   <li>{@code BitmapFont}  — default font for all HUD text.
 *   <li>{@code AssetManager} — loads tile textures; uses a
 *       {@link PathAwareResolver} so that both internal (classpath/asset-root)
 *       paths and absolute filesystem paths work without separate manager
 *       instances.  This is necessary because {@link KenneyTileSet} may
 *       discover tile PNGs at an absolute path on the filesystem when the game
 *       is launched from a project sub-directory or via the editor's
 *       "Bake &amp; Tour" button.
 *   <li>{@code KenneyTileSet} — resolved tile textures, set by LoadingScreen.
 * </ul>
 *
 * <h3>Editor launch</h3>
 * When {@code editorMapPath} is non-null, {@code create()} skips the main menu
 * and goes straight to {@code LoadingScreen(TOUR)}.  See {@code Main.java}.
 */
public class PhantasiaGame extends Game {

    private final String editorMapPath;

    private SpriteBatch  batch;
    private BitmapFont   font;
    private AssetManager assets;
    private GameSession  session;
    private WorldMap     worldMap;
    private FeatureRegistry featureRegistry;

    /** Tile texture set — null until LoadingScreen calls setTileSet(). */
    private KenneyTileSet tileSet;

    // -------------------------------------------------------------------------

    public PhantasiaGame() {
        this(null);
    }

    public PhantasiaGame(String editorMapPath) {
        this.editorMapPath = editorMapPath;
    }

    @Override
    public void create() {
        batch  = new SpriteBatch();
        font   = new BitmapFont();
        assets = new AssetManager(new PathAwareResolver());

        if (editorMapPath != null) {
            System.out.println("[PhantasiaGame] Editor launch — map: " + editorMapPath);
            this.setScreen(new LoadingScreen(this, LoadingScreen.Destination.TOUR));
        } else {
            this.setScreen(new MainMenuScreen(this));
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        assets.dispose();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public SpriteBatch  getBatch()         { return batch;          }
    public BitmapFont   getFont()          { return font;           }
    public AssetManager getAssets()        { return assets;         }
    public GameSession  getSession()       { return session;        }
    public WorldMap     getWorldMap()      { return worldMap;       }
    public String       getEditorMapPath() { return editorMapPath;  }
    public KenneyTileSet getTileSet()      { return tileSet;        }

    public void setSession(GameSession session)   { this.session  = session;  }
    public void setWorldMap(WorldMap worldMap)     { this.worldMap = worldMap; }
    public void setTileSet(KenneyTileSet tileSet)  { this.tileSet  = tileSet;  }

    // -------------------------------------------------------------------------
    // PathAwareResolver — handles both internal and absolute paths
    // -------------------------------------------------------------------------

    /**
     * A {@link FileHandleResolver} that dispatches based on whether the path
     * is absolute or relative.
     *
     * <ul>
     *   <li>Absolute paths (starting with {@code /} or a Windows drive letter
     *       like {@code C:\}) are opened via {@link AbsoluteFileHandleResolver}.
     *   <li>All other paths are opened via {@link InternalFileHandleResolver},
     *       which resolves relative to the LibGDX asset root
     *       ({@code phantasia-libgdx/assets/} in the standard Gradle layout).
     * </ul>
     *
     * This is necessary because {@link KenneyTileSet} discovers tiles using a
     * multi-path probe that may return either an internal asset-root path or an
     * absolute filesystem path depending on where the PNGs are found.
     * Without this resolver, absolute paths passed to
     * {@code AssetManager.load()} would be silently wrapped as internal paths
     * and fail to resolve.
     */
    public static final class PathAwareResolver implements FileHandleResolver {

        private final InternalFileHandleResolver internal =
                new InternalFileHandleResolver();
        private final AbsoluteFileHandleResolver absolute =
                new AbsoluteFileHandleResolver();

        @Override
        public FileHandle resolve(String fileName) {
            if (isAbsolute(fileName)) {
                return absolute.resolve(fileName);
            }
            return internal.resolve(fileName);
        }

        private static boolean isAbsolute(String path) {
            if (path == null || path.isEmpty()) return false;
            // Unix absolute
            if (path.charAt(0) == '/') return true;
            // Windows absolute: C:\... or C:/...
            return path.length() > 2
                    && Character.isLetter(path.charAt(0))
                    && path.charAt(1) == ':'
                    && (path.charAt(2) == '\\' || path.charAt(2) == '/');
        }
    }
    public FeatureRegistry getFeatureRegistry()                       { return featureRegistry; }
    public void setFeatureRegistry(FeatureRegistry featureRegistry)   { this.featureRegistry = featureRegistry; }
}