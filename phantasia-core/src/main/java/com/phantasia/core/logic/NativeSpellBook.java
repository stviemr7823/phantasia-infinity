package com.phantasia.core.logic;

import com.phantasia.core.model.*;
import java.util.*;

/** Caller-supplied permissions and detached spell definitions, not learned character state. */
public final class NativeSpellBook {
    public record Entry(int id, Spell spell, Set<Job> jobs) {
        public Entry {
            if (id <= 0) throw new IllegalArgumentException("Positive spell ID required");
            Objects.requireNonNull(spell); jobs = Set.copyOf(jobs);
            if (jobs.isEmpty()) throw new IllegalArgumentException("Explicit casting jobs required");
            var data = spell.getDataCore();
            if (data.getStat(DataLayout.SPELL_TARGET) >= Spell.TargetType.values().length
                    || data.getStat(DataLayout.SPELL_TYPE) >= Spell.SpellType.values().length
                    || data.getStat(DataLayout.SPELL_EFFECT_TYPE) >= Spell.EffectType.values().length)
                throw new IllegalArgumentException("Invalid spell enum");
            boolean enemy = spell.getEffectType() == Spell.EffectType.DAMAGE || spell.getEffectType() == Spell.EffectType.SLEEP;
            boolean ally = spell.getEffectType() == Spell.EffectType.HEAL || spell.getEffectType() == Spell.EffectType.AWAKEN;
            boolean enemyTarget = spell.getTargetType() == Spell.TargetType.SINGLE_ENEMY || spell.getTargetType() == Spell.TargetType.ALL_ENEMIES;
            if ((!enemy && !ally) || enemy != enemyTarget || spell.getLevelRequirement() < 1)
                throw new IllegalArgumentException("Unsupported spell effect, target or level");
            spell = new DetachedSpell(spell);
        }
        public boolean permits(NativeCampaignV5.Member member) {
            return jobs.stream().anyMatch(j -> j.getBitValue() == member.getJob()) && member.level() >= spell.getLevelRequirement();
        }
    }
    private static final class DetachedSpell extends Spell {
        DetachedSpell(Spell spell) { super(new DataCore(spell.getDataCore().getRawBytes().clone())); }
        @Override public DataCore getDataCore() { return new DataCore(super.getDataCore().getRawBytes().clone()); }
    }
    private final List<Entry> entries;
    public NativeSpellBook(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        if (this.entries.stream().map(Entry::id).distinct().count() != this.entries.size())
            throw new IllegalArgumentException("Duplicate spell ID");
    }
    public int idOf(Spell spell) {
        return entries.stream().filter(e -> e.spell() == spell).mapToInt(Entry::id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Spell is not from this book"));
    }
    public Entry entry(int id) { return entries.stream().filter(e -> e.id() == id).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown spell ID")); }
    public List<Entry> available(NativeCampaignV5.Member member) { return entries.stream().filter(e -> e.permits(member)).toList(); }
}
