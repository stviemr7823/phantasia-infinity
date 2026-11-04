// phantasia-core/src/main/java/com/phantasia/core/data/NpcRegistry.java
package com.phantasia.core.data;

import com.phantasia.core.model.DialogueNode;
import com.phantasia.core.model.NpcDefinition;
import com.phantasia.core.model.NpcRole;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime registry of all authored NPC definitions.
 *
 * <h3>ID conventions</h3>
 * <pre>
 *   101–109  overworld map NPCs (FeatureRecord.npc placements)
 *   110–119  Pendragon interior NPCs
 *   210–219  Scandor interior NPCs
 *   310–319  Loftwood interior NPCs
 *   410–419  Ironhaven interior NPCs
 * </pre>
 */
public final class NpcRegistry {

    private final Map<Integer, NpcDefinition> byId = new LinkedHashMap<>();

    public void register(NpcDefinition npc) { byId.put(npc.getId(), npc); }

    public NpcDefinition get(int id)        { return byId.get(id); }

    public Collection<NpcDefinition> getAll() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() { return byId.size(); }

    // -------------------------------------------------------------------------
    // Default programmatic registry
    // -------------------------------------------------------------------------

    public static NpcRegistry buildDefault() {
        NpcRegistry r = new NpcRegistry();

        // ── 101 — Filmon, overworld informant near Pendragon ───────────────
        NpcDefinition filmon = new NpcDefinition(
                101, "Filmon", NpcRole.INFORMANT, "npc_filmon");
        filmon.getDialogue().addNode(new DialogueNode(
                "Greetings, {player_name}. Welcome to Pendragon, the ancient capital. "
                        + "Dark times have come since the Sundering — travel carefully.",
                null, null, null));
        r.register(filmon);

        // ── Pendragon town NPCs (110–113) ──────────────────────────────────

        // 110 — Marta, innkeeper
        NpcDefinition marta = new NpcDefinition(
                110, "Marta", NpcRole.INNKEEPER, "npc_innkeeper");
        marta.getDialogue().addNode(new DialogueNode(
                "Welcome to the Pendragon Inn, {player_name}. "
                        + "Rest here and your wounds will mend.",
                null, null, null));
        r.register(marta);

        // 111 — Aldric, merchant — links to Pendragon General Store (shopId=10)
        NpcDefinition aldric = new NpcDefinition(
                111, "Aldric", NpcRole.MERCHANT, "npc_merchant");
        aldric.shopId(10);
        aldric.getDialogue().addNode(new DialogueNode(
                "Fine goods at fair prices. What can I offer you today?",
                null, null, null));
        r.register(aldric);

        // 112 — Gregor, guild master / trainer
        NpcDefinition gregor = new NpcDefinition(
                112, "Gregor", NpcRole.TRAINER, "npc_trainer");
        gregor.getDialogue().addNode(new DialogueNode(
                "The guild exists to sharpen the worthy. Bring experience "
                        + "and gold, and I will make you stronger.",
                null, null, null));
        r.register(gregor);

        // 113 — Serafine, banker
        NpcDefinition serafine = new NpcDefinition(
                113, "Serafine", NpcRole.BANKER, "npc_banker");
        serafine.getDialogue().addNode(new DialogueNode(
                "Your gold is safe with us. Deposit it here and no dungeon "
                        + "misfortune can take it from you.",
                null, null, null));
        r.register(serafine);

        // ── Scandor town NPCs (210–211) ────────────────────────────────────

        // 210 — Kira, merchant (shopId=11)
        NpcDefinition kira = new NpcDefinition(
                210, "Kira", NpcRole.MERCHANT, "npc_merchant");
        kira.shopId(11);
        kira.getDialogue().addNode(new DialogueNode(
                "Cold up here, isn't it? Browse my wares — "
                        + "a good blade keeps warmer than a thick cloak.",
                null, null, null));
        r.register(kira);

        // 211 — Bram, innkeeper
        NpcDefinition bram = new NpcDefinition(
                211, "Bram", NpcRole.INNKEEPER, "npc_innkeeper");
        bram.getDialogue().addNode(new DialogueNode(
                "Scandor's only inn. The cold keeps the monsters away. Mostly.",
                null, null, null));
        r.register(bram);

        // ── Loftwood town NPCs (310) ───────────────────────────────────────

        // 310 — Elara, healer/merchant (shopId=12)
        NpcDefinition elara = new NpcDefinition(
                310, "Elara", NpcRole.MERCHANT, "npc_merchant");
        elara.shopId(12);
        elara.getDialogue().addNode(new DialogueNode(
                "The forest provides. Our healers have the finest potions in the land.",
                null, null, null));
        r.register(elara);

        // ── Ironhaven town NPCs (410) ──────────────────────────────────────

        // 410 — Durrin, smith/merchant (shopId=13)
        NpcDefinition durrin = new NpcDefinition(
                410, "Durrin", NpcRole.MERCHANT, "npc_merchant");
        durrin.shopId(13);
        durrin.getDialogue().addNode(new DialogueNode(
                "Ironhaven steel. The finest in the realm. "
                        + "Nothing breaks in the Sundering's shadow like cheap iron.",
                null, null, null));
        r.register(durrin);

        return r;
    }
}
