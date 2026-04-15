// phantasia-core/src/main/java/com/phantasia/core/model/DialogueContext.java
package com.phantasia.core.model;

import com.phantasia.core.data.GameSession;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A read-only snapshot of game state for dialogue text rendering.
 *
 * Harvested from the Fragment system's NpcContextSnapshot, stripped down
 * to what DialogueTextRenderer actually needs: a token map for substitution
 * and a property map for conditional evaluation.
 *
 * The snapshot is frozen at interaction time so that dialogue evaluation
 * is deterministic — the state doesn't shift mid-conversation.
 *
 * TOKEN MAP (available in dialogue text as {token_name}):
 *   player_name  — party leader's name
 *   npc_name     — the NPC being spoken to
 *   gold         — party gold as a string
 *   party_size   — number of living party members
 *
 * PROPERTY MAP (available in {if property op value} conditionals):
 *   gold         — party gold as int
 *   party_size   — alive party members as int
 *
 * USAGE:
 *   DialogueContext ctx = DialogueContext.freeze(npcDef, session);
 *   DialogueResult result = npcDef.getDialogue().evaluate(session, ctx);
 */
public record DialogueContext(
        Map<String, String> tokens,
        Map<String, Number> properties
) {

    /**
     * Freezes the current game state into an immutable dialogue context.
     *
     * @param npcName the NPC's display name
     * @param session the active game session
     * @return an immutable context for text rendering
     */
    public static DialogueContext freeze(String npcName, GameSession session) {

        List<PlayerCharacter> party = session.getParty();

        String leaderName = party.isEmpty()
                ? "Adventurer"
                : party.getFirst().getName();

        int aliveCount = (int) party.stream()
                .filter(PlayerCharacter::isAlive)
                .count();

        int gold = session.getLedger().getPartyGold();

        // --- Token map for {token} substitution ---
        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("player_name", leaderName);
        tokens.put("npc_name",    npcName);
        tokens.put("gold",        String.valueOf(gold));
        tokens.put("party_size",  String.valueOf(aliveCount));

        // --- Property map for {if prop op value} conditionals ---
        Map<String, Number> properties = new LinkedHashMap<>();
        properties.put("gold",       gold);
        properties.put("party_size", aliveCount);

        return new DialogueContext(
                Collections.unmodifiableMap(tokens),
                Collections.unmodifiableMap(properties)
        );
    }

    /**
     * Returns the token value for the given key, or the key itself
     * (wrapped in braces) if not found — so unrecognized tokens pass
     * through unchanged rather than disappearing.
     */
    public String getToken(String key) {
        return tokens.getOrDefault(key, "{" + key + "}");
    }

    /**
     * Returns the numeric property for the given key, or null if not present.
     * Used by DialogueTextRenderer for conditional evaluation.
     */
    public Number getProperty(String key) {
        return properties.get(key);
    }
}