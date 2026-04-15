// phantasia-core/src/main/java/com/phantasia/core/world/InteriorMapType.java
package com.phantasia.core.world;

/**
 * Distinguishes the behavioral category of an interior map.
 *
 * The map type determines default {@link InteriorSettings} values
 * and which tile set vocabulary is available in the editor.
 */
public enum InteriorMapType {

    /** A town interior — lit, peaceful, service-oriented. */
    TOWN,

    /** A dungeon floor — dark, dangerous, exploration-oriented. */
    DUNGEON
}
