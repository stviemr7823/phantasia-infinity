// phantasia-core/src/main/java/com/phantasia/core/world/FeatureRecord.java
package com.phantasia.core.world;

import com.phantasia.core.model.AnimationProfile;

/**
 * Rich metadata for a single world feature — town, dungeon, or NPC.
 *
 * This is the authoritative data record loaded from features.dat.
 * It is engine-agnostic: no asset paths, no renderer references.
 *
 * ASSET RESOLUTION:
 *   Each frontend maintains its own features.manifest file that maps
 *   (type, id) → asset path. FeatureRecord never knows about assets.
 *
 * ANIMATION:
 *   The {@link AnimationProfile} carries pacing/wander behaviour and
 *   renderer-agnostic animation keys. World-map features that are fully
 *   static (towns, dungeon entrances) use the 7-param constructor, which
 *   defaults to {@link AnimationProfile#STATIC}. Overworld NPCs or
 *   animated world features supply an explicit profile via the 8-param
 *   constructor.
 *
 * FIELDS:
 *   id               — unique integer key, matches WorldFeature.id on tiles
 *   type             — TOWN, DUNGEON, NPC, etc.
 *   x, y             — map coordinates of the feature tile
 *   name             — display name ("Pendragon", "Frostpeak Cavern")
 *   description      — flavour text shown in HUD or town screen
 *   serviceFlags     — bitmask of available services (inn, shop, guild, bank)
 *   animationProfile — pacing and animation metadata (never null)
 */
public final class FeatureRecord {

    // -------------------------------------------------------------------------
    // Service flag constants
    // -------------------------------------------------------------------------

    public static final byte SERVICE_INN   = 0x01;
    public static final byte SERVICE_SHOP  = 0x02;
    public static final byte SERVICE_GUILD = 0x04;
    public static final byte SERVICE_BANK  = 0x08;

    // -------------------------------------------------------------------------
    // Fields — private final; access via getters
    // -------------------------------------------------------------------------

    private final int              id;
    private final FeatureType      type;
    private final int              x;
    private final int              y;
    private final String           name;
    private final String           description;
    private final byte             serviceFlags;
    private final AnimationProfile animationProfile;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Full constructor. Use when specifying a non-default AnimationProfile.
     */
    public FeatureRecord(int id, FeatureType type, int x, int y,
                         String name, String description, byte serviceFlags,
                         AnimationProfile animationProfile) {
        this.id               = id;
        this.type             = type;
        this.x                = x;
        this.y                = y;
        this.name             = name;
        this.description      = description;
        this.serviceFlags     = serviceFlags;
        this.animationProfile = animationProfile != null
                ? animationProfile : AnimationProfile.STATIC;
    }

    /**
     * Backward-compatible constructor — defaults animationProfile to
     * {@link AnimationProfile#STATIC}. All existing callers (factory methods,
     * {@code FeatureRegistry.load()}, etc.) use this overload unchanged.
     */
    public FeatureRecord(int id, FeatureType type, int x, int y,
                         String name, String description, byte serviceFlags) {
        this(id, type, x, y, name, description, serviceFlags, AnimationProfile.STATIC);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int              getId()                { return id;               }
    public FeatureType      getType()              { return type;             }
    public int              getX()                 { return x;                }
    public int              getY()                 { return y;                }
    public String           getName()              { return name;             }
    public String           getDescription()       { return description;      }
    public byte             getServiceFlags()      { return serviceFlags;     }
    public AnimationProfile getAnimationProfile()  { return animationProfile; }

    // -------------------------------------------------------------------------
    // Service queries
    // -------------------------------------------------------------------------

    public boolean hasInn()   { return (serviceFlags & SERVICE_INN)   != 0; }
    public boolean hasShop()  { return (serviceFlags & SERVICE_SHOP)  != 0; }
    public boolean hasGuild() { return (serviceFlags & SERVICE_GUILD) != 0; }
    public boolean hasBank()  { return (serviceFlags & SERVICE_BANK)  != 0; }

    // -------------------------------------------------------------------------
    // Convenience factories
    // -------------------------------------------------------------------------

    /** Town with full services. Stationary (AnimationProfile.STATIC). */
    public static FeatureRecord town(int id, int x, int y,
                                     String name, String description) {
        byte services = (byte)(SERVICE_INN | SERVICE_SHOP
                | SERVICE_GUILD | SERVICE_BANK);
        return new FeatureRecord(id, FeatureType.TOWN, x, y,
                name, description, services);
    }

    /** Dungeon — no services. Stationary (AnimationProfile.STATIC). */
    public static FeatureRecord dungeon(int id, int x, int y,
                                        String name, String description) {
        return new FeatureRecord(id, FeatureType.DUNGEON, x, y,
                name, description, (byte) 0);
    }

    /** NPC — no services, default stationary profile. */
    public static FeatureRecord npc(int id, int x, int y, String name) {
        return new FeatureRecord(id, FeatureType.NPC, x, y,
                name, "", (byte) 0);
    }

    /** NPC — no services, explicit AnimationProfile. */
    public static FeatureRecord npc(int id, int x, int y, String name,
                                    AnimationProfile profile) {
        return new FeatureRecord(id, FeatureType.NPC, x, y,
                name, "", (byte) 0, profile);
    }

    @Override
    public String toString() {
        return getType() + ":" + getName() + "(id=" + getId()
                + " @" + getX() + "," + getY() + ")";
    }
}