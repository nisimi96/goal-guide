package com.goalguide.quests;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.goalguide.items.ItemTree;

/**
 * The everyday boosts: one item per line, how many levels it adds, and what it takes to make it. A requirement Quest
 * Helper marks boostable is met with a boost instead of training when the gap is no bigger than the boost.
 *
 * <p>Idea from Codex Navigator's Boosts (github.com/Nubles/OSRS-Codex-Navigator, BSD 2-Clause); every row checked on
 * the OSRS Wiki 2026-10-25 (Temporary skill boost and each item's page). Navigator lists one item per skill; this
 * table keeps the cheaper ones too, because a low iron can brew an agility potion (Herblore 34) long before it can
 * bake a summer pie (Cooking 95). Only plain, repeatable boosts: no spicy stew (random), no minigame-only potions,
 * no Bandit's brew (shop opens partway into Desert Treasure I), no POH tea.
 */
public final class Boosts
{
    private Boosts() { }

    public static final class Boost
    {
        /** Upper-case skill name it boosts. */
        public final String skill;
        public final String item;
        public final int flat, percent;
        /** The skill that makes it, or null when shops sell it to anyone. */
        public final String makeSkill;
        public final int makeLevel;

        Boost(String skill, String item, int flat, int percent, String makeSkill, int makeLevel)
        {
            this.skill = skill;
            this.item = item;
            this.flat = flat;
            this.percent = percent;
            this.makeSkill = makeSkill;
            this.makeLevel = makeLevel;
        }

        /** Levels added at this real level: flat plus a share of the level, rounded down (the game's formula). */
        public int amount(int level)
        {
            return flat + level * percent / 100;
        }

        /** Anyone off the Grand Exchange; an iron only if they can make it, a shop sells it, or one is in the bank. */
        public boolean obtainable(ItemTree.Account a)
        {
            if (!a.iron || makeSkill == null) return true;
            if (a.skills.getOrDefault(makeSkill, 1) >= makeLevel) return true;
            String key = item.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Integer> e : a.owned.entrySet())
                if (e.getValue() > 0 && (e.getKey().equals(key) || e.getKey().startsWith(key + "(") || e.getKey().startsWith("half a " + key))) return true;
            return false;
        }

        @Override public String toString() { return item + " (+" + flat + (percent > 0 ? " +" + percent + "%" : "") + ")"; }
    }

    public static final Map<String, List<Boost>> BY_SKILL = new LinkedHashMap<>();

    private static void b(String skill, String item, int flat, int percent, String makeSkill, int makeLevel)
    {
        BY_SKILL.computeIfAbsent(skill, k -> new ArrayList<>()).add(new Boost(skill, item, flat, percent, makeSkill, makeLevel));
    }

    static
    {
        b("AGILITY", "Agility potion", 3, 0, "HERBLORE", 34);
        b("AGILITY", "Summer pie", 5, 0, "COOKING", 95);
        b("ATTACK", "Attack potion", 3, 10, "HERBLORE", 3);
        b("ATTACK", "Super attack", 5, 15, "HERBLORE", 45);
        b("STRENGTH", "Strength potion", 3, 10, "HERBLORE", 12);
        b("STRENGTH", "Super strength", 5, 15, "HERBLORE", 55);
        b("DEFENCE", "Defence potion", 3, 10, "HERBLORE", 30);
        b("DEFENCE", "Super defence", 5, 15, "HERBLORE", 66);
        b("RANGED", "Wild pie", 4, 0, "COOKING", 85);
        b("RANGED", "Ranging potion", 4, 10, "HERBLORE", 72);
        b("MAGIC", "Magic potion", 4, 0, "HERBLORE", 76);
        b("FISHING", "Fishing potion", 3, 0, "HERBLORE", 50);
        b("FISHING", "Admiral pie", 5, 0, "COOKING", 70);
        b("CRAFTING", "Mushroom pie", 4, 0, "COOKING", 60);
        b("FLETCHING", "Dragonfruit pie", 4, 0, "COOKING", 73);
        b("HERBLORE", "Botanical pie", 4, 0, "COOKING", 52);
        b("SLAYER", "Wild pie", 5, 0, "COOKING", 85);
        b("FARMING", "Garden pie", 3, 0, "COOKING", 34);
        b("HUNTER", "Hunter potion", 3, 0, "HERBLORE", 53);
        b("MINING", "Dwarven stout", 1, 0, null, 0);
        b("SMITHING", "Dwarven stout", 1, 0, null, 0);
    }

    /**
     * The boost to use for "skill at need" from this real level: the smallest one that is enough and the account can
     * get (an agility potion before a summer pie), or null when none is. Never for a requirement that is met already.
     */
    public static Boost toReach(String skill, int have, int need, ItemTree.Account a)
    {
        if (have >= need) return null;
        Boost best = null;
        for (Boost b : BY_SKILL.getOrDefault(skill, List.of()))
        {
            if (have + b.amount(have) < need || !b.obtainable(a)) continue;
            if (best == null || b.amount(have) < best.amount(have)) best = b;
        }
        return best;
    }
}
