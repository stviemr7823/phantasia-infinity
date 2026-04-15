package com.phantasia.core.logic;

import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.model.Stat;
import java.util.List;

/**
 * THE SKELETON: Core mathematical "Truth" of the Judgment.
 */
public final class JudgmentEngine {

    public enum Fate { RESURRECTED, UNDEAD, DESTROYED }

    // This record MUST be public for GameEvent to see it
    public record CharacterJudgment(PlayerCharacter character, Fate fate) {}

    // This is the specific symbol the compiler is missing
    public record JudgmentResult(List<CharacterJudgment> individualFates, boolean totalWipe) {}

    public static JudgmentResult judge(List<PlayerCharacter> party) {
        List<CharacterJudgment> outcomes = party.stream()
                .map(pc -> new CharacterJudgment(pc, calculateFate(pc)))
                .toList();

        boolean totalWipe = outcomes.stream().allMatch(j -> j.fate() == Fate.DESTROYED);

        JudgmentResult result = new JudgmentResult(outcomes, totalWipe);

        // Update the characters' internal state before announcing the event
        outcomes.forEach(j -> applyFateToCharacter(j));

        return result;
    }

    private static Fate calculateFate(PlayerCharacter pc) {
        int roll = (int)(Math.random() * 100) + (pc.getStat(Stat.LEVEL) * 2);
        if (roll >= 60) return Fate.RESURRECTED;
        if (roll >= 20) return Fate.UNDEAD;
        return Fate.DESTROYED;
    }

    private static void applyFateToCharacter(CharacterJudgment j) {
        switch (j.fate()) {
            case RESURRECTED -> j.character().setHp(j.character().getStat(Stat.MAX_HP));
            case UNDEAD -> {
                j.character().setHp(1);
                j.character().setUndead(true);
            }
            case DESTROYED -> j.character().setHp(0);
        }
    }
}