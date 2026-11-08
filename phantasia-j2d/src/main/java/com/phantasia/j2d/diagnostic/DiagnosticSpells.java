package com.phantasia.j2d.diagnostic;

import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;
import java.util.*;

/** Non-canon test definitions; no inferred hybrid-job permissions or learned spell state. */
public final class DiagnosticSpells {
    private DiagnosticSpells() {}
    private static NativeSpellBook.Entry entry(int id, String name, Spell.EffectType effect, Spell.TargetType target, Job job) {
        var data = new DataCore(); data.setName(name);
        data.setStat(DataLayout.SPELL_MP_COST, 3); data.setStat(DataLayout.SPELL_POWER, 12);
        data.setStat(DataLayout.SPELL_LEVEL_REQ, 1); data.setStat(DataLayout.SPELL_EFFECT_TYPE, effect.ordinal());
        data.setStat(DataLayout.SPELL_TARGET, target.ordinal());
        data.setStat(DataLayout.SPELL_TYPE, (job == Job.WIZARD ? Spell.SpellType.WIZARD : Spell.SpellType.PRIEST).ordinal());
        return new NativeSpellBook.Entry(id, new Spell(data), Set.of(job));
    }
    public static final NativeSpellBook BOOK = new NativeSpellBook(List.of(
            entry(1, "Test Spark", Spell.EffectType.DAMAGE, Spell.TargetType.SINGLE_ENEMY, Job.WIZARD),
            entry(2, "Test Sleep", Spell.EffectType.SLEEP, Spell.TargetType.SINGLE_ENEMY, Job.WIZARD),
            entry(3, "Test Sparks", Spell.EffectType.DAMAGE, Spell.TargetType.ALL_ENEMIES, Job.WIZARD),
            entry(4, "Test Slumber", Spell.EffectType.SLEEP, Spell.TargetType.ALL_ENEMIES, Job.WIZARD),
            entry(5, "Test Mend", Spell.EffectType.HEAL, Spell.TargetType.SINGLE_ALLY, Job.PRIEST),
            entry(6, "Test Awaken", Spell.EffectType.AWAKEN, Spell.TargetType.SINGLE_ALLY, Job.PRIEST),
            entry(7, "Test Mending", Spell.EffectType.HEAL, Spell.TargetType.ALL_ALLIES, Job.PRIEST),
            entry(8, "Test Waking", Spell.EffectType.AWAKEN, Spell.TargetType.ALL_ALLIES, Job.PRIEST)));
    public static NativeCampaignV5 freshCampaign() {
        return new NativeCampaignV5(List.of(
                PlayerCreationV5.create("Test wizard", 1, Job.WIZARD, 0, 20, 20, 15, 100, 10),
                PlayerCreationV5.create("Test priest", 2, Job.PRIEST, 0, 18, 18, 15, 100, 10)), 40, 2, List.of());
    }
}
