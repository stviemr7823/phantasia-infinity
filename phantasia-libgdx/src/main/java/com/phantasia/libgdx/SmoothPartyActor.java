package com.phantasia.libgdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.world.WorldPosition;

import static com.phantasia.libgdx.renderer.WorldMapRenderer.TILE_SIZE;

/**
 * Tracks the party's logical tile position and a smoothly interpolated
 * visual position for rendering.
 *
 * <h3>Rendering</h3>
 * Two draw paths, mirroring j2d {@code MapPanel}'s player sprite vs. fallback:
 *
 * <ul>
 *   <li>{@link #draw(SpriteBatch, Texture)} — draws {@code PLAYER.png} scaled
 *       to 90% of a tile, centred, with the SpriteBatch already open.  The
 *       sprite is drawn at the smoothed visual position so it glides between
 *       tiles.  Call this inside an active SpriteBatch block.
 *   <li>{@link #draw(ShapeRenderer)} — draws the gold outlined rectangle
 *       fallback when no texture is available.  Call this inside an active
 *       ShapeRenderer Filled block.
 * </ul>
 *
 * The caller decides which path to use based on whether
 * {@code KenneyTileSet.getPlayerTexture()} is non-null.
 */
public class SmoothPartyActor {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /**
     * Sprite is drawn at 90% of a tile — slightly larger than the j2d 60%
     * gold box to give the character image more presence on the textured map.
     * Centred within the tile by TOKEN_OFFSET.
     */
    private static final float SPRITE_SIZE   = TILE_SIZE * 0.9f;
    private static final float SPRITE_OFFSET = (TILE_SIZE - SPRITE_SIZE) / 2f;

    /** Fallback gold box — 60% of tile, matches j2d PartyActor exactly. */
    private static final float TOKEN_SIZE    = TILE_SIZE * 0.6f;
    private static final float TOKEN_OFFSET  = (TILE_SIZE - TOKEN_SIZE) / 2f;

    private static final Color TOKEN_COLOR   = new Color(1.00f, 0.902f, 0.196f, 1f);
    private static final Color TOKEN_OUTLINE = new Color(0.706f, 0.510f, 0.078f, 1f);

    private static final float LERP_SPEED = 8f;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    @SuppressWarnings("unused")
    private final PlayerCharacter character;

    private final Vector2 visualPos;
    private final Vector2 targetPos;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public SmoothPartyActor(PlayerCharacter pc, WorldPosition start) {
        this.character = pc;
        this.visualPos = new Vector2(start.x() * TILE_SIZE, start.y() * TILE_SIZE);
        this.targetPos = new Vector2(start.x() * TILE_SIZE, start.y() * TILE_SIZE);
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    public void update(float deltaTime, WorldPosition logicalPos) {
        targetPos.set(logicalPos.x() * TILE_SIZE, logicalPos.y() * TILE_SIZE);
        visualPos.lerp(targetPos, deltaTime * LERP_SPEED);
    }

    // -------------------------------------------------------------------------
    // Draw — textured path
    // -------------------------------------------------------------------------

    /**
     * Draws the party sprite using {@code PLAYER.png}.
     *
     * <p>The {@link SpriteBatch} must already be open with the map camera's
     * projection matrix set when this is called.  Draw this after tiles so
     * the sprite appears on top.
     *
     * <p>Mirrors j2d {@code MapPanel}'s player sprite draw:
     * the sprite is scaled to {@code SPRITE_SIZE} and centred within the tile.
     *
     * @param batch   active SpriteBatch with map camera projection
     * @param texture the loaded {@code PLAYER.png} texture (never null here)
     */
    public void draw(SpriteBatch batch, Texture texture) {
        float px = visualPos.x + SPRITE_OFFSET;
        float py = visualPos.y + SPRITE_OFFSET;
        batch.setColor(Color.WHITE);
        batch.draw(texture, px, py, SPRITE_SIZE, SPRITE_SIZE);
    }

    // -------------------------------------------------------------------------
    // Draw — ShapeRenderer fallback
    // -------------------------------------------------------------------------

    /**
     * Draws the gold outlined rectangle fallback when no player texture is
     * available.
     *
     * <p>The {@link ShapeRenderer} must already be in {@code Filled} mode with
     * the map camera's projection matrix set when this is called.
     *
     * @param sr active ShapeRenderer in Filled mode
     */
    public void draw(ShapeRenderer sr) {
        float px = visualPos.x + TOKEN_OFFSET;
        float py = visualPos.y + TOKEN_OFFSET;

        sr.setColor(TOKEN_OUTLINE);
        sr.rect(px - 1f, py - 1f, TOKEN_SIZE + 2f, TOKEN_SIZE + 2f);

        sr.setColor(TOKEN_COLOR);
        sr.rect(px, py, TOKEN_SIZE, TOKEN_SIZE);
    }

    // -------------------------------------------------------------------------
    // Snap
    // -------------------------------------------------------------------------

    public void snapTo(WorldPosition pos) {
        visualPos.set(pos.x() * TILE_SIZE, pos.y() * TILE_SIZE);
        targetPos.set(pos.x() * TILE_SIZE, pos.y() * TILE_SIZE);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Vector2 getVisualPos() { return visualPos; }
}