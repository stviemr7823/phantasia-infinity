package com.phantasia.core.logic;

import com.phantasia.core.model.CombatRank;
import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.Job;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerCreationV5;
import com.phantasia.core.model.PlayerRuntimeV5;
import com.phantasia.core.model.PlayerStateV5;
import com.phantasia.core.model.PlayerStateV5.Attribute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FormulaEngineV5Test {
    private static PlayerRuntimeV5 player(int strength, int agility, int mind) {
        return new PlayerRuntimeV5(PlayerCreationV5.create(
                "Seren", 1, Job.FIGHTER, 0, strength, agility, mind, 40, 12));
    }

    private static Monster monster(int hp) {
        DataCore record = new DataCore();
        record.setName("Orc");
        record.setStat(DataLayout.MON_HP, hp);
        return new Monster(record);
    }

    @Test
    void hitAndEscapeProbabilitiesReadAgilityNotMindOrStrength() {
        PlayerRuntimeV5 agile = player(5, 30, 2);
        Monster target = monster(60); // monster defense = 10
        assertEquals(70, FormulaEngine.playerHitChance(agile, target));
        assertEquals(80, FormulaEngine.monsterHitChance(target, agile));
        assertEquals(60, FormulaEngine.escapeChance(agile));

        PlayerStateV5 changed = agile.snapshot();
        changed.setAttribute(Attribute.STRENGTH, 200);
        changed.setAttribute(Attribute.MIND, 240);
        PlayerRuntimeV5 sameAgility = new PlayerRuntimeV5(changed);
        assertEquals(FormulaEngine.playerHitChance(agile, target),
                FormulaEngine.playerHitChance(sameAgility, target));
        assertEquals(FormulaEngine.escapeChance(agile),
                FormulaEngine.escapeChance(sameAgility));
    }

    @Test
    void hitChanceClampsAndDamageRespectsPositionAndCap() {
        assertEquals(8, FormulaEngine.playerHitChance(player(5, 0, 3), monster(255)));
        assertEquals(95, FormulaEngine.playerHitChance(player(5, 255, 3), monster(1)));
        assertEquals(95, FormulaEngine.monsterHitChance(monster(255), player(4, 0, 4)));
        assertEquals(5, FormulaEngine.monsterHitChance(monster(1), player(4, 255, 4)));
        assertEquals(85, FormulaEngine.escapeChance(player(1, 255, 1)));

        PlayerRuntimeV5 strong = player(255, 12, 2);
        for (int i = 0; i < 50; i++) {
            int front = FormulaEngine.calculatePlayerDamage(strong, CombatRank.FRONT);
            int back = FormulaEngine.calculatePlayerDamage(strong, CombatRank.BACK);
            assertTrue(front >= 0 && front <= 99);
            assertTrue(back >= 0 && back <= 99);
        }
        assertThrows(NullPointerException.class,
                () -> FormulaEngine.calculatePlayerDamage(strong, null));
        assertThrows(NullPointerException.class,
                () -> FormulaEngine.escapeChance(null));
    }
}
