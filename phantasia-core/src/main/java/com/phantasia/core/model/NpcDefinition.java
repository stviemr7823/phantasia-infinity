// phantasia-core/src/main/java/com/phantasia/core/model/NpcDefinition.java
package com.phantasia.core.model;

import com.phantasia.core.data.QuestFlag;

/**
 * The complete definition of a non-player character.
 *
 * NpcDefinition is the core data type that the editor authors, the data
 * layer serializes, and the renderer consumes. It ties together everything
 * an NPC is: identity, visual presentation, placement, and interaction.
 *
 * RENDERER-AGNOSTIC VISUALS:
 *   The {@code spriteAssetId} and {@code portraitAssetId} are string keys
 *   that each renderer maps to its own visual representation:
 *     - j2d:    sprite sheet PNG + animation frames
 *     - JME:    3D model + skeleton animation
 *     - libGDX: TextureAtlas region + animation
 *   Core never references sprites, models, or textures directly.
 *
 * DIALOGUE:
 *   Each NPC carries a {@link DialogueScript} — an ordered list of
 *   {@link DialogueNode} entries gated by quest flags. The engine selects
 *   the first matching entry and renders it with token substitution via
 *   {@link DialogueTextRenderer}. See {@link DialogueScript} for details.
 *
 * PLACEMENT:
 *   NpcDefinition does not carry position — that belongs to
 *   {@code PlacedNpc}, which references this definition by ID and adds
 *   grid coordinates, facing direction, and appear/disappear conditions.
 *   An NPC definition can be placed in multiple locations (e.g., an NPC
 *   that moves between towns based on quest state).
 *
 * ROLES:
 *   The {@link NpcRole} determines the interaction type:
 *     MERCHANT    → bumping opens a shop (linked by shopId)
 *     QUEST_GIVER → delivers dialogue, may set flags / give items
 *     TRAINER     → guild training services
 *     INFORMANT   → delivers dialogue only, no services
 *     INNKEEPER   → rest/heal services
 *     BANKER      → deposit/withdraw gold
 *     GUARD       → blocks passage until quest flag is set
 *     BOSS        → triggers a scripted combat encounter
 *
 * ANIMATION:
 *   The {@link AnimationProfile} describes pacing/wander behaviour and
 *   supplies renderer-agnostic animation keys. Defaults to
 *   {@link AnimationProfile#STATIC} — a fully stationary NPC. Override
 *   via the fluent {@link #animationProfile(AnimationProfile)} setter for
 *   any NPC that should wander or react to the player.
 *
 * LIFECYCLE:
 *   - Authored in the editor's NPC panel
 *   - Baked to npcs.dat by NpcBaker
 *   - Loaded at runtime by NpcRegistry
 *   - Placed on interior maps via PlacedNpc records
 *   - Interacted with when the player bumps their position
 */
public class NpcDefinition {

    private int              id;
    private String           name;              // "Filmon", "Greta the Smith"
    private NpcRole          role;              // determines interaction type
    private String           spriteAssetId;     // renderer visual: "npc_filmon"
    private String           portraitAssetId;   // dialogue portrait: "portrait_filmon" (nullable)
    private DialogueScript   dialogue;          // quest-flag-gated dialogue sequence
    private int              shopId;            // if MERCHANT, links to ShopInventory (0 = none)
    private AnimationProfile animationProfile   // pacing & animation metadata
            = AnimationProfile.STATIC;          //   defaults to fully stationary

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public NpcDefinition() {
        this.dialogue = new DialogueScript();
    }

    public NpcDefinition(int id, String name, NpcRole role, String spriteAssetId) {
        this.id            = id;
        this.name          = name;
        this.role          = role;
        this.spriteAssetId = spriteAssetId;
        this.dialogue      = new DialogueScript();
    }

    // -------------------------------------------------------------------------
    // Builder-style setters (for editor and loader convenience)
    // -------------------------------------------------------------------------

    public NpcDefinition id(int id)                               { this.id = id;                         return this; }
    public NpcDefinition name(String name)                        { this.name = name;                     return this; }
    public NpcDefinition role(NpcRole role)                       { this.role = role;                     return this; }
    public NpcDefinition spriteAssetId(String id)                 { this.spriteAssetId = id;              return this; }
    public NpcDefinition portraitAssetId(String id)               { this.portraitAssetId = id;            return this; }
    public NpcDefinition dialogue(DialogueScript script)          { this.dialogue = script;               return this; }
    public NpcDefinition shopId(int shopId)                       { this.shopId = shopId;                 return this; }
    public NpcDefinition animationProfile(AnimationProfile profile) { this.animationProfile = profile;   return this; }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int              getId()                { return id;               }
    public String           getName()              { return name;             }
    public NpcRole          getRole()              { return role;             }
    public String           getSpriteAssetId()     { return spriteAssetId;    }
    public String           getPortraitAssetId()   { return portraitAssetId;  }
    public DialogueScript   getDialogue()          { return dialogue;         }
    public int              getShopId()            { return shopId;           }
    public AnimationProfile getAnimationProfile()  { return animationProfile; }

    // -------------------------------------------------------------------------
    // Convenience
    // -------------------------------------------------------------------------

    /** Returns true if this NPC is linked to a shop inventory. */
    public boolean isMerchant() {
        return role == NpcRole.MERCHANT && shopId > 0;
    }

    @Override
    public String toString() {
        return "NPC[" + id + "] " + name + " (" + role + ")"
                + (shopId > 0 ? " shop=" + shopId : "");
    }
}