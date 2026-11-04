package com.phantasia.baseline;

import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.PartyLedger;
import com.phantasia.core.data.SaveManager;
import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.world.WorldClock;
import com.phantasia.core.world.WorldPosition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dependency-free baseline smoke tests for the 2026-04-26 source snapshot. */
public final class CoreBaselineSmokeTest {
    private static int checks = 0;
    private static void check(boolean result, String what) {
        checks++;
        if (!result) throw new AssertionError(what);
    }

    public static void main(String[] args) throws Exception {
        check(DataLayout.RECORD_SIZE == 64, "DataCore record size");
        DataCore c = new DataCore();
        check(c.getRawBytes().length == 64, "DataCore allocation");
        c.setName("Aldric");
        check("Aldric".equals(c.getName()), "Name round trip");
        c.setShort(DataLayout.PC_HP, 0x1234);
        check(c.getShort(DataLayout.PC_HP) == 0x1234, "16-bit round trip");
        check((c.getRawBytes()[DataLayout.PC_HP] & 0xff) == 0x12, "Big-endian high byte");
        c.setPortraitId(100);
        check(c.getPortraitId() == 31, "Portrait clamp");
        c.setTraits(0xA5);
        check(c.getTraits() == 0xA5, "Traits round trip");
        c.setResonance(5233);
        check(c.getResonance() == 5233, "Resonance round trip");
        c.setExtendedInventorySlot(1, 37);
        check(c.getExtendedInventorySlot(1) == 37, "Extended inventory round trip");

        PlayerCharacter player = new PlayerCharacter(c);
        WorldClock clock = new WorldClock(0.2f);
        clock.update(WorldClock.CYCLE_SECONDS * 0.25f);
        check(Math.abs(clock.getNormalisedTime() - 0.45f) < 0.0001f, "Clock elapsed time");

        GameSession session = new GameSession(List.of(player), PartyLedger.newGame(),
                new WorldPosition(12, 9), 0x100000002L);
        session.getWorldClock().update(360f);
        session.getLedger().addGold(75);
        Path tmp = Files.createTempFile("phantasia-april-v4-", ".dat");
        try {
            SaveManager.save(session, tmp.toString());
            GameSession loaded = SaveManager.load(tmp.toString());
            check(loaded.getParty().size() == 1, "Party size round trip");
            check("Aldric".equals(loaded.getParty().get(0).getName()), "Player name round trip");
            check(loaded.getLedger().getPartyGold() == 475, "Party gold round trip");
            check(loaded.getQuestFlags() == 0x100000002L, "64-bit quest flags");
            check(loaded.getWorldPosition().x() == 12 && loaded.getWorldPosition().y() == 9,
                    "World position round trip");
            check(Math.abs(loaded.getWorldClock().getNormalisedTime() - session.getWorldClock().getNormalisedTime()) < 0.00001f,
                    "Clock time round trip");
            check(loaded.getParty().get(0).getDataCore().getPortraitId() == 31, "Expansion field persisted");
        } finally {
            Files.deleteIfExists(tmp);
        }
        System.out.println("PASS core baseline smoke checks=" + checks + " (April source, save v4)");
    }
}
