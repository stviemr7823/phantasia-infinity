// phantasia-core/src/main/java/com/phantasia/core/world/TileSetEntry.java
package com.phantasia.core.world;

/**
 * A single tile definition within an {@link InteriorTileSet}.
 *
 * @param index           ordinal position in the tile set (used in map data)
 * @param name            human-readable name ("wood_floor", "locked_door")
 * @param passable        whether the party can walk onto this tile
 * @param assetId         renderer-agnostic visual asset key
 * @param interactionType how the tile responds to player contact
 */
public record TileSetEntry(
        int             index,
        String          name,
        boolean         passable,
        String          assetId,
        InteractionType interactionType
) {}
